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
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Client → Server: ArsenalBackpack (inv or curios "back") の通常スロットから
 * voltaic_blade を 1本取り出してメインハンドへ swap。
 *
 * arsenal_backpack 限定 (バニラ SB バックパックは対象外)。
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
            drawFromAnyArsenalBackpack(player);
        });
        ctx.setPacketHandled(true);
    }

    private static void drawFromAnyArsenalBackpack(ServerPlayer player) {
        if (player.getMainHandItem().getItem() == ArsenalItems.VOLTAIC_BLADE.get()) return;

        List<ItemStack> backpacks = new ArrayList<>();
        BackpackScanner.forEachArsenalBackpack(player, backpacks::add);

        ItemStack mainHandLive = player.getMainHandItem(); // identity 用 ( live ref )

        for (ItemStack backpackStack : backpacks) {
            IItemHandler handler = backpackStack
                .getCapability(ForgeCapabilities.ITEM_HANDLER)
                .orElse(null);
            if (!(handler instanceof IItemHandlerModifiable mod)) continue;

            for (int slot = 0; slot < mod.getSlots(); slot++) {
                ItemStack inner = mod.getStackInSlot(slot);
                if (inner.getItem() != ArsenalItems.VOLTAIC_BLADE.get()) continue;

                ItemStack drawn = inner.copy();

                // ★ メインハンドが抜刀元 backpack 自身の場合の特殊処理。
                //   通常の swap だと backpack を自分自身の slot に格納 → 外側参照消滅で消滅バグ。
                //   逃がし先の優先順位 ( MAW 流: silent refuse 方針、 強制 drop はしない ):
                //     1) offhand が空 → そこへ
                //     2) inventory に余裕 ( player.getInventory().add ) → 追加
                //     3) どちらも不可 → silent return ( 抜刀キャンセル )
                if (mainHandLive == backpackStack) {
                    ItemStack backpackCopy = backpackStack.copy();
                    boolean stored;
                    if (player.getOffhandItem().isEmpty()) {
                        player.setItemInHand(InteractionHand.OFF_HAND, backpackCopy);
                        stored = true;
                    } else {
                        stored = player.getInventory().add(backpackCopy);
                    }
                    if (!stored) {
                        // 空き無し → MAW と同じく silent return ( drop しない、 backpack を失わない )
                        return;
                    }
                    mod.setStackInSlot(slot, ItemStack.EMPTY); // 内側 slot を空にして blade 取り出し
                    player.setItemInHand(InteractionHand.MAIN_HAND, drawn);
                } else {
                    // 通常 swap: メインハンド ↔ blade slot 交換。
                    ItemStack mainHandCopy = mainHandLive.copy();
                    player.setItemInHand(InteractionHand.MAIN_HAND, drawn);
                    mod.setStackInSlot(slot, mainHandCopy);
                }
                drawSound(player);
                return;
            }
        }
    }

    private static void drawSound(ServerPlayer player) {
        player.level().playSound(null,
            player.getX(), player.getY(), player.getZ(),
            SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.5f, 1.4f);
    }
}
