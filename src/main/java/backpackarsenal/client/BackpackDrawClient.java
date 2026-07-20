package backpackarsenal.client;

import backpackarsenal.BackpackArsenalMod;
import backpackarsenal.init.ArsenalItems;
import backpackarsenal.item.ArsenalBackpackItem;
import backpackarsenal.network.BackpackArsenalNetwork;
import backpackarsenal.network.DrawFromBackpackPacket;
import backpackarsenal.network.SheathToBackpackPacket;
import backpackarsenal.util.BackpackScanner;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import org.lwjgl.glfw.GLFW;
import the_four_primitives_and_weapons.client.WeaponWheelState;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MAW R キーをフックし、ArsenalBackpack を抜刀/納刀先として優先扱うクライアントハンドラ。
 *
 * 走査対象: プレイヤー inventory + Curios "back" スロット の ArsenalBackpack 限定
 * (バニラ SB バックパックは対象外)。
 *
 * 2 モード:
 *   (A) 抜刀 (DRAW)  : メインハンドが空 + ArsenalBackpack のどこかに voltaic_blade あり
 *                       → DrawFromBackpackPacket
 *   (B) 納刀 (SHEATH): メインハンドに voltaic_blade あり + ArsenalBackpack に空きあり
 *                       → SheathToBackpackPacket
 *
 * <h2>長押しと短押しの分岐 (InputEvent.Key release で判定)</h2>
 *
 * MAW の R KeyMapping は setDown() で即時に WeaponWheelState.rKeyDown=true を立て、
 * release 時に RMessage(0,0) を送って Curios の saya (belt/back) に勝手に納刀してしまう。
 * 一方で 0.5秒以上押し続けると MAW の wheel が開く設計。両立させるため、release を
 * Forge {@link InputEvent.Key} で先取りして以下のように分岐:
 *
 * <ul>
 *   <li><b>短押し</b> (経過 &lt; 500ms): wheel は出ていない。MAW の rKeyDown を false にして
 *       onRKeyReleased() の RMessage(0,0) を抑止し、我々が DRAW/SHEATH packet を直接送る。</li>
 *   <li><b>長押し + 選択あり</b>: wheel が出ていて selectedIndex≥0。MAW の選択結果に任せる
 *       (MAW packet は自前の輪っかから来た saya エントリを処理し、{@link VoltaicBladeWheelInjector}
 *       が注入した backpack エントリは閉じ tick で別パケットを送る)。</li>
 *   <li><b>長押し + デッドゾーン</b>: wheel が出ていて selectedIndex&lt;0。RMessage(0,0) を
 *       抑止するため rKeyDown=false にする (saya への自動納刀を回避)。</li>
 * </ul>
 *
 * InputEvent.Key は KeyMapping.setDown() より前に発火するため、release 直後の MAW の
 * onRKeyReleased() を rKeyDown=false で no-op 化できる。
 */
@Mod.EventBusSubscriber(
    modid = BackpackArsenalMod.MODID,
    value = Dist.CLIENT,
    bus = Mod.EventBusSubscriber.Bus.FORGE
)
public class BackpackDrawClient {

    /** MAW の WheelState が長押しと判定するしきい値と同じ値 (500ms)。 */
    private static final long LONG_PRESS_THRESHOLD_MS = 500L;

    /** R が押された時刻 (millis)。release で経過時間判定に使う。 */
    private static long pressStartTime = 0L;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKey(InputEvent.Key event) {
        if (event.getKey() != GLFW.GLFW_KEY_R) return;
        // R は MAW の KeyMapping が握っているので、ここでは press/release の時刻と
        // release 時の MAW 抑止だけを担当する。consumeClick() は触らない。
        if (event.getAction() == GLFW.GLFW_PRESS) {
            pressStartTime = System.currentTimeMillis();
        } else if (event.getAction() == GLFW.GLFW_RELEASE) {
            handleRelease();
        }
    }

    private static void handleRelease() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;

        Player player = mc.player;
        if (player == null) return;

        Mode mode = determineMode(player);
        if (mode == null) return;  // 我々が扱う条件ではない → MAW にそのまま任せる

        long elapsed = System.currentTimeMillis() - pressStartTime;
        boolean isLongPress = elapsed >= LONG_PRESS_THRESHOLD_MS;
        boolean wheelWasVisible = WeaponWheelState.isWheelVisible();
        int selectedIndex = WeaponWheelState.getSelectedIndex();

        if (isLongPress && wheelWasVisible) {
            if (selectedIndex < 0) {
                // 長押し → wheel 開いた → デッドゾーンで離した。
                // MAW の RMessage(0,0) (=saya 自動納刀) を抑止するだけで、我々も packet 送らない。
                killMawRState();
            }
            // 選択あり: VoltaicBladeWheelInjector.handleWheelClosed() が次 tick で処理。
            // ここでは何もしない (rKeyDown も触らない)。
            return;
        }

        // 短押し (or wheel 未表示で release): MAW を抑止して我々の packet を送信。
        killMawRState();
        switch (mode) {
            case DRAW   -> BackpackArsenalNetwork.CHANNEL.sendToServer(new DrawFromBackpackPacket());
            case SHEATH -> BackpackArsenalNetwork.CHANNEL.sendToServer(new SheathToBackpackPacket());
        }
    }

    private static Mode determineMode(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() == ArsenalItems.VOLTAIC_BLADE.get() && hasEmptyArsenalSlot(player)) {
            return Mode.SHEATH;
        }
        if (mainHand.isEmpty() && hasVoltaicBladeInAnyArsenal(player)) {
            return Mode.DRAW;
        }
        return null;
    }

    private enum Mode { DRAW, SHEATH }

    // --- MAW WeaponWheelState.rKeyDown を reflection で false に倒すヘルパ ---
    // setDown(false) → MAW.onRKeyReleased() が呼ばれる「前」に rKeyDown を false にすれば、
    // onRKeyReleased() は早期 return し RMessage(0,0) を送らない。
    private static Field rKeyDownField;
    private static boolean reflectionAttempted = false;

    private static void killMawRState() {
        if (!reflectionAttempted) {
            reflectionAttempted = true;
            try {
                rKeyDownField = WeaponWheelState.class.getDeclaredField("rKeyDown");
                rKeyDownField.setAccessible(true);
            } catch (Throwable t) {
                rKeyDownField = null;
            }
        }
        if (rKeyDownField == null) return;
        try {
            rKeyDownField.set(null, false);
        } catch (Throwable ignored) {
            // 失敗してもクラッシュさせない
        }
    }

    private static boolean hasVoltaicBladeInAnyArsenal(Player player) {
        // ★ client 側では ItemStack の ITEM_HANDLER capability は空を返す
        //   (SB は中身をサーバー側 UUID 保管し、client NBT には載せない)。
        //   そのため capability を直接 scan すると常に 0 本判定になり、抜刀が
        //   永久に発火しない。代わりにサーバーが 0.2 秒ごとに同期している
        //   NBT count (countVoltaicInRegularSlots が NBT を優先参照) を使う。
        AtomicBoolean found = new AtomicBoolean(false);
        BackpackScanner.forEachArsenalBackpack(player, backpackStack -> {
            if (found.get()) return;
            if (ArsenalBackpackItem.countVoltaicInRegularSlots(backpackStack) > 0) {
                found.set(true);
            }
        });
        return found.get();
    }

    private static boolean hasEmptyArsenalSlot(Player player) {
        AtomicBoolean found = new AtomicBoolean(false);
        BackpackScanner.forEachArsenalBackpack(player, backpackStack -> {
            if (found.get()) return;
            IItemHandler h = backpackStack.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
            if (h == null) return;
            for (int s = 0; s < h.getSlots(); s++) {
                if (h.getStackInSlot(s).isEmpty()) {
                    found.set(true);
                    return;
                }
            }
        });
        return found.get();
    }
}
