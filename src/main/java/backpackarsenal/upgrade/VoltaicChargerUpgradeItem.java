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
 * Voltaic Charger Upgrade — SB の upgrade slot に挿せるマーカー。
 *
 * tier 0 (base) 〜 tier 5 まで 6 段階。 各 tier の倍率寄与は (tier+1)^2:
 *   base=+1, tier1=+4, tier2=+9, tier3=+16, tier4=+25, tier5=+36
 * {@link backpackarsenal.event.BackpackChargingHandler} が backpack 内の全 wrapper の
 * 寄与を合計して充電倍率に反映する (multiplier = 1 + Σ contributions)。
 *
 * 全 tier は同じ {@link #TYPE} を共有しているので、 SB の {@code getTypeWrappers(TYPE)} で
 * 一度に取れる。
 */
public class VoltaicChargerUpgradeItem extends UpgradeItemBase<VoltaicChargerUpgradeItem.Wrapper> {

    public static final UpgradeType<Wrapper> TYPE = new UpgradeType<>(Wrapper::new);

    /** tier max (0..5 = 6 段階) */
    public static final int MAX_TIER = 5;

    private final int tier;

    public VoltaicChargerUpgradeItem(int tier) {
        super(Config.SERVER.maxUpgradesPerStorage);
        this.tier = tier;
    }

    public int getTier() {
        return tier;
    }

    /** 倍率寄与 = (tier+1)^2。 base=1, tier1=4, tier2=9, tier3=16, tier4=25。 */
    public int getMultiplierContribution() {
        int t = tier + 1;
        return t * t;
    }

    @Override
    public UpgradeType<Wrapper> getType() {
        return TYPE;
    }

    @Override
    public List<UpgradeConflictDefinition> getUpgradeConflicts() {
        return List.of();
    }

    public static class Wrapper extends UpgradeWrapperBase<Wrapper, VoltaicChargerUpgradeItem> {
        public Wrapper(IStorageWrapper backpackWrapper, ItemStack upgrade, Consumer<ItemStack> upgradeSaveHandler) {
            super(backpackWrapper, upgrade, upgradeSaveHandler);
        }

        public int getMultiplierContribution() {
            return upgradeItem.getMultiplierContribution();
        }
    }
}
