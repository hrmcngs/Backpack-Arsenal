package backpackarsenal.energy;

import net.minecraftforge.energy.IEnergyStorage;

/**
 * Arsenal Backpack 専用の発電ストレージ。
 *
 * <p>内部は <b>long</b> で保持し、 high-multiplier ( Voltaic Growth 等で multiplier が
 * 数十万〜) でも int overflow で発電が頭打ちにならないようにする。 capacity /
 * maxExtract を {@code Long.MAX_VALUE} にすれば実質無制限バッファ・無制限搬出。</p>
 *
 * <p>「発電専用」なので外部からの {@link #receiveEnergy(int, boolean)} は常に 0。
 * 内部発電は {@link #generate(long)} 経由。 外部搬出は long 版 {@link #extractLong(long, boolean)}
 * ( Mekanism STRICT_ENERGY 用 = 無制限 ) と、 Forge {@link IEnergyStorage#extractEnergy(int, boolean)}
 * ( int にクランプ = Forge FE API の上限 21.4 億/t が上限 ) の 2 経路。</p>
 *
 * <p>Forge の {@link IEnergyStorage} は int API なので、 int を超える搬出は
 * Mekanism ( FloatingLong ) 経由でのみ可能。 int 版メソッドは long 値を
 * {@code Integer.MAX_VALUE} にクランプして返す。</p>
 */
public class BackpackFeStorage implements IEnergyStorage {

    private long energy;
    private final long capacity;
    private final long maxExtract;

    public BackpackFeStorage(long capacity, long maxExtract) {
        this.capacity = capacity;
        this.maxExtract = maxExtract;
    }

    // ─── long ネイティブ API ( 発電 / 無制限搬出 / 永続化 ) ──────────────────

    /** 発電で内部バッファに積む。 capacity 上限まで。 受け入れた量を返す。 */
    public long generate(long amount) {
        if (amount <= 0) return 0;
        long space = capacity - energy;
        if (space <= 0) return 0;
        long accepted = Math.min(amount, space);
        energy += accepted;
        return accepted;
    }

    /** long 版取り出し ( Mekanism 用 )。 maxExtract 上限 & 残量までで応じ、 取り出した量を返す。 */
    public long extractLong(long maxExtractRequested, boolean simulate) {
        if (maxExtractRequested <= 0 || energy <= 0) return 0;
        long extracted = Math.min(energy, Math.min(this.maxExtract, maxExtractRequested));
        if (extracted <= 0) return 0;
        if (!simulate) energy -= extracted;
        return extracted;
    }

    public long getEnergyStoredLong()    { return energy; }
    public long getMaxEnergyStoredLong() { return capacity; }

    /** NBT デシリアライズ用の直接 setter ( long )。 */
    public void setEnergy(long amount) {
        energy = Math.max(0, Math.min(amount, capacity));
    }

    // ─── Forge IEnergyStorage ( int 境界。 値は Integer.MAX_VALUE にクランプ ) ──

    /** 発電専用: 外部受電は常に拒否。 */
    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        return 0;
    }

    @Override
    public int extractEnergy(int maxExtractRequested, boolean simulate) {
        return (int) Math.min(Integer.MAX_VALUE, extractLong(maxExtractRequested, simulate));
    }

    @Override
    public int getEnergyStored() {
        return (int) Math.min(Integer.MAX_VALUE, energy);
    }

    @Override
    public int getMaxEnergyStored() {
        return (int) Math.min(Integer.MAX_VALUE, capacity);
    }

    @Override
    public boolean canExtract() {
        return maxExtract > 0;
    }

    @Override
    public boolean canReceive() {
        return false;
    }
}
