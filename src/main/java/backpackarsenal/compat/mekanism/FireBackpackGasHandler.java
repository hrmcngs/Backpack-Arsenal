package backpackarsenal.compat.mekanism;

import mekanism.api.Action;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasHandler;
import mekanism.common.registries.MekanismGases;
import mekanism.api.providers.IGasProvider;
import backpackarsenal.upgrade.GasSelectorUpgradeItem;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/** One-tank, output-only selectable combustible/process gas store. */
public class FireBackpackGasHandler implements IGasHandler {
    public static final long CAPACITY = 64_000L;
    public static final long GENERATE_PER_TICK = 20L;
    public static final long MAX_OUTPUT_PER_TICK = 200L;
    private static final String NBT_AMOUNT = "BackpackArsenalGasAmount";
    private static final String NBT_MODE = "BackpackArsenalGasMode";
    private static final String LEGACY_HYDROGEN_CHLORIDE_NBT_AMOUNT = "BackpackArsenalHydrogenChloride";
    private static final String LEGACY_HYDROGEN_NBT_AMOUNT = "BackpackArsenalHydrogen";

    private final Supplier<ItemStack> backpack;
    private GasSelectorUpgradeItem.Mode mode = GasSelectorUpgradeItem.Mode.ETHENE;
    private long stored;
    private boolean loadedFromItem;

    public FireBackpackGasHandler(Supplier<ItemStack> backpack) {
        this.backpack = backpack;
    }

    public long getStored() {
        ItemStack stack = backpack.get();
        // The SB wrapper's ItemStack is briefly empty while a placed backpack is loading.
        // Do not treat that transient state as an empty gas tank or generation is discarded.
        if (!loadedFromItem && !stack.isEmpty()) {
            loadedFromItem = true;
            if (stack.getTag() != null) {
                var tag = stack.getTag();
                long amount = tag.contains(NBT_AMOUNT) ? tag.getLong(NBT_AMOUNT)
                    : tag.contains(LEGACY_HYDROGEN_CHLORIDE_NBT_AMOUNT)
                        ? tag.getLong(LEGACY_HYDROGEN_CHLORIDE_NBT_AMOUNT)
                        : tag.getLong(LEGACY_HYDROGEN_NBT_AMOUNT);
                stored = Math.max(0L, Math.min(CAPACITY, amount));
            }
        }
        return stored;
    }

    private void setStored(long amount) {
        stored = Math.max(0L, Math.min(CAPACITY, amount));
        ItemStack stack = backpack.get();
        if (!stack.isEmpty()) {
            loadedFromItem = true;
            var tag = stack.getOrCreateTag();
            tag.putLong(NBT_AMOUNT, stored);
            tag.putString(NBT_MODE, mode.name());
            tag.remove(LEGACY_HYDROGEN_CHLORIDE_NBT_AMOUNT);
            tag.remove(LEGACY_HYDROGEN_NBT_AMOUNT);
        }
    }

    public void generate(long amount) {
        if (amount > 0) setStored(getStored() + amount);
    }

    public void setMode(GasSelectorUpgradeItem.Mode newMode) {
        if (newMode == null) newMode = GasSelectorUpgradeItem.Mode.ETHENE;
        if (mode != newMode) {
            mode = newMode;
            setStored(0); // never transmute gas already in the tank
        }
    }

    public GasSelectorUpgradeItem.Mode getMode() { return mode; }

    public IGasProvider getGas() {
        return mode == GasSelectorUpgradeItem.Mode.HYDROGEN_CHLORIDE
            ? MekanismGases.HYDROGEN_CHLORIDE : MekanismGases.ETHENE;
    }

    @Override public int getTanks() { return 1; }
    @Override public GasStack getChemicalInTank(int tank) {
        return tank == 0 && getStored() > 0
            ? new GasStack(getGas(), getStored()) : GasStack.EMPTY;
    }
    @Override public void setChemicalInTank(int tank, GasStack stack) {
        if (tank == 0) setStored(stack.isEmpty() ? 0 :
            (stack.isTypeEqual(getGas().getChemical()) ? stack.getAmount() : 0));
    }
    @Override public long getTankCapacity(int tank) { return tank == 0 ? CAPACITY : 0; }
    @Override public boolean isValid(int tank, GasStack stack) {
        return tank == 0 && stack.isTypeEqual(getGas().getChemical());
    }
    @Override public GasStack insertChemical(int tank, GasStack stack, Action action) {
        return stack; // generator: external insertion is disabled
    }
    @Override public GasStack extractChemical(int tank, long amount, Action action) {
        if (tank != 0 || amount <= 0 || getStored() <= 0) return GasStack.EMPTY;
        long extracted = Math.min(getStored(), amount);
        if (action.execute()) setStored(getStored() - extracted);
        return new GasStack(getGas(), extracted);
    }
    @Override public GasStack getEmptyStack() { return GasStack.EMPTY; }
}
