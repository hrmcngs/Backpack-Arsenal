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
    /** 残りの neighbor update 発火回数。 BE 構築時に {@link #INITIAL_NOTIFY_TICKS} で
     *  初期化され、 onLevelTickFast の各 tick で 1 ずつ消費。 0 になったら以後 no-op。
     *  パフォーマンス重視のため初期値は短く ( = 5 tick = 0.25 秒)、 必要十分な時間で完了する。
     *  既存設置の backpack も world load 時に AttachCapabilitiesEvent が再発火するので
     *  この値も自動的にリセットされる。 */
    public int notifyTicksRemaining = INITIAL_NOTIFY_TICKS;
    public static final int INITIAL_NOTIFY_TICKS = 5;

    /** 直近 10-tick (0.5 秒) の発電能力 ( base × multiplier、 blade 不在時は 0 )。 UI 表示用
     *  ( FE/s = この値 × 2 )。 バッファ空き容量による制限は反映しないので、 upgrade の効果
     *  ( multiplier ) が UI で素直に確認できる。 BackpackFeEvents.onLevelTick が書き込む。 */
    public int lastGenPerInterval = 0;

    /** 毎 tick 発電する量 ( = baseFePerTick × multiplier、 blade 不在時は 0 )。
     *  外部 drain と発電を per-tick で平準化し、 cable が "0 / 5 FE/t を行き来" する状態を防ぐ。
     *  10-tick gate の重いスロットスキャンで再計算され、 fastTick が毎 tick 適用する。 */
    public volatile int cachedFePerTick = 0;

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
