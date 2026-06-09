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
        boolean isArsenal = stack.getItem() == ArsenalItems.ARSENAL_BACKPACK_ELECTRON.get();
        BackpackArsenalMod.LOGGER.info(
            "[backpack_arsenal] RightClickItem fired: side={}, hand={}, item={}, isArsenalBackpack={}, canceled={}",
            event.getLevel().isClientSide ? "CLIENT" : "SERVER",
            event.getHand(),
            stack.getItem(),
            isArsenal,
            event.isCanceled()
        );

        // 「upgrade slot に入れられない」 症状用: voltaic_charger_upgrade を手に持って
        // 右クリックした時、 その瞬間の item の class identity / IUpgradeItem instance check
        // を出す。 ctor 時の instanceof と一致しないなら classloader / 別 instance 疑いが確定。
        if (stack.getItem() == ArsenalItems.VOLTAIC_CHARGER_UPGRADE.get()) {
            var item = stack.getItem();
            var upgradeIfaceClass = net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeItem.class;
            boolean isIUpgrade = item instanceof net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeItem;
            BackpackArsenalMod.LOGGER.info(
                "[backpack_arsenal] [VOLTAIC_CHARGER_DIAG] runtime-check: " +
                "item.class={}, item.classloader={}, IUpgradeItem.class={}, " +
                "IUpgradeItem.classloader={}, instanceof={}, " +
                "implementedInterfaces=[{}]",
                item.getClass().getName(),
                System.identityHashCode(item.getClass().getClassLoader()),
                upgradeIfaceClass.getName(),
                System.identityHashCode(upgradeIfaceClass.getClassLoader()),
                isIUpgrade,
                String.join(",", listInterfaces(item.getClass()))
            );
        }
    }

    /** クラスとその superclass チェーンの全 interface 名を集める (重複あり)。
     *  IUpgradeItem が見えてるかを目視確認するため。 */
    private static java.util.List<String> listInterfaces(Class<?> cls) {
        var out = new java.util.ArrayList<String>();
        Class<?> c = cls;
        while (c != null && c != Object.class) {
            for (Class<?> i : c.getInterfaces()) {
                out.add(c.getSimpleName() + "→" + i.getName());
            }
            c = c.getSuperclass();
        }
        return out;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        ItemStack stack = event.getItemStack();
        boolean isArsenal = stack.getItem() == ArsenalItems.ARSENAL_BACKPACK_ELECTRON.get();
        if (isArsenal) {
            BackpackArsenalMod.LOGGER.info(
                "[backpack_arsenal] RightClickEmpty fired: hand={}, isArsenalBackpack={}",
                event.getHand(), isArsenal);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClickItemLowest(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        boolean isArsenal = stack.getItem() == ArsenalItems.ARSENAL_BACKPACK_ELECTRON.get();
        if (isArsenal) {
            BackpackArsenalMod.LOGGER.info(
                "[backpack_arsenal] RightClickItem LOWEST priority check: canceled={}, result={}",
                event.isCanceled(), event.getCancellationResult());
        }
    }
}
