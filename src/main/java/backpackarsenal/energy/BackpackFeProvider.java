package backpackarsenal.energy;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 設置 arsenal_backpack の BlockEntity に attach する FE capability provider。
 *
 * BlockEntity の {@code load}/{@code saveAdditional} とは別経路で NBT 保存される
 * ({@link net.minecraftforge.common.capabilities.SerializableCapabilityProvider} の
 *  Forge 内部メカニズム)。 ブロックを壊すと storage は失われる仕様。
 */
public class BackpackFeProvider implements ICapabilitySerializable<CompoundTag> {

    public final BackpackFeStorage storage;
    private final LazyOptional<IEnergyStorage> opt;

    public BackpackFeProvider(int capacity, int maxExtract) {
        this.storage = new BackpackFeStorage(capacity, maxExtract);
        this.opt = LazyOptional.of(() -> storage);
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return cap == ForgeCapabilities.ENERGY ? opt.cast() : LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Energy", storage.getEnergyStored());
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        storage.setEnergy(tag.getInt("Energy"));
    }

    /** BlockEntity 破棄時に LazyOptional を無効化 (cache holder が掴んだままにならないように)。 */
    public void invalidate() {
        opt.invalidate();
    }
}
