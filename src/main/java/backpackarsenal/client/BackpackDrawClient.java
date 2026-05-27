package backpackarsenal.client;

import backpackarsenal.BackpackArsenalMod;
import backpackarsenal.event.BackpackChargingHandler;
import backpackarsenal.init.ArsenalItems;
import backpackarsenal.network.BackpackArsenalNetwork;
import backpackarsenal.network.DrawFromBackpackPacket;
import backpackarsenal.network.SheathToBackpackPacket;
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
 * MAW の R キー (単押し抜刀/納刀) を本体より先にフックし、Sophisticated Backpack /
 * ArsenalBackpack を抜刀/納刀先として優先で扱う。
 *
 * 優先順位制御:
 *   - {@code EventPriority.HIGHEST} で {@link TickEvent.ClientTickEvent} を購読
 *   - MAW listener より前に走り、条件成立時のみ {@code R.consumeClick()} を呼んで
 *     MAW のキー消費を奪う
 *   - 条件不成立なら R は触らず MAW の通常抜刀 (Curios 鞘) に流す
 *
 * 2 モード:
 *   (A) 抜刀 (DRAW)  : メインハンドに voltaic_blade が無い + バックパックに voltaic_blade
 *                       が居る → バックパックの最初の 1 本をメインハンドへ swap
 *   (B) 納刀 (SHEATH): メインハンドに voltaic_blade が居る + バックパックに空きがある
 *                       → メインハンドの voltaic_blade をバックパックの最初の空きスロットへ
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

        boolean handHasBlade = player.getMainHandItem().getItem() == ArsenalItems.VOLTAIC_BLADE.get();

        Mode mode;
        if (handHasBlade && hasEmptyBackpackSlot(player)) {
            mode = Mode.SHEATH;
        } else if (!handHasBlade && hasVoltaicBladeInAnyBackpack(player)) {
            mode = Mode.DRAW;
        } else {
            return; // 該当条件無し → R を MAW にそのまま渡す
        }

        KeyMapping rKey = TheFourPrimitivesAndWeaponsModKeyMappings.R;
        if (rKey == null) return;

        // consumeClick(): 1 回押下イベントを取り出して true を返す。
        // 押下イベントがなければ false (= 今 tick で R は押されていない) → 何もしない。
        if (!rKey.consumeClick()) return;

        switch (mode) {
            case DRAW   -> BackpackArsenalNetwork.CHANNEL.sendToServer(new DrawFromBackpackPacket());
            case SHEATH -> BackpackArsenalNetwork.CHANNEL.sendToServer(new SheathToBackpackPacket());
        }
    }

    private enum Mode { DRAW, SHEATH }

    private static boolean hasVoltaicBladeInAnyBackpack(Player player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!BackpackChargingHandler.isSophisticatedBackpack(stack)) continue;
            IItemHandler handler = stack.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
            if (handler == null) continue;
            for (int s = 0; s < handler.getSlots(); s++) {
                if (handler.getStackInSlot(s).getItem() == ArsenalItems.VOLTAIC_BLADE.get()) return true;
            }
        }
        return false;
    }

    private static boolean hasEmptyBackpackSlot(Player player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!BackpackChargingHandler.isSophisticatedBackpack(stack)) continue;
            IItemHandler handler = stack.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
            if (handler == null) continue;
            for (int s = 0; s < handler.getSlots(); s++) {
                if (handler.getStackInSlot(s).isEmpty()) return true;
            }
        }
        return false;
    }
}
