package backpackarsenal.client;

import backpackarsenal.BackpackArsenalMod;
import backpackarsenal.event.BackpackChargingHandler;
import backpackarsenal.init.ArsenalItems;
import backpackarsenal.network.BackpackArsenalNetwork;
import backpackarsenal.network.DrawFromBackpackPacket;
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
import the_four_primitives_and_weapons.client.WeaponWheelState;
import the_four_primitives_and_weapons.util.CuriosScabbardHelper;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * MAW Weapon Wheel UI に Sophisticated Backpack/ArsenalBackpack 内の voltaic_blade を
 * 視覚的に注入する。
 *
 * 動作:
 *   1) 毎 ClientTickEvent (NORMAL priority — MAW の tick より後) に走る
 *   2) wheel が見えている間、reflection で drawableWeapons 静的フィールドにアクセス
 *   3) 各バックパック内の voltaic_blade を DrawableWeaponInfo(INVENTORY) として list に追加
 *   4) wheel が閉じる遷移を検出し、選択 index が我々の注入分を指していたら
 *      DrawFromBackpackPacket をサーバへ送って実際に抜刀
 *
 * 注意: MAW 側の sendPacket() も並行で発火するが、location=INVENTORY + slotIndex が
 * 「バックパック本体の player inventory スロット」なので server 側で
 * drawFromInventory(player, slot) が呼ばれて「バックパック自体を武器として draw」
 * しようとし、対応武器型でないので silent no-op する想定。
 */
@Mod.EventBusSubscriber(
    modid = BackpackArsenalMod.MODID,
    value = Dist.CLIENT,
    bus = Mod.EventBusSubscriber.Bus.FORGE
)
public class VoltaicBladeWheelInjector {

    private static boolean wheelWasVisible = false;
    /** 前 tick で注入したエントリ。wheel 終了時の選択検出に使う。 */
    private static final IdentityHashMap<CuriosScabbardHelper.DrawableWeaponInfo, Boolean>
        injectedEntries = new IdentityHashMap<>();
    /** 前 tick の選択 index (wheel 閉じ後にリセットされる前に保存) */
    private static int lastSelectedIndex = -1;
    /** 前 tick の drawableWeapons リスト参照 (閉じ後の検出用) */
    private static List<CuriosScabbardHelper.DrawableWeaponInfo> lastList = null;

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        boolean visible = WeaponWheelState.isWheelVisible();
        if (visible) {
            injectEntries(mc.player);
            // 選択 index を毎 tick 控えておく (wheel 閉じる時には reset されてる)
            lastSelectedIndex = WeaponWheelState.getSelectedIndex();
            lastList = WeaponWheelState.getDrawableWeapons();
        } else if (wheelWasVisible) {
            // 直前の tick まで wheel が見えていて、今閉じた → 選択処理
            handleWheelClosed();
        }
        wheelWasVisible = visible;
    }

    /** drawableWeapons リストにバックパック内 voltaic_blade を追加する。重複は避ける。 */
    private static void injectEntries(Player player) {
        try {
            Field f = WeaponWheelState.class.getDeclaredField("drawableWeapons");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<CuriosScabbardHelper.DrawableWeaponInfo> list =
                (List<CuriosScabbardHelper.DrawableWeaponInfo>) f.get(null);
            if (list == null) return;

            // 既に注入済みエントリは保持、無くなった backpack の分は外す
            // 毎 tick 全注入を作り直すのが単純なので、まず injectedEntries の分を list から除去
            list.removeIf(injectedEntries::containsKey);
            injectedEntries.clear();

            Inventory inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack backpackStack = inv.getItem(i);
                if (!BackpackChargingHandler.isSophisticatedBackpack(backpackStack)) continue;

                IItemHandler handler = backpackStack
                    .getCapability(ForgeCapabilities.ITEM_HANDLER)
                    .orElse(null);
                if (handler == null) continue;
                for (int s = 0; s < handler.getSlots(); s++) {
                    ItemStack inner = handler.getStackInSlot(s);
                    if (inner.getItem() != ArsenalItems.VOLTAIC_BLADE.get()) continue;
                    CuriosScabbardHelper.DrawableWeaponInfo entry =
                        new CuriosScabbardHelper.DrawableWeaponInfo(
                            backpackStack,
                            inner,
                            CuriosScabbardHelper.ScabbardLocation.INVENTORY,
                            "",  // curio slot id — 未使用 (location が INVENTORY なので)
                            i    // player inventory slot index (backpack の所在)
                        );
                    injectedEntries.put(entry, Boolean.TRUE);
                    list.add(entry);
                }
            }
        } catch (Exception e) {
            // reflection 失敗時はサイレントに諦める (MAW 側の変更で field 名が変わった等)
        }
    }

    /** Wheel が閉じる直前の選択を見て、我々のエントリなら DrawFromBackpackPacket を送る。 */
    private static void handleWheelClosed() {
        try {
            if (lastList == null || lastSelectedIndex < 0 || lastSelectedIndex >= lastList.size()) {
                return;
            }
            CuriosScabbardHelper.DrawableWeaponInfo selected = lastList.get(lastSelectedIndex);
            if (injectedEntries.containsKey(selected)) {
                // 我々の注入したエントリが選択された
                BackpackArsenalNetwork.CHANNEL.sendToServer(new DrawFromBackpackPacket());
            }
        } catch (Exception e) {
            // ignore
        } finally {
            // 次の wheel 開きに備えて状態リセット
            injectedEntries.clear();
            lastSelectedIndex = -1;
            lastList = null;
        }
    }
}
