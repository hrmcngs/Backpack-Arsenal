package backpackarsenal.client;

import backpackarsenal.BackpackArsenalMod;
import backpackarsenal.init.ArsenalItems;
import backpackarsenal.network.BackpackArsenalNetwork;
import backpackarsenal.network.DrawFromBackpackPacket;
import backpackarsenal.network.SheathToBackpackPacket;
import backpackarsenal.util.BackpackScanner;
import net.minecraft.client.Minecraft;
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
 * MAW Weapon Wheel に Arsenal Backpack のエントリを注入する。
 *
 * フィルタ: ArsenalBackpack のみ (バニラ SB の通常 backpack は表示しない)。
 *
 * 動作 (WheelMode に応じて切替):
 *   DRAW   — 各 ArsenalBackpack 内の voltaic_blade を引き抜き候補として表示
 *   SHEATH — 空きのある各 ArsenalBackpack を収納先として表示 (プレイヤーが手に voltaic_blade を
 *            持っている前提)
 *
 * 選択時動作: wheel 閉じ遷移を検出 → 選択 index が我々の注入分なら
 *   DRAW  → DrawFromBackpackPacket
 *   SHEATH → SheathToBackpackPacket
 *
 * R 短押し (BackpackDrawClient) は wheel を介さずに直接 packet を発行するので
 * 「短押し優先で収納」が機能する。長押し時にだけ wheel が出る。
 */
@Mod.EventBusSubscriber(
    modid = BackpackArsenalMod.MODID,
    value = Dist.CLIENT,
    bus = Mod.EventBusSubscriber.Bus.FORGE
)
public class VoltaicBladeWheelInjector {

    private static boolean wheelWasVisible = false;
    /** 前 tick で我々が注入したエントリ群 (IdentityHashMap で参照同一性) */
    private static final IdentityHashMap<CuriosScabbardHelper.DrawableWeaponInfo, Boolean>
        injectedEntries = new IdentityHashMap<>();
    /** wheel 閉じ後 reset される前の最終選択 index */
    private static int lastSelectedIndex = -1;
    /** wheel 閉じ後 reset される前の最終 list 参照 */
    private static List<CuriosScabbardHelper.DrawableWeaponInfo> lastList = null;
    /** wheel 閉じ後 reset される前の最終モード (DRAW/SHEATH) */
    private static WeaponWheelState.WheelMode lastMode = null;

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        boolean visible = WeaponWheelState.isWheelVisible();
        if (visible) {
            WeaponWheelState.WheelMode mode = WeaponWheelState.getCurrentMode();
            injectEntries(mc.player, mode);
            lastSelectedIndex = WeaponWheelState.getSelectedIndex();
            lastList = WeaponWheelState.getDrawableWeapons();
            lastMode = mode;
        } else if (wheelWasVisible) {
            handleWheelClosed();
        }
        wheelWasVisible = visible;
    }

    /** wheel に Arsenal Backpack 関連エントリを注入。重複は毎 tick 作り直し。
     *
     *  mode の判定は MAW WheelMode に依存しているが、開幕直後など mode が未確定
     *  (null) の tick がある。その場合は player のメインハンドの状態から fallback
     *  推定する (= 空なら DRAW、voltaic_blade を持っていれば SHEATH)。
     */
    private static void injectEntries(Player player, WeaponWheelState.WheelMode mode) {
        try {
            Field f = WeaponWheelState.class.getDeclaredField("drawableWeapons");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<CuriosScabbardHelper.DrawableWeaponInfo> list =
                (List<CuriosScabbardHelper.DrawableWeaponInfo>) f.get(null);
            if (list == null) return;

            // 前 tick の注入を一旦除去 (毎 tick リフレッシュ)
            list.removeIf(injectedEntries::containsKey);
            injectedEntries.clear();

            // mode 未確定 / 未対応値の時はメインハンドから推定する fallback
            ItemStack mainHand = player.getMainHandItem();
            boolean isDrawMode  = (mode == WeaponWheelState.WheelMode.DRAW)
                    || (mode == null && mainHand.isEmpty());
            boolean isSheathMode = (mode == WeaponWheelState.WheelMode.SHEATH)
                    || (mode == null && mainHand.getItem() == ArsenalItems.VOLTAIC_BLADE.get());

            BackpackScanner.forEachArsenalBackpackWithSource(player, (backpackStack, source) -> {
                IItemHandler handler = backpackStack
                    .getCapability(ForgeCapabilities.ITEM_HANDLER)
                    .orElse(null);
                if (handler == null) return;

                if (isDrawMode) {
                    for (int s = 0; s < handler.getSlots(); s++) {
                        ItemStack inner = handler.getStackInSlot(s);
                        if (inner.getItem() != ArsenalItems.VOLTAIC_BLADE.get()) continue;
                        addEntry(list, backpackStack, inner, source);
                    }
                } else if (isSheathMode) {
                    if (mainHand.getItem() != ArsenalItems.VOLTAIC_BLADE.get()) return;
                    if (!hasEmptySlot(handler)) return;
                    addEntry(list, backpackStack, mainHand, source);
                }
            });
        } catch (Throwable t) {
            BackpackArsenalMod.LOGGER.warn(
                "[backpack_arsenal] wheel injection failed: {}", t.toString());
        }
    }

    private static void addEntry(
        List<CuriosScabbardHelper.DrawableWeaponInfo> list,
        ItemStack backpackStack,
        ItemStack bladeStack,
        BackpackScanner.BackpackSource source
    ) {
        // wheel UI の getSlotLabel は ScabbardLocation.CURIOS+curioSlotId="back" を「背中」と表示し、
        // ScabbardLocation.INVENTORY を「インベントリ」と表示する。
        // 背負っているバックパックは背中ラベル、インベントリ内のは「インベントリ」を表示させる。
        CuriosScabbardHelper.ScabbardLocation location;
        String curioSlotId;
        if (source == BackpackScanner.BackpackSource.CURIOS_BACK) {
            location = CuriosScabbardHelper.ScabbardLocation.CURIOS;
            curioSlotId = "back";
        } else {
            location = CuriosScabbardHelper.ScabbardLocation.INVENTORY;
            curioSlotId = null;
        }
        // slotIndex は wheel 視覚にしか使われない (我々の packet 側では参照しない)。
        CuriosScabbardHelper.DrawableWeaponInfo entry =
            new CuriosScabbardHelper.DrawableWeaponInfo(
                backpackStack,
                bladeStack,
                location,
                curioSlotId,
                -1
            );
        injectedEntries.put(entry, Boolean.TRUE);
        list.add(entry);
    }

    private static boolean hasEmptySlot(IItemHandler handler) {
        for (int s = 0; s < handler.getSlots(); s++) {
            if (handler.getStackInSlot(s).isEmpty()) return true;
        }
        return false;
    }

    /** wheel 閉じる直前の選択を見て対応するパケットを送信。 */
    private static void handleWheelClosed() {
        try {
            if (lastList == null || lastSelectedIndex < 0 || lastSelectedIndex >= lastList.size()) {
                return;
            }
            CuriosScabbardHelper.DrawableWeaponInfo selected = lastList.get(lastSelectedIndex);
            if (!injectedEntries.containsKey(selected)) return;

            if (lastMode == WeaponWheelState.WheelMode.DRAW) {
                BackpackArsenalNetwork.CHANNEL.sendToServer(new DrawFromBackpackPacket());
            } else if (lastMode == WeaponWheelState.WheelMode.SHEATH) {
                BackpackArsenalNetwork.CHANNEL.sendToServer(new SheathToBackpackPacket());
            }
        } catch (Exception ignored) {
            // ignore
        } finally {
            injectedEntries.clear();
            lastSelectedIndex = -1;
            lastList = null;
            lastMode = null;
        }
    }

    /** ArsenalBackpack 限定判定 (バニラ SB バックパックは false) */
    public static boolean isArsenalBackpack(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == ArsenalItems.ARSENAL_BACKPACK_ELECTRON.get();
    }
}
