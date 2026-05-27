package backpackarsenal.upgrade;

import net.minecraft.resources.ResourceLocation;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeCountLimitConfig;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeItem;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeGroup;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeItemBase;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeType;

import java.util.List;

/**
 * Voltaic Charger Upgrade — SB バックパックのアップグレードスロットに挿せる
 * アイテム。本MOD の BackpackChargingHandler がこのアップグレード数をカウントし、
 * voltaic_blade の充電速度を倍率アップする (1枚で 2倍、2枚で 3倍 ...)。
 *
 * 1 backpack あたり最大 3枚 (調整可)。
 * 副作用は無く、純粋なマーカーとして機能する。
 */
public class VoltaicChargerUpgradeItem extends UpgradeItemBase<VoltaicChargerUpgradeWrapper> {

    /** 1 ストレージあたりの最大装着枚数 */
    public static final int MAX_PER_STORAGE = 3;

    private static final IUpgradeCountLimitConfig LIMIT_CONFIG = new IUpgradeCountLimitConfig() {
        @Override
        public int getMaxUpgradesPerStorage(String storageType, ResourceLocation upgrade) {
            return MAX_PER_STORAGE;
        }
        @Override
        public int getMaxUpgradesInGroupPerStorage(String storageType, UpgradeGroup group) {
            return Integer.MAX_VALUE;
        }
    };

    /** SB が wrapper を作る factory として登録する UpgradeType */
    public static final UpgradeType<VoltaicChargerUpgradeWrapper> TYPE =
        new UpgradeType<>(VoltaicChargerUpgradeWrapper::new);

    public VoltaicChargerUpgradeItem() {
        super(LIMIT_CONFIG);
    }

    @Override
    public UpgradeType<VoltaicChargerUpgradeWrapper> getType() {
        return TYPE;
    }

    @Override
    public List<IUpgradeItem.UpgradeConflictDefinition> getUpgradeConflicts() {
        return List.of();
    }
}
