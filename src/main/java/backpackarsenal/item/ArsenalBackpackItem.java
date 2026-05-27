package backpackarsenal.item;

import backpackarsenal.init.ArsenalItems;
import backpackarsenal.inventory.ArsenalBackpackContainer;
import backpackarsenal.inventory.ChargeSlotInventory;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.network.NetworkHooks;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackBlock;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem;
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContext;
import net.p3pp3rf1y.sophisticatedbackpacks.init.ModBlocks;

import java.util.List;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Arsenal Backpack — Sophisticated Backpacks の BackpackItem を継承した独自グレード。
 *
 * 設計コンセプト: スタート時は最小限の容量。
 * Sophisticated Backpacks 標準の Upgrade Card スロット (5枠) を埋めて拡張する想定:
 *   - Stack Upgrade (basic/advanced/...) → 1スロットの最大スタック数を倍々で増やす
 *   - Pickup Upgrade → 自動拾い
 *   - Magnet / Feeding / Compacting 等
 *   - Refill Upgrade → ホットバー自動補充
 *
 * 充電ロジック: BackpackChargingHandler が ArsenalBackpack も対象に含めている。
 * voltaic_blade を中に入れると充電される。
 *
 * インベントリ: 9 スロット (最低限)。アップグレード: 5 スロット (Stack 系含めて柔軟に組める量)。
 * ブロック供給: SB 標準の革製 backpack ブロックを共用 (設置時の見た目はノーマルな revolt 革)。
 */
public class ArsenalBackpackItem extends BackpackItem {

    /** インベントリスロット数 — 最低限の 9 スロット (チェスト1段分のさらに半分以下)。
     *  これより小さくしたい/大きくしたい場合は数字を変える。 */
    public static final int INVENTORY_SLOTS = 9;
    /** アップグレードスロット数 — SB の Stack/Pickup/Magnet 等を 5 枚まで挿せる。 */
    public static final int UPGRADE_SLOTS   = 5;

    /** 通常スロット 1 マスあたりの最大スタック数 (voltaic_blade は別。stacksTo(1) の Item 自体の上限が効く) */
    public static final int PER_SLOT_STACK_LIMIT = 9;

    public ArsenalBackpackItem() {
        super(
            () -> INVENTORY_SLOTS,
            () -> UPGRADE_SLOTS,
            (Supplier<BackpackBlock>) ModBlocks.BACKPACK::get,
            (UnaryOperator<Item.Properties>) props -> props.rarity(Rarity.UNCOMMON).stacksTo(1)
        );
    }

    /**
     * SB の capability provider を wrap し、ITEM_HANDLER を返す際に
     * {@link backpackarsenal.inventory.StackLimitedHandler} で getSlotLimit を
     * {@link #PER_SLOT_STACK_LIMIT} (9) に上限制限する。
     *
     * voltaic_blade は Item.maxStackSize=1 なので影響なし。他アイテム (土・石ブロック等) は
     * 1スロット 9個までになる。
     */
    @Override
    public net.minecraftforge.common.capabilities.ICapabilityProvider initCapabilities(
            ItemStack stack, net.minecraft.nbt.CompoundTag tag) {
        final net.minecraftforge.common.capabilities.ICapabilityProvider sbProvider =
            super.initCapabilities(stack, tag);
        if (sbProvider == null) return null;
        return new net.minecraftforge.common.capabilities.ICapabilityProvider() {
            @Override
            @org.jetbrains.annotations.NotNull
            public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
                    @org.jetbrains.annotations.NotNull
                    net.minecraftforge.common.capabilities.Capability<T> cap,
                    @org.jetbrains.annotations.Nullable
                    net.minecraft.core.Direction side) {
                net.minecraftforge.common.util.LazyOptional<T> sbCap = sbProvider.getCapability(cap, side);
                if (cap == net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER) {
                    return sbCap.lazyMap(h -> {
                        if (h instanceof net.minecraftforge.items.IItemHandlerModifiable mod) {
                            return new backpackarsenal.inventory.StackLimitedHandler(mod, PER_SLOT_STACK_LIMIT);
                        }
                        return h;
                    }).cast();
                }
                return sbCap;
            }
        };
    }

    /**
     * インベントリでホバー時に納刀中の voltaic_blade を表示する (通常鞘相当)。
     * - 充電スロット (専用) の中身
     * - 通常スロット内の voltaic_blade 本数
     */
    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        // (1) 充電スロット
        ItemStack stored = readDedicatedSlot(stack);
        if (!stored.isEmpty() && stored.getItem() == ArsenalItems.VOLTAIC_BLADE.get()) {
            int charge = VoltaicBladeItem.getCharge(stored);
            int max = VoltaicBladeItem.getMaxCharge(stored);
            tooltip.add(Component.translatable(
                    "item.backpack_arsenal.arsenal_backpack.stored",
                    stored.getHoverName(), charge, max
            ).withStyle(ChatFormatting.AQUA));
        }

        // (2) 通常スロット内の voltaic_blade 本数
        int countInRegular = countVoltaicInRegularSlots(stack);
        if (countInRegular > 0) {
            tooltip.add(Component.translatable(
                    "item.backpack_arsenal.arsenal_backpack.stored_regular",
                    countInRegular
            ).withStyle(ChatFormatting.GRAY));
        }
    }

    /** NBT 直読みで充電スロットの ItemStack を取得する (ChargeSlotInventory の副作用を避ける用)。 */
    private static ItemStack readDedicatedSlot(ItemStack backpack) {
        CompoundTag tag = backpack.getTag();
        if (tag == null || !tag.contains(ChargeSlotInventory.NBT_KEY, 10)) return ItemStack.EMPTY;
        CompoundTag slotTag = tag.getCompound(ChargeSlotInventory.NBT_KEY);
        // ItemStackHandler.serializeNBT() は "Items" リストに各スロットを格納
        if (!slotTag.contains("Items", 9)) return ItemStack.EMPTY;
        var items = slotTag.getList("Items", 10);
        if (items.isEmpty()) return ItemStack.EMPTY;
        return ItemStack.of(items.getCompound(0));
    }

    /** バックパック内の通常スロットに入っている voltaic_blade の本数を数える。 */
    private static int countVoltaicInRegularSlots(ItemStack backpack) {
        IItemHandler handler = backpack.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
        if (handler == null) return 0;
        int count = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            if (handler.getStackInSlot(i).getItem() == ArsenalItems.VOLTAIC_BLADE.get()) {
                count++;
            }
        }
        return count;
    }

    /**
     * SB の use() を再現しつつ、開く Menu を ArsenalBackpackContainer に差し替える。
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            String handlerName = (hand == InteractionHand.MAIN_HAND) ? "main" : "offhand";
            int slotIndex = (hand == InteractionHand.MAIN_HAND)
                ? player.getInventory().selected
                : 0;
            BackpackContext.Item ctx = new BackpackContext.Item(handlerName, slotIndex);

            SimpleMenuProvider provider = new SimpleMenuProvider(
                (containerId, inv, p) -> new ArsenalBackpackContainer(containerId, p, ctx),
                stack.getHoverName()
            );
            NetworkHooks.openScreen(serverPlayer, provider, ctx::addToBuffer);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
