package backpackarsenal.item;

import backpackarsenal.init.ArsenalBlocks;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackBlock;

import java.util.function.Supplier;

/** Soldier 11-style backpack. Gas behavior is added separately from item registration. */
public class FireBackpackItem extends ArsenalBackpackItem {
    public FireBackpackItem() {
        super((Supplier<BackpackBlock>) ArsenalBlocks.ARSENAL_BACKPACK_FIRE_BLOCK::get);
    }
}
