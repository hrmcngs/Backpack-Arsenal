package backpackarsenal.network;

import backpackarsenal.init.ArsenalItems;
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
 * ArsenalBackpack (inventory or curios "back") の通常スロットに納める。
 *
 * arsenal_backpack 限定 (バニラ SB バックパックには納刀しない)。
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

        List<ItemStack> targets = new ArrayList<>();
        BackpackScanner.forEachArsenalBackpack(player, targets::add);

        for (ItemStack backpackStack : targets) {
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
        }
    }

    private static void sheathSound(ServerPlayer player) {
        player.level().playSound(null,
            player.getX(), player.getY(), player.getZ(),
            SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.5f, 0.8f);
    }
}
