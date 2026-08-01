package backpackarsenal.event;

import backpackarsenal.BackpackArsenalMod;
import backpackarsenal.init.ArsenalItems;
import backpackarsenal.item.VoltaicBladeItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * voltaic_blade の element level ブーストを金床で強化 / 減少するハンドラ。
 *
 * <ul>
 *   <li>左 = voltaic_blade</li>
 *   <li>右 = <b>glowstone_dust</b> → ブースト <b>+1</b> ( 細かく強化 )。</li>
 *   <li>右 = <b>glowstone (block)</b> → ブースト <b>+{@value #LEVELS_PER_BLOCK}</b> ( まとめて強化、 上限までクランプ )。</li>
 *   <li>右 = <b>redstone</b> → ブースト <b>-1</b> ( 細かく減少 )。</li>
 *   <li>右 = <b>redstone_block</b> → ブースト <b>-{@value #LEVELS_PER_BLOCK}</b> ( まとめて減少、 0 までクランプ )。</li>
 * </ul>
 *
 * survival 上限は {@link VoltaicBladeItem#MAX_ELEMENT_LEVEL_BOOST}、 creative は無制限。
 * block は 1 個で 9 レベル換算 ( growth charger と同じ )。
 *
 * <p><b>スタックでバッチ</b>: 右スロットの個数分を一気に消費して上げ下げする
 * ( 例: glowstone_dust ×5 → 一度に +5、 上限/在庫/XP39 の範囲でクランプ )。 消費数は
 * 実際に使ったレベル数ぶんだけ ( 端数の block は 1 個単位で切り上げ消費 )。</p>
 *
 * 実効 element level = 充電由来 (1〜3) + ブースト。 レベルが高いほど 1 ヒットの
 * 充電消費が {@link VoltaicBladeItem#CHARGE_COST_PER_ELEMENT_LEVEL} ずつ徐々に増える。
 *
 * XP コストは上げ先レベルの累積 ( 徐々に高くなる )。 下げは一律 1。
 */
@Mod.EventBusSubscriber(modid = BackpackArsenalMod.MODID)
public class VoltaicElementLevelAnvilHandler {

    /** block 1 個で up/down するレベル数 ( growth charger と同じ換算 )。 */
    private static final int LEVELS_PER_BLOCK = 9;

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (left.isEmpty() || right.isEmpty()) return;
        if (left.getItem() != ArsenalItems.VOLTAIC_BLADE.get()) return;

        // 右アイテムから 方向 ( up/down ) と 1 個あたりのレベル数を判定。
        boolean up;
        int perItem;
        if (right.getItem() == Items.GLOWSTONE_DUST) {
            up = true;  perItem = 1;
        } else if (right.getItem() == Items.GLOWSTONE) { // glowstone block のアイテム名は GLOWSTONE
            up = true;  perItem = LEVELS_PER_BLOCK;
        } else if (right.getItem() == Items.REDSTONE) {
            up = false; perItem = 1;
        } else if (right.getItem() == Items.REDSTONE_BLOCK) {
            up = false; perItem = LEVELS_PER_BLOCK;
        } else {
            return;
        }

        int boost = VoltaicBladeItem.getElementLevelBoost(left);
        Player player = event.getPlayer();
        boolean creative = player != null && player.isCreative();

        // 右スタックの個数 × 1 個あたりレベル = 一気に使えるレベル在庫 ( スタックでバッチ強化/減少 )。
        long availableLevels = (long) right.getCount() * perItem;

        int newBoost;
        int xpCost;
        int matCost; // 消費する右アイテム数 ( level 消費量 ÷ perItem の切り上げ )
        if (up) {
            int cap = creative ? Integer.MAX_VALUE : VoltaicBladeItem.MAX_ELEMENT_LEVEL_BOOST;
            if (boost >= cap) return; // これ以上上げられない
            // cap ・ creative 安全弁(1000) ・ survival XP39 の範囲で、 在庫が続く限り一気に上げる。
            int raiseBy = 0;
            long xp = 0;
            while (raiseBy < availableLevels && boost + raiseBy < cap && raiseBy < 1000) {
                int nextLevel = boost + raiseBy + 1; // 上げ先レベルほど徐々に高コスト
                if (!creative && xp + nextLevel > 39) break; // vanilla "Too Expensive!" 閾値
                xp += nextLevel;
                raiseBy++;
            }
            if (raiseBy == 0) return;
            newBoost = boost + raiseBy;
            xpCost = (int) Math.max(1, xp);
            matCost = (int) ((raiseBy + perItem - 1) / perItem);
        } else {
            if (boost <= 0) return; // これ以上下げられない
            int lowerBy = (int) Math.min(availableLevels, boost);
            if (lowerBy <= 0) return;
            newBoost = boost - lowerBy;
            xpCost = 1;
            matCost = (int) ((lowerBy + perItem - 1) / perItem);
        }

        ItemStack output = left.copy();
        VoltaicBladeItem.setElementLevelBoost(output, newBoost);

        event.setOutput(output);
        event.setCost(xpCost);
        event.setMaterialCost(creative ? 0 : matCost);
    }
}
