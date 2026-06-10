package backpackarsenal.energy;

import backpackarsenal.BackpackArsenalMod;
import backpackarsenal.init.ArsenalBackpackConfig;
import backpackarsenal.init.ArsenalBlocks;
import backpackarsenal.item.VoltaicBladeItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

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
        // 我々の block 上の BE だけ対象 (SB の BackpackBlockEntity は他の SB backpack も使う)
        if (be.getLevel() == null) return;
        var block = be.getBlockState().getBlock();
        if (block != ArsenalBlocks.ARSENAL_BACKPACK_ELECTRON_BLOCK.get()) return;

        BackpackFeProvider provider = new BackpackFeProvider(
            ArsenalBackpackConfig.feCapacity,
            ArsenalBackpackConfig.feMaxExtract);
        event.addCapability(CAP_KEY, provider);
        event.addListener(provider::invalidate);
        tracked.put(be, provider);
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.level.isClientSide) return;
        if (event.level.getGameTime() % 10 != 0) return;

        int chargePerInterval = VoltaicBladeItem.CHARGE_PER_TICK_IN_BACKPACK * 10;
        int fePerInterval = ArsenalBackpackConfig.feGenPerTick * 10;

        synchronized (tracked) {
            Iterator<Map.Entry<BlockEntity, BackpackFeProvider>> it = tracked.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<BlockEntity, BackpackFeProvider> entry = it.next();
                BlockEntity be = entry.getKey();
                if (be == null || be.isRemoved()) { it.remove(); continue; }

                BackpackFeProvider provider = entry.getValue();
                be.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
                    boolean charged = chargeBladesAndReport(handler, chargePerInterval);
                    if (charged) {
                        provider.storage.generate(fePerInterval);
                    }
                });
            }
        }
    }

    /** ハンドラ内の voltaic_blade を充電し、 1 本でも充電できた (= 満タンでなかった) かを返す。
     *  ネストされた SB backpack の再帰は省略 (top-level だけ対象)。 */
    private static boolean chargeBladesAndReport(IItemHandler handler, int amount) {
        boolean any = false;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            var inner = handler.getStackInSlot(slot);
            if (inner.isEmpty()) continue;
            if (!(inner.getItem() instanceof VoltaicBladeItem)) continue;

            int before = VoltaicBladeItem.getCharge(inner);
            if (before >= VoltaicBladeItem.getMaxCharge(inner)) continue;

            VoltaicBladeItem.addCharge(inner, amount);
            if (handler instanceof IItemHandlerModifiable mod) {
                mod.setStackInSlot(slot, inner);
            }
            any = true;
        }
        return any;
    }
}
