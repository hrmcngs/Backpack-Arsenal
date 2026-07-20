package backpackarsenal.energy;

import backpackarsenal.BackpackArsenalMod;
import backpackarsenal.init.ArsenalBackpackConfig;
import backpackarsenal.init.ArsenalBlocks;
import backpackarsenal.item.VoltaicBladeItem;
import backpackarsenal.upgrade.VoltaicChargerUpgradeItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackBlockEntity;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 設置 arsenal_backpack に Forge Energy capability を取り付け、 voltaic_blade の
 * 充電と同期して FE を内部バッファに発電するハンドラ。
 *
 * 動作:
 *   - {@link AttachCapabilitiesEvent}: SB の BackpackBlockEntity (block が
 *     {@code ARSENAL_BACKPACK_ELECTRON_BLOCK}) に {@link BackpackFeProvider} を attach。
 *   - {@link TickEvent.LevelTickEvent}: 10 tick おきに tracked BE を走査。 内側の
 *     voltaic_blade を充電 (held backpack と同じレート) + そのチャージ量に応じた FE を
 *     内部 storage に generate。 voltaic_blade が満タンならその tick の FE 発電は無し。
 *
 * Mekanism / Create / 他 FE pipe は {@link ForgeCapabilities#ENERGY} を見て自動で接続。
 */
@Mod.EventBusSubscriber(modid = BackpackArsenalMod.MODID)
public class BackpackFeEvents {

    private static final ResourceLocation CAP_KEY =
        new ResourceLocation(BackpackArsenalMod.MODID, "fe");
    private static final ResourceLocation ITEM_CAP_KEY =
        new ResourceLocation(BackpackArsenalMod.MODID, "blade_fe");

    /** tracked placed-backpack BEs (weak → BE が GC されたら自動消滅)。 */
    private static final Map<BlockEntity, BackpackFeProvider> tracked =
        Collections.synchronizedMap(new WeakHashMap<>());

    /** ArsenalBackpackContainer から発電量を表示するために BE → Provider を引く lookup。 */
    public static BackpackFeProvider getProvider(BlockEntity be) {
        if (be == null) return null;
        synchronized (tracked) {
            return tracked.get(be);
        }
    }

    /** voltaic_blade ItemStack に IEnergyStorage を attach。 Mekanism cube / 他 FE 充電器で
     *  blade を充電できるようにする。 */
    @SubscribeEvent
    public static void onAttachItem(AttachCapabilitiesEvent<ItemStack> event) {
        ItemStack stack = event.getObject();
        if (stack.getItem() instanceof VoltaicBladeItem) {
            event.addCapability(ITEM_CAP_KEY, new VoltaicBladeEnergyProvider(stack));
        }
    }

    @SubscribeEvent
    public static void onAttachBlockEntity(AttachCapabilitiesEvent<BlockEntity> event) {
        BlockEntity be = event.getObject();
        // ★ ここで be.getLevel() を見てはいけない — AttachCapabilitiesEvent は BlockEntity
        //   コンストラクタから呼ばれ、 setLevel() より前なので常に null。
        //   blockState は constructor 引数で既に格納済みなので getBlockState() は OK。
        var block = be.getBlockState().getBlock();
        if (block != ArsenalBlocks.ARSENAL_BACKPACK_ELECTRON_BLOCK.get()) return;

        // config は int だが、 Integer.MAX_VALUE ( = "無制限" のセンチネル ) は long の
        // Long.MAX_VALUE に昇格し、 int 上限を超えるバッファ / 搬出を許可する。 これで
        // high-multiplier の発電を storage が飲み込め、 Mekanism 経由の搬出も int に縛られない。
        long capacity   = ArsenalBackpackConfig.feCapacity   == Integer.MAX_VALUE
                              ? Long.MAX_VALUE : ArsenalBackpackConfig.feCapacity;
        long maxExtract = ArsenalBackpackConfig.feMaxExtract == Integer.MAX_VALUE
                              ? Long.MAX_VALUE : ArsenalBackpackConfig.feMaxExtract;
        BackpackFeProvider provider = new BackpackFeProvider(capacity, maxExtract);
        event.addCapability(CAP_KEY, provider);
        event.addListener(provider::invalidate);
        tracked.put(be, provider);
        // notify pulse 開始 — onLevelTickFast がこれを見て走り出す
        pendingNotifyCount.incrementAndGet();

        // SB の BackpackBlockEntity#getCapability(ENERGY, side) は private な
        // {@code energyStorageCap} (battery upgrade 用) を必ず先に返すため、
        // 我々の attached provider は ENERGY に関しては shadow される。
        // → reflection で energyStorageCap を 我々の storage を保持する
        //   LazyOptional に上書きし、 SB の getCapability(ENERGY) が我々の値を返すようにする。
        //   ※ SB は cap 無効化のたびにこれを null 化するため、 onLevelTickFast で毎 tick 再注入する。
        ensureSbEnergyField(be, provider);

        // Mekanism がロードされていれば、 Mekanism native の STRICT_ENERGY cap も付与する。
        // これで Mekanism cube / cable は backpack を "Mekanism native の発電機" として認識し、
        // Forge FE ↔ Joules ブリッジ ( config に依存) を経由せず直接連携する。
        // Mekanism 未導入環境では何もしない ( class isolation で NoClassDefFoundError を回避)。
        backpackarsenal.compat.mekanism.MekanismCompat.tryAttachStrictEnergy(event, provider);
    }

    /** SB {@code BackpackBlockEntity#energyStorageCap} field への reflective handle。
     *  一度解決したら cache する。 SB のバージョン差で field 名が変わった等で見つからない
     *  場合は null のまま ( = Forge FE 経路は諦め、 Mekanism STRICT_ENERGY のみで連携 )。 */
    private static Field sbEnergyField;
    private static boolean sbEnergyFieldResolved = false;

    private static Field resolveSbEnergyField(BlockEntity be) {
        if (sbEnergyFieldResolved) return sbEnergyField;
        sbEnergyFieldResolved = true;
        Class<?> cls = be.getClass();
        while (cls != null) {
            try {
                Field f = cls.getDeclaredField("energyStorageCap");
                f.setAccessible(true);
                sbEnergyField = f;
                break;
            } catch (NoSuchFieldException ignored) {
                cls = cls.getSuperclass();
            }
        }
        if (sbEnergyField == null) {
            BackpackArsenalMod.LOGGER.warn(
                "[backpack_arsenal] energyStorageCap not found on {} — Forge FE pull unavailable",
                be.getClass().getName());
        }
        return sbEnergyField;
    }

    /**
     * SB の {@code energyStorageCap} を我々の storage に向ける ( PULL 経路の要 )。
     *
     * <p>SB は upgrade cache 無効化 ( {@code invalidateBackpackCaps} ) のたびに
     * {@code energyStorageCap} を invalidate + null 化し、 次回 {@code getCapability(ENERGY)}
     * で自前の {@code EmptyEnergyStorage} ( 受発電 0 ) を lazily 生成する。 この無効化は
     * ロード直後や内容変更で頻繁に起きるため、 attach 時に一度だけ注入すると即座に上書き
     * され、 Forge FE の cable / 直繋機械が空 storage を掴んで搬出 0 になる。</p>
     *
     * <p>対策として毎 tick これを呼び、 field が我々の storage を指していなければ再注入する。
     * 既に我々の storage を指しているときは何もしない ( = 接続 thrash しない )。 SB が差し込んだ
     * Empty 等が居たら invalidate して Forge の cap consumer に再 query させる。</p>
     */
    private static void ensureSbEnergyField(BlockEntity be, BackpackFeProvider provider) {
        Field field = resolveSbEnergyField(be);
        if (field == null) return;
        try {
            Object cur = field.get(be);
            if (cur instanceof LazyOptional<?> lo) {
                if (lo.resolve().orElse(null) == provider.storage) return; // 既に我々の → no-op
                lo.invalidate(); // SB の Empty を無効化 → listener が再 query
            }
            field.set(be, LazyOptional.of(() -> provider.storage));
        } catch (Throwable ignored) {
            // 毎 tick 呼ばれるので失敗しても warn しない ( resolveSbEnergyField で一度 warn 済み )
        }
    }

    /** notify pulse が pending の BE 総数。 0 なら onLevelTickFast を即 return できる ( O(1) )。
     *  Attach 時に +1、 各 BE が pulse 完了したら -1。 */
    private static final java.util.concurrent.atomic.AtomicInteger pendingNotifyCount =
        new java.util.concurrent.atomic.AtomicInteger(0);

    /**
     * 毎 tick 走る軽量ハンドラ。 二つの責務:
     *   1. 隣接通知 ( pending pulse のみ、 0 のときは skip )
     *   2. per-tick 発電 + per-tick push ( tracked が空でなければ常に )
     *
     * per-tick 発電にする理由: 10-tick gate でバルク発電すると cable 視点で
     * "発電→吸い切る→idle 9 tick→発電" を繰り返し input rate が 0 / N FE/t を
     * 行き来する。 毎 tick 同じ量を加算すれば安定供給になる。
     *
     * 重いスロットスキャン ( chargeBladesAndReport / distributeFeToItemsInside ) は
     * 引き続き 10-tick gate に残し、 fastTick は加算と push のみで軽量に保つ。
     */
    @SubscribeEvent
    public static void onLevelTickFast(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.level.isClientSide) return;
        if (tracked.isEmpty()) return;

        boolean hasPendingNotify = pendingNotifyCount.get() > 0;

        synchronized (tracked) {
            Iterator<Map.Entry<BlockEntity, BackpackFeProvider>> it = tracked.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<BlockEntity, BackpackFeProvider> e = it.next();
                BlockEntity be = e.getKey();
                BackpackFeProvider provider = e.getValue();
                if (be == null || be.isRemoved()) continue;
                if (be.getLevel() != event.level) continue;

                // 0) PULL 経路の自己修復: SB が energyStorageCap を null 化して EmptyEnergyStorage に
                //    戻すのを毎 tick 検知して我々の storage に再注入する ( cable / 直繋機械の吸出し用 )。
                ensureSbEnergyField(be, provider);

                // 1) notify pulse 進行
                if (hasPendingNotify && provider.notifyTicksRemaining > 0) {
                    provider.notifyTicksRemaining--;
                    event.level.updateNeighborsAt(be.getBlockPos(), be.getBlockState().getBlock());
                    if (provider.notifyTicksRemaining == 0) {
                        pendingNotifyCount.decrementAndGet();
                    }
                }

                // 2) per-tick 発電 ( blade 不在なら cachedFePerTick は 0 )
                if (provider.cachedFePerTick > 0) {
                    provider.storage.generate(provider.cachedFePerTick);
                }

                // 3) 隣接 push ( cable / cube / 他 mod の FE 機械への分配 )。 storage 空なら skip。
                if (provider.storage.getEnergyStored() > 0) {
                    pushFeToNeighbors(be, provider);
                }
            }
        }

    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.level.isClientSide) return;
        if (event.level.getGameTime() % 10 != 0) return;

        int baseChargePerInterval = VoltaicBladeItem.CHARGE_PER_TICK_IN_BACKPACK * 10;
        // FE 発電は high-multiplier で int を溢れるため long で計算する
        // ( 溢れると負値になり cachedFePerTick>0 を満たさず発電が頭打ち/停止していた )。
        long baseFePerInterval = (long) ArsenalBackpackConfig.feGenPerTick * 10;

        synchronized (tracked) {
            Iterator<Map.Entry<BlockEntity, BackpackFeProvider>> it = tracked.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<BlockEntity, BackpackFeProvider> entry = it.next();
                BlockEntity be = entry.getKey();
                if (be == null || be.isRemoved()) { it.remove(); continue; }
                // 別 level の BE は その level の tick で処理 (二重 tick 防止)
                if (be.getLevel() != event.level) continue;

                BackpackFeProvider provider = entry.getValue();

                // VoltaicChargerUpgrade の倍率を計算 (手持ち backpack と同じロジック)。
                //   upgrade 無し           → 1x
                //   tier I (256bonus) 1枚  → 2x
                //   tier I + tier II       → 4x (tier II bonus 512 = +2)
                //   ...etc
                int multiplier = 1 + sumVoltaicChargerContributions(be);
                // blade 充電量は int ( blade の max charge が int ) なので overflow をクランプ。
                int chargePerInterval = (int) Math.min(
                        Integer.MAX_VALUE, (long) baseChargePerInterval * multiplier);
                // FE 発電は long のまま ( 搬出も long 経路で無制限 )。
                long fePerInterval = baseFePerInterval * multiplier;

                // 隣接通知 は別ハンドラ {@link #onLevelTickFast} で 毎 tick 実行する。
                // ここ (10 tick gate 内) ではやらない。

                final boolean[] bladePresent = {false};
                var handlerOpt = be.getCapability(ForgeCapabilities.ITEM_HANDLER);
                handlerOpt.ifPresent(handler -> {
                    ChargeReport rep = chargeBladesAndReport(handler, chargePerInterval);
                    // 発電条件: backpack 内に voltaic_blade が 1 本でも存在すれば ( 満タンでも OK )。
                    //   旧仕様は "充電できた tick だけ発電" だったが、 blade が満タン状態で
                    //   置いてあるだけでも storage が貯まらないのは非直感的なので緩和。
                    // この 10-tick gate でのバルク発電は廃止し、 per-tick fastTick で
                    // {@link BackpackFeProvider#cachedFePerTick} を毎 tick 加算する。
                    if (rep.bladesFound > 0) bladePresent[0] = true;
                });
                // UI 用: blade あり時は理論最大レート ( base × multiplier ) を表示する。
                // 実際の生成量はバッファ空き容量で頭打ちになるので、 upgrade を
                // 増やしてもバッファが満タンだと "5 FE/s" 等にしか見えず混乱する。
                // 代わりに fePerInterval ( storage 制限抜き ) を見せて upgrade 効果を可視化する。
                provider.lastGenPerInterval = bladePresent[0] ? fePerInterval : 0;
                // per-tick 発電量も同時に更新 ( fastTick が毎 tick 使う )。
                provider.cachedFePerTick = bladePresent[0] ? (fePerInterval / 10) : 0;

                // 中の Mekanism tool 等 (IEnergyStorage cap 持ち) に FE を配布。
                // voltaic_blade は直接充電してるのでスキップ。
                handlerOpt.ifPresent(handler -> distributeFeToItemsInside(handler, provider.storage));

                // 隣接 push と発電は per-tick で fastTick が処理する。 ここではしない。
            }
        }
    }

    /** 設置 backpack の BE から VoltaicChargerUpgrade の倍率寄与を合計する。
     *  手持ち backpack 用の {@code BackpackChargingHandler#sumVoltaicChargerContributions}
     *  と同じ計算を、 ItemStack でなく BlockEntity から取った wrapper に対して行う。
     *  各 wrapper の {@link VoltaicChargerUpgradeItem.Wrapper#getMultiplierContribution()}
     *  ( = (tier+1)^2 ) を全部足す。 */
    private static int sumVoltaicChargerContributions(BlockEntity be) {
        if (!(be instanceof BackpackBlockEntity sbBe)) return 0;
        try {
            var wrapper = sbBe.getBackpackWrapper();
            if (wrapper == null) return 0;
            int s = 0;
            for (var w : wrapper.getUpgradeHandler().getTypeWrappers(VoltaicChargerUpgradeItem.TYPE)) {
                if (w != null && w.isEnabled()) s += w.getMultiplierContribution();
            }
            for (var w : wrapper.getUpgradeHandler().getTypeWrappers(
                    backpackarsenal.upgrade.VoltaicGrowthUpgradeItem.TYPE)) {
                if (w != null && w.isEnabled()) s += w.getMultiplierContribution();
            }
            return s;
        } catch (Throwable t) {
            return 0;
        }
    }

    /** chargeBladesAndReport の戻り値。 診断ログ用に詳細を返す。 */
    private static final class ChargeReport {
        boolean chargedAny;
        int slotsScanned;
        int bladesFound;
        int bladesAlreadyFull;
    }

    /** backpack 内部の各スロットを走査し、 {@link IEnergyStorage} cap を持っていて
     *  receiveEnergy 可能なアイテム ( = Mekanism tool / 他 mod のバッテリ式アイテム ) に
     *  storage から FE を配る。 voltaic_blade は別経路で充電されてるのでスキップ。 */
    private static void distributeFeToItemsInside(IItemHandler handler, BackpackFeStorage storage) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (storage.getEnergyStored() <= 0) return;
            ItemStack inner = handler.getStackInSlot(slot);
            if (inner.isEmpty()) continue;
            if (inner.getItem() instanceof VoltaicBladeItem) continue;
            final int[] accepted = {0};
            inner.getCapability(ForgeCapabilities.ENERGY).ifPresent(target -> {
                if (!target.canReceive()) return;
                int available = storage.extractEnergy(Integer.MAX_VALUE, true);
                if (available <= 0) return;
                accepted[0] = target.receiveEnergy(available, false);
                if (accepted[0] > 0) storage.extractEnergy(accepted[0], false);
            });
            // 永続化: SB の wrapper は setStackInSlot を呼ばないと NBT 変更を保存しない場合が
            // あるため明示的に書き戻す。 voltaic_blade と同じ ChargingHandler の挙動。
            if (accepted[0] > 0 && handler instanceof IItemHandlerModifiable mod) {
                mod.setStackInSlot(slot, inner);
            }
        }
    }

    /** 隣接 6 方向の FE 受け取り手に PUSH。 storage に貯まってる分を上限まで押し込む。 */
    private static void pushFeToNeighbors(BlockEntity be, BackpackFeProvider provider) {
        Level level = be.getLevel();
        if (level == null) return;
        if (provider.storage.getEnergyStored() <= 0) return;

        BlockPos pos = be.getBlockPos();
        for (Direction dir : Direction.values()) {
            if (provider.storage.getEnergyStored() <= 0) break;
            BlockEntity adj = level.getBlockEntity(pos.relative(dir));
            if (adj == null) continue;
            adj.getCapability(ForgeCapabilities.ENERGY, dir.getOpposite()).ifPresent(target -> {
                if (!target.canReceive()) return;
                int available = provider.storage.extractEnergy(Integer.MAX_VALUE, true);
                if (available <= 0) return;
                int accepted = target.receiveEnergy(available, false);
                if (accepted > 0) provider.storage.extractEnergy(accepted, false);
            });
        }
    }

    /** ハンドラ内の voltaic_blade を充電し、 1 本でも充電できた (= 満タンでなかった) かを返す。
     *  ネストされた SB backpack の再帰は省略 (top-level だけ対象)。 */
    private static ChargeReport chargeBladesAndReport(IItemHandler handler, int amount) {
        ChargeReport rep = new ChargeReport();
        rep.slotsScanned = handler.getSlots();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            var inner = handler.getStackInSlot(slot);
            if (inner.isEmpty()) continue;
            if (!(inner.getItem() instanceof VoltaicBladeItem)) continue;
            rep.bladesFound++;

            int before = VoltaicBladeItem.getCharge(inner);
            if (before >= VoltaicBladeItem.getMaxCharge(inner)) {
                rep.bladesAlreadyFull++;
                continue;
            }

            VoltaicBladeItem.addCharge(inner, amount);
            if (handler instanceof IItemHandlerModifiable mod) {
                mod.setStackInSlot(slot, inner);
            }
            rep.chargedAny = true;
        }
        return rep;
    }
}
