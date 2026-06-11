package backpackarsenal.energy;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * voltaic_blade ItemStack に {@link VoltaicBladeEnergyStorage} を attach する
 * ICapabilityProvider。 NBT 永続化は不要 ( BackpackCharge は元から ItemStack NBT
 * で永続化されてる )。
 */
public class VoltaicBladeEnergyProvider implements ICapabilityProvider {

    private final LazyOptional<IEnergyStorage> opt;

    public VoltaicBladeEnergyProvider(ItemStack stack) {
        this.opt = LazyOptional.of(() -> new VoltaicBladeEnergyStorage(stack));
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return cap == ForgeCapabilities.ENERGY ? opt.cast() : LazyOptional.empty();
    }
}
