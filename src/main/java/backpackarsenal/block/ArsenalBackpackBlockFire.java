package backpackarsenal.block;

import backpackarsenal.init.ArsenalItems;
import net.minecraft.world.item.Item;

/** Placed-block counterpart of the Fire backpack. */
public class ArsenalBackpackBlockFire extends ArsenalBackpackBlockElectron {
    @Override
    public Item asItem() {
        return ArsenalItems.ARSENAL_BACKPACK_FIRE.get();
    }
}
