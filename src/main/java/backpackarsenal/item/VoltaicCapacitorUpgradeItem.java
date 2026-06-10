package backpackarsenal.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Voltaic Capacitor Upgrade — voltaic_blade の最大充電をアンビルで強化する素材。
 *
 * 3 tier (I=+256, II=+512, III=+1024) を {@link backpackarsenal.init.ArsenalItems} で個別登録する。
 * アンビル handler ({@link backpackarsenal.event.VoltaicCapacitorAnvilHandler}) が blade の
 * {@link VoltaicBladeItem#TAG_CAPACITOR_STAGES} に bonus 値を append し、 右スタック数分だけ
 * 一気に消費する (= shift 関係なく bulk)。 既存 stage は sneak+RC で 1 つずつ剥がせる。
 *
 * Survival では合計 {@link VoltaicBladeItem#MAX_CAPACITOR_BONUS} stage まで。
 * Creative は上限なし (anvil handler 側で player.isCreative() で bypass)。
 */
public class VoltaicCapacitorUpgradeItem extends Item {

    /** 1 個適用したときの +bonus 量 (256 / 512 / 1024)。 strongest tier = 1024。 */
    private final int bonusValue;

    public VoltaicCapacitorUpgradeItem(int bonusValue) {
        super(new Item.Properties()
            .rarity(bonusValue >= VoltaicBladeItem.CAPACITOR_TIER_III_BONUS ? Rarity.RARE
                : bonusValue >= VoltaicBladeItem.CAPACITOR_TIER_II_BONUS  ? Rarity.UNCOMMON
                : Rarity.COMMON)
            .stacksTo(16));
        this.bonusValue = bonusValue;
    }

    public int getBonusValue() {
        return bonusValue;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable(
            "item.backpack_arsenal.voltaic_capacitor_upgrade.tooltip_args",
            bonusValue, VoltaicBladeItem.MAX_CAPACITOR_BONUS
        ).withStyle(ChatFormatting.GRAY));
    }
}
