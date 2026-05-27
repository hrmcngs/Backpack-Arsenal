package backpackarsenal.inventory;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

/**
 * SB の IItemHandler をラップし、getSlotLimit を指定値で上限キャップする。
 *
 * insertItem は超過分を leftover として返す(自前で容量計算)。
 * setStackInSlot は SB の内部書き換え用に delegate そのまま (バイパス可)。
 * これにより GUI クリックや hopper 自動投入では制限が効くが、SB 内部の
 * 直接書き換え (setStackInSlot 経由のチャージ NBT 更新等) は通る。
 */
public class StackLimitedHandler implements IItemHandlerModifiable {

    private final IItemHandlerModifiable delegate;
    private final int limit;

    public StackLimitedHandler(IItemHandlerModifiable delegate, int limit) {
        this.delegate = delegate;
        this.limit = limit;
    }

    @Override
    public int getSlotLimit(int slot) {
        return Math.min(limit, delegate.getSlotLimit(slot));
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack existing = delegate.getStackInSlot(slot);
        int slotMax = getSlotLimit(slot);
        int itemMax = Math.min(slotMax, stack.getMaxStackSize());

        // 既存と同じアイテムでスタック可能か
        boolean canMerge = existing.isEmpty()
            || (ItemStack.isSameItemSameTags(existing, stack));
        if (!canMerge) {
            // 違うアイテム同士は SB の validate に任せる (空ならスタック)
            return delegate.insertItem(slot, stack, simulate);
        }

        int spaceLeft = itemMax - existing.getCount();
        if (spaceLeft <= 0) return stack; // 満杯
        if (stack.getCount() > spaceLeft) {
            ItemStack toInsert = stack.copy();
            toInsert.setCount(spaceLeft);
            ItemStack rejected = delegate.insertItem(slot, toInsert, simulate);
            // rejected 分 + 元々 spaceLeft を超えた分を leftover として返す
            ItemStack leftover = stack.copy();
            leftover.setCount(stack.getCount() - spaceLeft + rejected.getCount());
            return leftover;
        }
        return delegate.insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return delegate.extractItem(slot, amount, simulate);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        delegate.setStackInSlot(slot, stack);
    }

    @Override
    public int getSlots() {
        return delegate.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return delegate.getStackInSlot(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return delegate.isItemValid(slot, stack);
    }
}
