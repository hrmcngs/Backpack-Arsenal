package backpackarsenal.util;

import backpackarsenal.event.BackpackChargingHandler;
import backpackarsenal.init.ArsenalItems;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * プレイヤーが所持するバックパックを統一的に走査するユーティリティ。
 *
 *   - 通常インベントリ (ホットバー + メイン + offhand)
 *   - Curios の "back" スロット (背負いスロット)
 *
 * 両方を1つのループとして扱う。collectorのほうで item id でフィルタする。
 */
public class BackpackScanner {

    /** バックパックの所在地。Wheel UI で「インベントリ」「背中」を出し分ける用。 */
    public enum BackpackSource { INVENTORY, CURIOS_BACK }

    /** ArsenalBackpack のみ走査 (sheath / draw / wheel 共用) */
    public static void forEachArsenalBackpack(Player player, Consumer<ItemStack> consumer) {
        forEachArsenalBackpackWithSource(player, (s, source) -> consumer.accept(s));
    }

    /**
     * ArsenalBackpack を走査し、所在地 (inventory or Curios "back") も合わせて返す。
     * Wheel UI のラベル ("インベントリ" / "背中") 切替や、背負いバックパックを判別したい
     * 呼び出し側用。
     */
    public static void forEachArsenalBackpackWithSource(
            Player player,
            BiConsumer<ItemStack, BackpackSource> consumer) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (isArsenalBackpack(s)) consumer.accept(s, BackpackSource.INVENTORY);
        }
        forEachInCuriosBack(player, s -> {
            if (isArsenalBackpack(s)) consumer.accept(s, BackpackSource.CURIOS_BACK);
        });
    }

    /** SB + ArsenalBackpack の両方を走査 (charging 用) */
    public static void forEachAnyBackpack(Player player, Consumer<ItemStack> consumer) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (BackpackChargingHandler.isSophisticatedBackpack(s)) consumer.accept(s);
        }
        forEachInCuriosBack(player, s -> {
            if (BackpackChargingHandler.isSophisticatedBackpack(s)) consumer.accept(s);
        });
    }

    /** Curios "back" スロット内のすべての ItemStack を消費者に渡す。 */
    private static void forEachInCuriosBack(Player player, Consumer<ItemStack> consumer) {
        try {
            CuriosApi.getCuriosInventory(player).ifPresent(curios ->
                curios.getStacksHandler("back").ifPresent(stacks -> {
                    var handler = stacks.getStacks();
                    for (int i = 0; i < handler.getSlots(); i++) {
                        consumer.accept(handler.getStackInSlot(i));
                    }
                })
            );
        } catch (Throwable ignored) {
            // Curios が無い/version 不整合の場合はサイレントに諦める
        }
    }

    public static boolean isArsenalBackpack(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == ArsenalItems.ARSENAL_BACKPACK_ELECTRON.get();
    }
}
