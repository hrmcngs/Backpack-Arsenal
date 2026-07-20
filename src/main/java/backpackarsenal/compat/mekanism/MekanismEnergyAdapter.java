package backpackarsenal.compat.mekanism;

import backpackarsenal.energy.BackpackFeStorage;
import mekanism.api.Action;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.math.FloatingLong;

/**
 * Mekanism native energy 連携の adapter。 {@link BackpackFeStorage} を
 * Mekanism の {@link IStrictEnergyHandler} として公開する。
 *
 * これで Mekanism 側 (Energy Cube / Universal Cable / Induction Matrix) は
 * 我々の backpack を **「Mekanism native の発電機」** として認識する。
 * FE ↔ Joules の変換ブリッジを通さないので Mekanism config の
 * {@code JoulesToForge} の影響を受けない。
 *
 * <p>変換方針: 内部 FE 値を <b>1 FE = 1 J</b> として扱う ( = 数値そのまま)。
 * Mekanism の標準 (1 FE = 2.5 J) より厳しいレートだが、 user は Mekanism config
 * で調整可能。</p>
 *
 * <p>この class は Mekanism API の型を import しているため、 Mekanism が
 * 読み込まれていない環境で**class load してはいけない**。
 * {@link MekanismCompat} がロード判定を行ったうえで初めて参照する。</p>
 */
public class MekanismEnergyAdapter implements IStrictEnergyHandler {

    private final BackpackFeStorage storage;

    public MekanismEnergyAdapter(BackpackFeStorage storage) {
        this.storage = storage;
    }

    @Override
    public int getEnergyContainerCount() {
        return 1; // 1 つの container だけ持つ単純な発電機
    }

    @Override
    public FloatingLong getEnergy(int container) {
        if (container != 0) return FloatingLong.ZERO;
        // long 値をそのまま公開 ( int にクランプしない = Mekanism 経由の搬出は無制限 )。
        return FloatingLong.createConst(storage.getEnergyStoredLong());
    }

    @Override
    public void setEnergy(int container, FloatingLong energy) {
        if (container != 0) return;
        // FloatingLong → long 変換 (capacity で clamp)
        long v = Math.min(energy.longValue(), storage.getMaxEnergyStoredLong());
        storage.setEnergy(Math.max(0L, v));
    }

    @Override
    public FloatingLong getMaxEnergy(int container) {
        if (container != 0) return FloatingLong.ZERO;
        return FloatingLong.createConst(storage.getMaxEnergyStoredLong());
    }

    @Override
    public FloatingLong getNeededEnergy(int container) {
        if (container != 0) return FloatingLong.ZERO;
        long need = storage.getMaxEnergyStoredLong() - storage.getEnergyStoredLong();
        return FloatingLong.createConst(Math.max(0L, need));
    }

    /**
     * 外部からの注入は受け付けない (発電専用)。 渡された量をそのまま「未受け取り」として返す。
     */
    @Override
    public FloatingLong insertEnergy(int container, FloatingLong amount, Action action) {
        return amount;
    }

    /**
     * 外部 (Mekanism cable / cube) からの取り出しに応じる。
     * Mekanism の Action と Forge の simulate フラグを橋渡しする。
     */
    @Override
    public FloatingLong extractEnergy(int container, FloatingLong amount, Action action) {
        if (container != 0) return FloatingLong.ZERO;
        if (amount.isZero()) return FloatingLong.ZERO;
        // long のまま取り出す ( int クランプしない = 1 tick で int 上限を超える搬出も可 )。
        long extracted = storage.extractLong(amount.longValue(), action.simulate());
        return FloatingLong.createConst(extracted);
    }
}
