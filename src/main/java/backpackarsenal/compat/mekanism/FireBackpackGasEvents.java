package backpackarsenal.compat.mekanism;

import backpackarsenal.BackpackArsenalMod;
import backpackarsenal.init.ArsenalBlocks;
import mekanism.api.chemical.gas.GasStack;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackBlockEntity;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Generates hydrogen chloride in placed Fire backpacks and pushes it into adjacent Mekanism handlers. */
@Mod.EventBusSubscriber(modid = BackpackArsenalMod.MODID)
public class FireBackpackGasEvents {
    private static final ResourceLocation KEY =
        new ResourceLocation(BackpackArsenalMod.MODID, "fire_backpack_gas");
    private static final Map<BlockEntity, FireBackpackGasHandler> TRACKED =
        Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean loggedFirstAttach;
    private static boolean loggedFirstTransfer;

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<BlockEntity> event) {
        BlockEntity be = event.getObject();
        if (be.getBlockState().getBlock() != ArsenalBlocks.ARSENAL_BACKPACK_FIRE_BLOCK.get()) return;
        if (!(be instanceof BackpackBlockEntity backpackBe)) return;

        FireBackpackGasHandler handler = new FireBackpackGasHandler(
            () -> backpackBe.getBackpackWrapper().getBackpack());
        LazyOptional<mekanism.api.chemical.gas.IGasHandler> optional = LazyOptional.of(() -> handler);
        ICapabilityProvider provider = new ICapabilityProvider() {
            @Override
            public <T> LazyOptional<T> getCapability(net.minecraftforge.common.capabilities.Capability<T> cap,
                                                     Direction side) {
                return cap == Capabilities.GAS_HANDLER ? optional.cast() : LazyOptional.empty();
            }
        };
        event.addCapability(KEY, provider);
        event.addListener(optional::invalidate);
        TRACKED.put(be, handler);
        if (!loggedFirstAttach) {
            loggedFirstAttach = true;
            BackpackArsenalMod.LOGGER.info(
                "[backpack_arsenal] Fire backpack gas generator attached at {}", be.getBlockPos());
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide || TRACKED.isEmpty()) return;
        synchronized (TRACKED) {
            var iterator = TRACKED.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                BlockEntity source = entry.getKey();
                if (source == null || source.isRemoved()) { iterator.remove(); continue; }
                if (source.getLevel() != event.level) continue;
                FireBackpackGasHandler gas = entry.getValue();
                gas.setMode(selectedMode(source));
                gas.generate(FireBackpackGasHandler.GENERATE_PER_TICK);
                push(source, gas);
            }
        }
    }

    private static void push(BlockEntity source, FireBackpackGasHandler gas) {
        if (gas.getStored() <= 0 || source.getLevel() == null) return;
        long budget = Math.min(gas.getStored(), FireBackpackGasHandler.MAX_OUTPUT_PER_TICK);
        GasStack offered = new GasStack(gas.getGas(), budget);
        // Use Mekanism's own machine-output path. This handles pressurized tubes and
        // transmitter networks, whose handlers may reject a plain direct insertion.
        long sent = mekanism.common.util.ChemicalUtil.emit(
            java.util.EnumSet.allOf(Direction.class), offered, source);
        if (sent > 0) {
            gas.extractChemical(sent, mekanism.api.Action.EXECUTE);
            if (!loggedFirstTransfer) {
                loggedFirstTransfer = true;
                BackpackArsenalMod.LOGGER.info(
                    "[backpack_arsenal] Fire backpack emitted {} mB of {} into Mekanism network",
                    sent, gas.getMode());
            }
        }
    }

    private static backpackarsenal.upgrade.GasSelectorUpgradeItem.Mode selectedMode(BlockEntity be) {
        if (!(be instanceof BackpackBlockEntity backpackBe)) {
            return backpackarsenal.upgrade.GasSelectorUpgradeItem.Mode.ETHENE;
        }
        try {
            for (var wrapper : backpackBe.getBackpackWrapper().getUpgradeHandler()
                    .getTypeWrappers(backpackarsenal.upgrade.GasSelectorUpgradeItem.TYPE)) {
                if (wrapper != null && wrapper.isEnabled()) return wrapper.getMode();
            }
        } catch (Throwable ignored) {}
        return backpackarsenal.upgrade.GasSelectorUpgradeItem.Mode.ETHENE;
    }
}
