package backpackarsenal.energy;

import backpackarsenal.BackpackArsenalMod;
import backpackarsenal.init.ArsenalBackpackConfig;
import backpackarsenal.init.ArsenalBlocks;
import backpackarsenal.item.VoltaicBladeItem;
import net.minecraft.resources.ResourceLocation;
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

    /** tracked placed-backpack BEs (weak → BE が GC されたら自動消滅)。 */
    private static final Map<BlockEntity, BackpackFeProvider> tracked =
        Collections.synchronizedMap(new WeakHashMap<>());

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

        int chargePerInterval = VoltaicBladeItem.CHARGE_PER_TICK_IN_BACKPACK * 10;
        int fePerInterval = ArsenalBackpackConfig.feGenPerTick * 10;
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

    /** chargeBladesAndReport の戻り値。 診断ログ用に詳細を返す。 */
    private static final class ChargeReport {
        boolean chargedAny;
        int slotsScanned;
        int bladesFound;
        int bladesAlreadyFull;
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
