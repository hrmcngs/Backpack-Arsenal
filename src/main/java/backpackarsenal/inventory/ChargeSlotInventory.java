package backpackarsenal.inventory;

import backpackarsenal.init.ArsenalItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

/**
 * Arsenal Backpack の専用充電スロット (1スロット) を ItemStack NBT に永続化する handler。
 *
 * - voltaic_blade のみ受理 (isItemValid)
 * - 中身は backpack の ItemStack NBT のサブキー "ArsenalChargeSlot" に保存
 * - onContentsChanged で自動的に書き戻す
 *
 * Container を開き直しても、ワールド保存/読込しても永続される。
 */
public class ChargeSlotInventory extends ItemStackHandler {

    public static final String NBT_KEY = "ArsenalChargeSlot";

    /** このスロットを保持しているバックパック本体 ItemStack (NBT 書き込み先) */
    private final ItemStack owner;

    public ChargeSlotInventory(ItemStack owner) {
        super(1);
        this.owner = owner;
        CompoundTag tag = owner.getTag();
        if (tag != null && tag.contains(NBT_KEY, 10 /* CompoundTag */)) {
            deserializeNBT(tag.getCompound(NBT_KEY));
        }
    }

    @Override
    protected void onContentsChanged(int slot) {
        super.onContentsChanged(slot);
        owner.getOrCreateTag().put(NBT_KEY, serializeNBT());
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (stack.isEmpty()) return true;
        return stack.getItem() == ArsenalItems.VOLTAIC_BLADE.get();
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }
}
