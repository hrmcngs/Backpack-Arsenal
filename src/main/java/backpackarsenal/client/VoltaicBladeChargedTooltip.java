package backpackarsenal.client;

import backpackarsenal.BackpackArsenalMod;
import backpackarsenal.item.VoltaicBladeItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import the_four_primitives_and_weapons.ElementalTooltipEvent;

import java.util.List;

/**
 * MAW の {@link ElementalTooltipEvent} が出す属性行 ( "Electric Element N" 等、 属性色 +
 * ローマ数字 ) に、 voltaic_blade が充電済みのとき "(charged)" を追記するデコレータ。
 *
 * <p>voltaic_blade のレベル表示は MAW 行に一本化している ( 我々の appendHoverText は
 * レベル行を出さない )。 MAW 行は {@code ItemTooltipEvent} で挿入されるので、 それより後に
 * 走らせるため {@link EventPriority#LOWEST} で購読し、 該当行を見つけて suffix を足す。</p>
 *
 * <p>行の特定は MAW と同じ文字列 ( 属性名 + " " + ローマ数字 ) を再構築して一致比較する。
 * 見つからなければ何もしない ( クラッシュしない )。</p>
 */
@Mod.EventBusSubscriber(
    modid = BackpackArsenalMod.MODID,
    value = Dist.CLIENT,
    bus = Mod.EventBusSubscriber.Bus.FORGE
)
public class VoltaicBladeChargedTooltip {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof VoltaicBladeItem)) return;
        if (VoltaicBladeItem.getCharge(stack) <= 0) return;

        int level = VoltaicBladeItem.getEffectiveElementLevel(stack);
        if (level <= 0) return;

        // MAW 行の文字列を再構築 ( 属性名 + " " + ローマ数字 )。
        String elementKey = VoltaicBladeItem.isThunderMode(stack)
                ? "tooltip.the_four_primitives_and_weapons.element.thunder"
                : "tooltip.the_four_primitives_and_weapons.element.electric";
        String expected = I18n.get(elementKey) + " " + ElementalTooltipEvent.toRoman(level);

        List<Component> tip = event.getToolTip();
        for (int i = 0; i < tip.size(); i++) {
            if (expected.equals(tip.get(i).getString())) {
                // MAW 行 ( 属性色 ) をそのまま活かし、 gray の "(charged)" を後置。
                tip.set(i, Component.empty()
                        .append(tip.get(i))
                        .append(Component.translatable("item.backpack_arsenal.voltaic_blade.charged_suffix")
                                .withStyle(ChatFormatting.GRAY)));
                return;
            }
        }
    }
}
