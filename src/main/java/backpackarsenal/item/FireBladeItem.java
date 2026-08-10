package backpackarsenal.item;

/** Soldier 11-style blade using the same charge/NBT/bar system as Voltaic Blade. */
public class FireBladeItem extends VoltaicBladeItem {
    public FireBladeItem() {
        super();
    }

    @Override
    public void inventoryTick(net.minecraft.world.item.ItemStack stack,
                              net.minecraft.world.level.Level level,
                              net.minecraft.world.entity.Entity entity,
                              int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (!level.isClientSide && getCharge(stack) > 0) {
            var tag = stack.getOrCreateTag();
            if (!MODE_FIRE.equals(tag.getString(TAG_ELEMENT_TYPE))) {
                setCharge(stack, getCharge(stack));
            }
        }
    }
}
