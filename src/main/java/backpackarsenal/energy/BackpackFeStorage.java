package backpackarsenal.energy;

import backpackarsenal.BackpackArsenalMod;
import net.minecraftforge.energy.EnergyStorage;

/**
 * Arsenal Backpack 専用の Forge Energy ストレージ。
 *
 * 「発電専用」なので外部からの {@link #receiveEnergy(int, boolean)} は常に 0
 * (Forge {@code EnergyStorage} のコンストラクタ第 2 引数 maxReceive=0 で実現)。
 *
 * 内部発電は {@link #generate(int)} 経由で行う (capacity を超える分は捨てる)。
 * 外部 (Mekanism cable / Create / 他 FE pipe) からは {@link #extractEnergy(int, boolean)}
 * だけが効く。
 */
public class BackpackFeStorage extends EnergyStorage {

    public BackpackFeStorage(int capacity, int maxExtract) {
        super(capacity, 0, maxExtract);
    }

    /** 発電で内部バッファに積む。capacity 上限まで。受け入れた量を返す。 */
    public int generate(int amount) {
        if (amount <= 0) return 0;
        int accepted = Math.min(amount, capacity - energy);
        energy += accepted;
        return accepted;
    }

    /** NBT デシリアライズ用の直接 setter。 */
    public void setEnergy(int amount) {
        energy = Math.max(0, Math.min(amount, capacity));
    }

    /** デバッグ: 外部から FE を吸い出された (= cable 接続成功) ことを確認するためログ出力。
     *  spam しないように 4 秒に 1 回だけ出す。 */
    private long lastLogMs = 0;

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        int extracted = super.extractEnergy(maxExtract, simulate);
        if (!simulate && extracted > 0) {
            long now = System.currentTimeMillis();
            if (now - lastLogMs > 4000) {
                lastLogMs = now;
                BackpackArsenalMod.LOGGER.info(
                    "[backpack_arsenal] FE extracted by external: {} FE (remaining {}/{})",
                    extracted, energy, capacity);
            }
        }
        return extracted;
    }
}
