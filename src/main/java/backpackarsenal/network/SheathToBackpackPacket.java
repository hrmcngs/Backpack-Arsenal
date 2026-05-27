package backpackarsenal.network;

import backpackarsenal.event.BackpackChargingHandler;
import backpackarsenal.init.ArsenalItems;
import backpackarsenal.inventory.ChargeSlotInventory;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → Server: R 単押し時、メインハンドの voltaic_blade を
 * バックパック (SB or ArsenalBackpack) に納める。
 *
 * 優先順:
 *   (1) ArsenalBackpack の専用充電スロット (空なら最優先)
 *   (2) 通常のインベントリスロット (insertItem 経由、SB の validate を尊重)
 *
 * 1個制限: backpack 内 (通常スロット+専用スロット 双方) に既に voltaic_blade が
 * あれば追加挿入は行わず、他のバックパックを試す。それでも入らなければ手中に残す。
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
            sheathIntoFirstBackpack(player);
        });
        ctx.setPacketHandled(true);
    }

    private static void sheathIntoFirstBackpack(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() != ArsenalItems.VOLTAIC_BLADE.get()) return;

        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack backpackStack = inv.getItem(i);
            if (!BackpackChargingHandler.isSophisticatedBackpack(backpackStack)) continue;

            // (0) backpack 内 (通常 + 専用) に既に voltaic_blade があるならスキップ (1個制限)
            if (backpackAlreadyHasVoltaic(backpackStack)) continue;

            // (1) ArsenalBackpack: 専用充電スロット優先
            if (backpackStack.getItem() == ArsenalItems.ARSENAL_BACKPACK.get()) {
                ChargeSlotInventory charge = new ChargeSlotInventory(backpackStack);
                if (charge.getStackInSlot(0).isEmpty()) {
                    charge.setStackInSlot(0, mainHand.copy());
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    sheathSound(player);
                    return;
                }
            }

            // (2) 通常スロットへ insertItem 経由
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
                // 全部入った
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                sheathSound(player);
                return;
            }
            // 入りきらなかった分は手中に戻す (1本だけのはずだから全か無か)
            // 念のため leftover を手中に書き戻して item loss を防ぐ
            player.setItemInHand(InteractionHand.MAIN_HAND, leftover);
            return;
        }
        // どこにも入らなかった → 何もしない (手中の blade は維持される)
    }

    /** backpack のあらゆる場所に voltaic_blade が既にあるか判定 (1個制限のため) */
    private static boolean backpackAlreadyHasVoltaic(ItemStack backpackStack) {
        // 専用スロット
        if (backpackStack.getItem() == ArsenalItems.ARSENAL_BACKPACK.get()) {
            ChargeSlotInventory charge = new ChargeSlotInventory(backpackStack);
            if (!charge.getStackInSlot(0).isEmpty()) return true;
        }
        // 通常スロット
        IItemHandler handler = backpackStack
            .getCapability(ForgeCapabilities.ITEM_HANDLER)
            .orElse(null);
        if (handler == null) return false;
        for (int s = 0; s < handler.getSlots(); s++) {
            if (handler.getStackInSlot(s).getItem() == ArsenalItems.VOLTAIC_BLADE.get()) return true;
        }
        return false;
    }

    private static void sheathSound(ServerPlayer player) {
        player.level().playSound(null,
            player.getX(), player.getY(), player.getZ(),
            SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.5f, 0.8f);
    }
}
