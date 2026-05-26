package backpackarsenal.network;

import backpackarsenal.event.BackpackChargingHandler;
import backpackarsenal.init.ArsenalItems;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → Server パケット。
 *
 * クライアントで R 単押し検出 + voltaic_blade がバックパック内にあると確認したときに送信。
 * サーバ側でプレイヤーの全バックパックを走査し、最初に見つけた voltaic_blade を
 * メインハンドへ swap する (元のメインハンドはその空いたスロットに入れる)。
 *
 * ペイロードなし — サーバが状態を再判定するので、クライアントが嘘をついても安全。
 */
public class DrawFromBackpackPacket {

    public DrawFromBackpackPacket() {}

    public static DrawFromBackpackPacket decode(FriendlyByteBuf buf) {
        return new DrawFromBackpackPacket();
    }

    public void encode(FriendlyByteBuf buf) {
        // no payload
    }

    public static void handle(DrawFromBackpackPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            drawFromAnyBackpack(player);
        });
        ctx.setPacketHandled(true);
    }

    /** プレイヤーの最初のバックパックから voltaic_blade を抜いてメインハンドと swap。 */
    private static void drawFromAnyBackpack(ServerPlayer player) {
        // 既にメインハンドに voltaic_blade を持っているなら何もしない (二度引きを防ぐ)
        if (player.getMainHandItem().getItem() == ArsenalItems.VOLTAIC_BLADE.get()) return;

        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack backpackStack = inv.getItem(i);
            if (!BackpackChargingHandler.isSophisticatedBackpack(backpackStack)) continue;

            IItemHandler handler = backpackStack
                .getCapability(ForgeCapabilities.ITEM_HANDLER)
                .orElse(null);
            if (!(handler instanceof IItemHandlerModifiable mod)) continue;

            for (int slot = 0; slot < mod.getSlots(); slot++) {
                ItemStack inner = mod.getStackInSlot(slot);
                if (inner.isEmpty()) continue;
                if (inner.getItem() != ArsenalItems.VOLTAIC_BLADE.get()) continue;

                // 見つけた → swap
                ItemStack mainHand = player.getMainHandItem().copy();
                ItemStack drawn = inner.copy();

                player.setItemInHand(InteractionHand.MAIN_HAND, drawn);
                mod.setStackInSlot(slot, mainHand);

                player.level().playSound(null,
                    player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.5f, 1.4f);
                return;
            }
        }
        // 見つからなかった → サイレントに何もしない
    }
}
