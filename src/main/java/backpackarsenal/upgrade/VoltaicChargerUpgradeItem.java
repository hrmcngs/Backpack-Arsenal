package backpackarsenal.upgrade;

import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedbackpacks.Config;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeItemBase;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeType;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeWrapperBase;

import java.util.List;
import java.util.function.Consumer;

/**
 * Voltaic Charger Upgrade — Sophisticated Backpacks の upgrade slot に挿せる純粋なマーカー。
 * {@link backpackarsenal.event.BackpackChargingHandler} が
 * {@code upgradeHandler.getTypeWrappers(TYPE)} で枚数を引き、 voltaic_blade の充電速度を倍率
 * アップする。
 *
 * 実装は SB の {@code EverlastingUpgradeItem} と同じ構造に統一:
 *   - Wrapper は inner static class
 *   - 上限設定は {@link Config#SERVER}.maxUpgradesPerStorage を直接利用
 *     (SB が用意してる sophisticated config 経由の上限管理に乗っかる)
 *   - getUpgradeConflicts は空 = 競合なし
 */
public class VoltaicChargerUpgradeItem extends UpgradeItemBase<VoltaicChargerUpgradeItem.Wrapper> {

    public static final UpgradeType<Wrapper> TYPE = new UpgradeType<>(Wrapper::new);

    public VoltaicChargerUpgradeItem() {
        super(Config.SERVER.maxUpgradesPerStorage);
        backpackarsenal.BackpackArsenalMod.LOGGER.info(
            "[backpack_arsenal] [DIAG-V2] VoltaicChargerUpgradeItem ctor: " +
            "instanceof IUpgradeItem={}, Config.SERVER={}, maxUpgradesPerStorage={}",
            (this instanceof net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeItem),
            Config.SERVER,
            Config.SERVER == null ? "null" : Config.SERVER.maxUpgradesPerStorage);
    }

    @Override
    public UpgradeType<Wrapper> getType() {
        // getType は SB が wrapper を作る瞬間に呼ばれる。 ここに log があれば SB が
        // 我々の item を upgrade として認識してインサート処理に入った証拠。
        if (!getTypeLogged) {
            getTypeLogged = true;
            backpackarsenal.BackpackArsenalMod.LOGGER.info(
                "[backpack_arsenal] [DIAG-V2] VoltaicChargerUpgradeItem.getType() invoked (first time)");
        }
        return TYPE;
    }
    private static boolean getTypeLogged = false;

    @Override
    public List<UpgradeConflictDefinition> getUpgradeConflicts() {
        return List.of();
    }

    public static class Wrapper extends UpgradeWrapperBase<Wrapper, VoltaicChargerUpgradeItem> {
        public Wrapper(IStorageWrapper backpackWrapper, ItemStack upgrade, Consumer<ItemStack> upgradeSaveHandler) {
            super(backpackWrapper, upgrade, upgradeSaveHandler);
            // Wrapper の ctor が呼ばれる = SB が UpgradeType.create() を呼んで wrapper を
            // 作った = slot に正常に挿入された。 ここに log があれば成功確定。
            backpackarsenal.BackpackArsenalMod.LOGGER.info(
                "[backpack_arsenal] [DIAG-V2] VoltaicChargerUpgradeItem.Wrapper ctor: " +
                "upgrade stack item class={}", upgrade.getItem().getClass().getSimpleName());
        }
    }
}
