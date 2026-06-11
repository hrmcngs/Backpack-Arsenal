package backpackarsenal.energy;

import backpackarsenal.item.VoltaicBladeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * voltaic_blade の {@link IEnergyStorage} 実装。 ItemStack の NBT
 * "BackpackCharge" を Forge Energy の単位 (FE) として公開する wrapper。
 *
 * 変換: {@link #FE_PER_CHARGE} FE = 1 BackpackCharge ユニット。
 * voltaic_blade の最大チャージは rarity で 1024〜12288 なので、 FE 換算では
 * 102,400〜1,228,800 FE。
 *
 * 受け取り専用 (canExtract = false)。 外部 (Mekanism cube / induction matrix /
 * Create's electric conduit など) から FE を流し込んで blade をチャージできる。
 */
public class VoltaicBladeEnergyStorage implements IEnergyStorage {

    /** 1 BackpackCharge を何 FE 相当で表すか。 */
    public static final int FE_PER_CHARGE = 100;
    /** 1 receiveEnergy 呼び出しあたりの受け入れ上限 (= 充電速度の rate-limit)。
     *  Mekanism cube は無制限に push するので、 ここで絞らないと 1 tick で満タンに
     *  なってしまう。 1000 FE/tick = 10 charge/tick = 200 charge/秒。 */
    public static final int MAX_RECEIVE_PER_CALL = 1000;

    private final ItemStack stack;

    public VoltaicBladeEnergyStorage(ItemStack stack) {
        this.stack = stack;
    }

    @Override
    public int getEnergyStored() {
        return VoltaicBladeItem.getCharge(stack) * FE_PER_CHARGE;
    }

    @Override
    public int getMaxEnergyStored() {
        return VoltaicBladeItem.getMaxCharge(stack) * FE_PER_CHARGE;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        int charge = VoltaicBladeItem.getCharge(stack);
        int maxCharge = VoltaicBladeItem.getMaxCharge(stack);
        if (charge >= maxCharge) return 0;

        int feRoom = (maxCharge - charge) * FE_PER_CHARGE;
        int accepted = Math.min(MAX_RECEIVE_PER_CALL, Math.min(maxReceive, feRoom));
        int chargeToAdd = accepted / FE_PER_CHARGE;
        if (chargeToAdd <= 0) return 0;
        if (!simulate) {
            VoltaicBladeItem.addCharge(stack, chargeToAdd);
        }
        return chargeToAdd * FE_PER_CHARGE;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return 0; // blade からエネルギーを抜き取る用途は無い (= 受け取り専用)
    }

    @Override
    public boolean canExtract() { return false; }

    @Override
    public boolean canReceive() { return true; }
}
