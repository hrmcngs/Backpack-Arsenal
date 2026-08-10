package backpackarsenal.util;

import backpackarsenal.init.ArsenalItems;
import net.minecraft.world.item.ItemStack;

/** Keeps each elemental backpack paired with its own weapon. */
public final class ArsenalWeaponPair {
    private ArsenalWeaponPair() {}

    public static boolean isSupportedWeapon(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() == ArsenalItems.VOLTAIC_BLADE.get()
            || stack.getItem() == ArsenalItems.FIRE_BLADE.get());
    }

    public static boolean matches(ItemStack backpack, ItemStack weapon) {
        if (backpack.isEmpty() || weapon.isEmpty()) return false;
        if (backpack.getItem() == ArsenalItems.ARSENAL_BACKPACK_ELECTRON.get()) {
            return weapon.getItem() == ArsenalItems.VOLTAIC_BLADE.get();
        }
        if (backpack.getItem() == ArsenalItems.ARSENAL_BACKPACK_FIRE.get()) {
            return weapon.getItem() == ArsenalItems.FIRE_BLADE.get();
        }
        return false;
    }
}
