package backpackarsenal.event;

import backpackarsenal.BackpackArsenalMod;
import backpackarsenal.init.ArsenalItems;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * use() がなぜか呼ばれない問題の診断用。PlayerInteractEvent.RightClickItem は Forge が
 * Item.use() の直前に発火するので、ここでログを出して右クリックが届いているか確認する。
 *
 * このイベントが arsenal_backpack で発火するなら use() も発火するはず。
 * イベントが発火しないなら、上流 (MAW などの onRightClickEmpty / setCanceled) で
 * 右クリック自体が止まっている。
 *
 * 診断が終わったら削除して良い。
 */
@Mod.EventBusSubscriber(modid = BackpackArsenalMod.MODID)
public class RightClickDebugHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        boolean isArsenal = stack.getItem() == ArsenalItems.ARSENAL_BACKPACK.get();
        BackpackArsenalMod.LOGGER.info(
            "[backpack_arsenal] RightClickItem fired: side={}, hand={}, item={}, isArsenalBackpack={}, canceled={}",
            event.getLevel().isClientSide ? "CLIENT" : "SERVER",
            event.getHand(),
            stack.getItem(),
            isArsenal,
            event.isCanceled()
        );
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        ItemStack stack = event.getItemStack();
        boolean isArsenal = stack.getItem() == ArsenalItems.ARSENAL_BACKPACK.get();
        if (isArsenal) {
            BackpackArsenalMod.LOGGER.info(
                "[backpack_arsenal] RightClickEmpty fired: hand={}, isArsenalBackpack={}",
                event.getHand(), isArsenal);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClickItemLowest(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        boolean isArsenal = stack.getItem() == ArsenalItems.ARSENAL_BACKPACK.get();
        if (isArsenal) {
            BackpackArsenalMod.LOGGER.info(
                "[backpack_arsenal] RightClickItem LOWEST priority check: canceled={}, result={}",
                event.isCanceled(), event.getCancellationResult());
        }
    }
}
