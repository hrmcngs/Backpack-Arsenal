package backpackarsenal.network;

import backpackarsenal.init.ArsenalItems;
import backpackarsenal.inventory.ChargeSlotInventory;
import backpackarsenal.util.BackpackScanner;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Client → Server: R 単押し時、メインハンドの voltaic_blade を
 * ArsenalBackpack (inventory or curios "back") に納める。
 *
 * arsenal_backpack 限定 (バニラ SB バックパックには納刀しない)。
 *
 * 優先順:
 *   (1) ArsenalBackpack の専用充電スロット (空なら最優先 / ここに入った blade だけが充電)
 *   (2) 通常のインベントリスロット (insertItem 経由、複数本可)
 *
 * 個数制限なし: 通常スロットには複数本の voltaic_blade を格納可能 (voltaic_blade 自体の
 * maxStackSize=1 なので 1スロット1本)。専用スロットには 1本のみ。
 */
public class SheathToBackpackPacket {

    public SheathToBackpackPacket() {}

    public static SheathToBackpackPacket decode(FriendlyByteBuf buf) {
        return new SheathToBackpackPacket();
    }

    public void encode(FriendlyByteBuf buf) {
        // no payload
    }

    public static void handle(SheathToBackpackPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            sheathIntoFirstArsenalBackpack(player);
        });
        ctx.setPacketHandled(true);
    }

    private static void sheathIntoFirstArsenalBackpack(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() != ArsenalItems.VOLTAIC_BLADE.get()) return;

        // 走査対象 ItemStack を集める (inv + curios back, ArsenalBackpack 限定)
        List<ItemStack> targets = new ArrayList<>();
        BackpackScanner.forEachArsenalBackpack(player, targets::add);

        for (ItemStack backpackStack : targets) {
            // (1) 専用充電スロット優先 (空ならここ)
            ChargeSlotInventory charge = new ChargeSlotInventory(backpackStack);
            if (charge.getStackInSlot(0).isEmpty()) {
                charge.setStackInSlot(0, mainHand.copy());
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                sheathSound(player);
                return;
            }

            // (2) 通常スロットへ insertItem (個数制限なし)
            IItemHandler handler = backpackStack
                .getCapability(ForgeCapabilities.ITEM_HANDLER)
                .orElse(null);
            if (handler == null) continue;

            ItemStack leftover = mainHand.copy();
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                leftover = handler.insertItem(slot, leftover, false);
                if (leftover.isEmpty()) break;
            }
            if (leftover.isEmpty()) {
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                sheathSound(player);
                return;
            }
            // この backpack に空きが無かった → 次の backpack を試す
        }
        // どのバックパックにも入らなかった → 何もしない (手中維持)
    }

    private static void sheathSound(ServerPlayer player) {
        player.level().playSound(null,
            player.getX(), player.getY(), player.getZ(),
            SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.5f, 0.8f);
    }
}
