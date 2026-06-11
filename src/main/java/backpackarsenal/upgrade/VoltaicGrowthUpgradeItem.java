package backpackarsenal.upgrade;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.p3pp3rf1y.sophisticatedbackpacks.Config;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeItemBase;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeType;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeWrapperBase;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Voltaic Growth Charger Upgrade — NBT に "Level" を持ち、 アンビルで素材 (redstone)
 * を投入すると level が上がっていく、 上限無しの成長型 charger。
 *
 * - Level 0 → 倍率寄与 0 (= スロット食うだけで効果なし、 弱い)
 * - Level N → 倍率寄与 N
 * - 全 multiplier = 1 + Σ (各 upgrade の寄与)
 *
 * 既存の {@link VoltaicChargerUpgradeItem} とは別 {@link UpgradeType} なので
 * 両方混在させられる ( = 6 個までのスロットに自由に混ぜる)。
 *
 * アンビルでの強化: {@link backpackarsenal.event.VoltaicGrowthAnvilHandler} 参照。
 */
public class VoltaicGrowthUpgradeItem extends UpgradeItemBase<VoltaicGrowthUpgradeItem.Wrapper> {

    public static final UpgradeType<Wrapper> TYPE = new UpgradeType<>(Wrapper::new);

    public static final String TAG_LEVEL = "Level";

    public VoltaicGrowthUpgradeItem() {
        super(Config.SERVER.maxUpgradesPerStorage);
    }

    public static int getLevel(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return 0;
        return Math.max(0, tag.getInt(TAG_LEVEL));
    }

    public static void setLevel(ItemStack stack, int level) {
        stack.getOrCreateTag().putInt(TAG_LEVEL, Math.max(0, level));
    }

    /** 倍率寄与 = level (Level 0 で 0 = 無効、 Level 100 で +100x)。 */
    public static int getMultiplierContributionFor(ItemStack stack) {
        return getLevel(stack);
    }

    /** 1 level 上げるのに必要な redstone 数。 level に関係なく常に同じ ( = フラットコスト )。
     *  vanilla anvil の "Too Expensive!" 閾値 (40 lvl) を考慮しつつ、 1 stack でしっかり
     *  level が伸びるよう低めの値に設定。 */
    public static final int REDSTONE_COST_PER_LEVEL = 1;
    /** 1 level 上げるのに必要な XP (level)。 同じくフラット。 */
    public static final int XP_COST_PER_LEVEL = 1;

    /** 次の level に必要な redstone 数。 level に依存しないフラットコスト。 */
    public static int redstoneCostForNextLevel(int currentLevel) {
        return REDSTONE_COST_PER_LEVEL;
    }

    /** 次の level の XP コスト (lvl)。 level に依存しないフラットコスト。 */
    public static int xpCostForNextLevel(int currentLevel) {
        return XP_COST_PER_LEVEL;
    }

    @Override
    public UpgradeType<Wrapper> getType() {
        return TYPE;
    }

    @Override
    public List<UpgradeConflictDefinition> getUpgradeConflicts() {
        return List.of();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        int lv = getLevel(stack);
        tooltip.add(Component.translatable(
            "item.backpack_arsenal.voltaic_growth_charger_upgrade.level_tooltip", lv
        ).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(
            "item.backpack_arsenal.voltaic_growth_charger_upgrade.multiplier_tooltip", lv
        ).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
            "item.backpack_arsenal.voltaic_growth_charger_upgrade.next_tooltip",
            redstoneCostForNextLevel(lv), xpCostForNextLevel(lv)
        ).withStyle(ChatFormatting.DARK_GRAY));
    }

    public static class Wrapper extends UpgradeWrapperBase<Wrapper, VoltaicGrowthUpgradeItem> {
        public Wrapper(IStorageWrapper backpackWrapper, ItemStack upgrade, Consumer<ItemStack> upgradeSaveHandler) {
            super(backpackWrapper, upgrade, upgradeSaveHandler);
        }

        public int getMultiplierContribution() {
            return getMultiplierContributionFor(upgrade);
        }
    }
}
