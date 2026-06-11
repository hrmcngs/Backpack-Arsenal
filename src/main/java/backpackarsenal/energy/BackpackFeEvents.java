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

        BackpackFeProvider provider = new BackpackFeProvider(
            ArsenalBackpackConfig.feCapacity,
            ArsenalBackpackConfig.feMaxExtract);
        event.addCapability(CAP_KEY, provider);
        event.addListener(provider::invalidate);
        tracked.put(be, provider);

        // SB の BackpackBlockEntity#getCapability(ENERGY, side) は private な
        // {@code energyStorageCap} (battery upgrade 用) を必ず先に返すため、
        // 我々の attached provider は ENERGY に関しては shadow される。
        // → reflection で energyStorageCap を 我々の storage を保持する
        //   LazyOptional に上書きし、 SB の getCapability(ENERGY) が我々の値を返すようにする。
        injectIntoSbEnergyField(be, provider);

        // Mekanism がロードされていれば、 Mekanism native の STRICT_ENERGY cap も付与する。
        // これで Mekanism cube / cable は backpack を "Mekanism native の発電機" として認識し、
        // Forge FE ↔ Joules ブリッジ ( config に依存) を経由せず直接連携する。
        // Mekanism 未導入環境では何もしない ( class isolation で NoClassDefFoundError を回避)。
        backpackarsenal.compat.mekanism.MekanismCompat.tryAttachStrictEnergy(event, provider);

        BackpackArsenalMod.LOGGER.info(
            "[backpack_arsenal] FE cap attached to BE @ {} (tracked={})",
            be.getBlockPos(), tracked.size());
    }

    /** SB BackpackBlockEntity の {@code energyStorageCap} field を reflection で
     *  我々の storage の LazyOptional に置き換える。 SB のバージョンが上がって
     *  field 名が変わった場合は warn して諦める (= Mekanism から見えなくなるだけで
     *  blade 充電や他機能には影響なし)。 */
    private static void injectIntoSbEnergyField(BlockEntity be, BackpackFeProvider provider) {
        try {
            Class<?> cls = be.getClass();
            Field field = null;
            while (cls != null) {
                try { field = cls.getDeclaredField("energyStorageCap"); break; }
                catch (NoSuchFieldException ignored) { cls = cls.getSuperclass(); }
            }
            if (field == null) {
                BackpackArsenalMod.LOGGER.warn(
                    "[backpack_arsenal] energyStorageCap not found on {} — FE cap will be shadowed by SB",
                    be.getClass().getName());
                return;
            }
            field.setAccessible(true);
            LazyOptional<IEnergyStorage> opt = LazyOptional.of(() -> provider.storage);
            field.set(be, opt);
            BackpackArsenalMod.LOGGER.info(
                "[backpack_arsenal] injected our FE storage into SB BackpackBlockEntity.energyStorageCap (cls={})",
                be.getClass().getSimpleName());
        } catch (Throwable t) {
            BackpackArsenalMod.LOGGER.warn(
                "[backpack_arsenal] failed to inject FE cap into SB BE: {}", t.toString());
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.level.isClientSide) return;
        if (event.level.getGameTime() % 10 != 0) return;

        int baseChargePerInterval = VoltaicBladeItem.CHARGE_PER_TICK_IN_BACKPACK * 10;
        int baseFePerInterval = ArsenalBackpackConfig.feGenPerTick * 10;
        int totalGenerated = 0;
        int ticked = 0;
        // 診断用: tick したけど発電 0 だった BE の状態を 1 つだけ拾う
        String idleReason = null;

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
                int chargePerInterval = baseChargePerInterval * multiplier;
                int fePerInterval = baseFePerInterval * multiplier;

                // Mekanism cable 等への 1 回限りの再検出通知。
                // AttachCapabilitiesEvent は BE constructor 中で level==null なので
                // 通知できない → 最初の tick で実行する。
                if (!provider.notifiedNeighbors && be.getLevel() != null) {
                    provider.notifiedNeighbors = true;
                    be.getLevel().updateNeighborsAt(be.getBlockPos(), be.getBlockState().getBlock());
                    BackpackArsenalMod.LOGGER.info(
                        "[backpack_arsenal] notified neighbors about FE provider @ {}",
                        be.getBlockPos());
                }

                final int[] genThisTick = {0};
                final String[] reason = {null};
                var handlerOpt = be.getCapability(ForgeCapabilities.ITEM_HANDLER);
                if (!handlerOpt.isPresent()) {
                    if (idleReason == null) idleReason = "BE has no ITEM_HANDLER cap @ " + be.getBlockPos();
                } else {
                    handlerOpt.ifPresent(handler -> {
                        ChargeReport rep = chargeBladesAndReport(handler, chargePerInterval);
                        if (rep.chargedAny) {
                            genThisTick[0] = provider.storage.generate(fePerInterval);
                        } else {
                            reason[0] = String.format("@ %s: %d slots scanned, %d blades found, %d already full, storage=%d/%d",
                                be.getBlockPos(), rep.slotsScanned, rep.bladesFound, rep.bladesAlreadyFull,
                                provider.storage.getEnergyStored(), provider.storage.getMaxEnergyStored());
                        }
                    });
                    if (idleReason == null && reason[0] != null) idleReason = reason[0];
                }
                if (genThisTick[0] > 0) {
                    totalGenerated += genThisTick[0];
                    ticked++;
                }

                // 中の Mekanism tool 等 (IEnergyStorage cap 持ち) に FE を配布。
                // voltaic_blade は直接充電してるのでスキップ。
                handlerOpt.ifPresent(handler -> distributeFeToItemsInside(handler, provider.storage));

                // 隣接 FE 受け取り手に PUSH (Mekanism cable / 直接 cube / 他 mod の FE 機械)。
                // PULL に頼らない発電機の標準パターン。
                pushFeToNeighbors(be, provider);
            }
        }

        // 動作確認用ログ — 2 秒に 1 回 (40 tick おき)。
        // 発電してれば "generated:" を、ゼロなら理由を出す。
        if (event.level.getGameTime() % 40 == 0 && !tracked.isEmpty()) {
            if (ticked > 0) {
                BackpackArsenalMod.LOGGER.info(
                    "[backpack_arsenal] FE generated: {} FE across {} BE(s) (tracked={})",
                    totalGenerated, ticked, tracked.size());
            } else if (idleReason != null) {
                BackpackArsenalMod.LOGGER.info(
                    "[backpack_arsenal] FE idle (tracked={}): {}",
                    tracked.size(), idleReason);
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
            var matched = wrapper.getUpgradeHandler().getTypeWrappers(VoltaicChargerUpgradeItem.TYPE);
            int s = 0;
            for (var w : matched) {
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
    private static long lastPushLogMs = 0;
    private static long lastDiagLogMs = 0;
    private static void pushFeToNeighbors(BlockEntity be, BackpackFeProvider provider) {
        Level level = be.getLevel();
        if (level == null) return;
        if (provider.storage.getEnergyStored() <= 0) return;

        BlockPos pos = be.getBlockPos();
        int totalPushed = 0;
        StringBuilder diag = new StringBuilder();
        for (Direction dir : Direction.values()) {
            if (provider.storage.getEnergyStored() <= 0) break;
            BlockPos adjPos = pos.relative(dir);
            BlockEntity adj = level.getBlockEntity(adjPos);
            if (adj == null) {
                // 隣接にブロックは有るけど BE が無い場合がある (vanilla block 等)
                diag.append(dir.getName()).append("=no-be ");
                continue;
            }
            String adjClass = adj.getClass().getSimpleName();
            var capOpt = adj.getCapability(ForgeCapabilities.ENERGY, dir.getOpposite());
            if (!capOpt.isPresent()) {
                diag.append(dir.getName()).append("=").append(adjClass).append("(no-cap) ");
                continue;
            }
            int pushed = capOpt.map(target -> {
                if (!target.canReceive()) return -1; // -1 でフラグ
                int available = provider.storage.extractEnergy(Integer.MAX_VALUE, true);
                if (available <= 0) return 0;
                int accepted = target.receiveEnergy(available, false);
                if (accepted > 0) provider.storage.extractEnergy(accepted, false);
                return accepted;
            }).orElse(0);
            if (pushed == -1) {
                diag.append(dir.getName()).append("=").append(adjClass).append("(no-receive) ");
            } else if (pushed > 0) {
                totalPushed += pushed;
                diag.append(dir.getName()).append("=").append(adjClass).append("(+").append(pushed).append(") ");
            } else {
                diag.append(dir.getName()).append("=").append(adjClass).append("(accepted=0) ");
            }
        }

        long now = System.currentTimeMillis();
        if (totalPushed > 0 && now - lastPushLogMs > 4000) {
            lastPushLogMs = now;
            BackpackArsenalMod.LOGGER.info(
                "[backpack_arsenal] FE pushed: {} FE (storage {}/{}) — {}",
                totalPushed, provider.storage.getEnergyStored(),
                provider.storage.getMaxEnergyStored(), diag.toString().trim());
        } else if (totalPushed == 0 && now - lastDiagLogMs > 4000
                   && provider.storage.getEnergyStored() > 0) {
            // FE は貯まってるのに 1 つも送れない時の診断ログ
            lastDiagLogMs = now;
            BackpackArsenalMod.LOGGER.info(
                "[backpack_arsenal] FE push had no takers (storage {}/{}): {}",
                provider.storage.getEnergyStored(),
                provider.storage.getMaxEnergyStored(),
                diag.length() == 0 ? "no adjacent blocks" : diag.toString().trim());
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
