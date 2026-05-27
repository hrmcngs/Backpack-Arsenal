package backpackarsenal.upgrade;

import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeWrapperBase;

import java.util.function.Consumer;

/**
 * Voltaic Charger Upgrade のランタイムラッパー。状態を持たないマーカーで、
 * BackpackChargingHandler が「このアップグレードがあれば充電倍率を上げる」用途で
 * インスタンスを数えるために使う。
 */
public class VoltaicChargerUpgradeWrapper
        extends UpgradeWrapperBase<VoltaicChargerUpgradeWrapper, VoltaicChargerUpgradeItem> {

    public VoltaicChargerUpgradeWrapper(
            IStorageWrapper storageWrapper,
            ItemStack upgrade,
            Consumer<ItemStack> upgradeSaveHandler) {
        super(storageWrapper, upgrade, upgradeSaveHandler);
    }
}
