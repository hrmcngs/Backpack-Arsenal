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
 * Voltaic Element Core — 金床で voltaic_blade と合成すると属性を
 * 電気 (ELECTRIC) ⇄ 雷 (THUNDER) にトグルする特殊アイテム。
 *
 * 合成ロジックは {@link backpackarsenal.event.VoltaicElementSwitchAnvilHandler}。
 */
public class VoltaicElementCoreItem extends Item {

    public VoltaicElementCoreItem() {
        super(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("item.backpack_arsenal.voltaic_element_core.tooltip"));
        tooltip.add(Component.translatable("item.backpack_arsenal.voltaic_element_core.tooltip2")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
