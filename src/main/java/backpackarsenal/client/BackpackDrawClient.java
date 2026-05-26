package backpackarsenal.client;

import backpackarsenal.BackpackArsenalMod;
import backpackarsenal.event.BackpackChargingHandler;
import backpackarsenal.init.ArsenalItems;
import backpackarsenal.network.BackpackArsenalNetwork;
import backpackarsenal.network.DrawFromBackpackPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import the_four_primitives_and_weapons.init.TheFourPrimitivesAndWeaponsModKeyMappings;

/**
 * MAW の R キー (単押し抜刀) を本体より先にフックして、
 * Sophisticated Backpack 内の voltaic_blade があれば優先で引き抜く。
 *
 * 優先順位制御の仕組み:
 *   - {@code EventPriority.HIGHEST} で {@link TickEvent.ClientTickEvent} を購読
 *   - MAW の listener より前に走り、条件が揃ったときだけ {@code R.consumeClick()} を呼んで
 *     MAW のキー消費を奪う
 *   - 条件が揃わない場合は R を触らず、MAW の通常抜刀 (Curios 鞘) にそのまま流す
 *
 * 条件:
 *   1. 画面 (Screen) が開いていない
 *   2. player が null でない
 *   3. メインハンドに既に voltaic_blade を持っていない
 *   4. プレイヤーインベントリ内のいずれかの Sophisticated Backpack 内に voltaic_blade がある
 */
@Mod.EventBusSubscriber(
    modid = BackpackArsenalMod.MODID,
    value = Dist.CLIENT,
    bus = Mod.EventBusSubscriber.Bus.FORGE
)
public class BackpackDrawClient {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;

        Player player = mc.player;
        if (player == null) return;

        // 既にメインハンドに持っているなら R は通常通り MAW に渡す
        if (player.getMainHandItem().getItem() == ArsenalItems.VOLTAIC_BLADE.get()) return;

        // バックパック内に voltaic_blade が無いなら R は通常通り MAW に渡す
        if (!hasVoltaicBladeInAnyBackpack(player)) return;

        // 条件成立 → R を奪う
        KeyMapping rKey = TheFourPrimitivesAndWeaponsModKeyMappings.R;
        if (rKey == null) return;

        // consumeClick() は 1 回押下イベントを取り出して true を返す。
        // ここで true → 今 tick で R が押された → サーバへ抜刀リクエスト
        // ここで false → 押されていない → 何もしない (MAW も拾えないが、そもそも押下イベントが無いので問題なし)
        if (!rKey.consumeClick()) return;

        BackpackArsenalNetwork.CHANNEL.sendToServer(new DrawFromBackpackPacket());
    }

    private static boolean hasVoltaicBladeInAnyBackpack(Player player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!BackpackChargingHandler.isSophisticatedBackpack(stack)) continue;

            IItemHandler handler = stack
                .getCapability(ForgeCapabilities.ITEM_HANDLER)
                .orElse(null);
            if (handler == null) continue;

            for (int s = 0; s < handler.getSlots(); s++) {
                ItemStack inner = handler.getStackInSlot(s);
                if (inner.getItem() == ArsenalItems.VOLTAIC_BLADE.get()) return true;
            }
        }
        return false;
    }
}
