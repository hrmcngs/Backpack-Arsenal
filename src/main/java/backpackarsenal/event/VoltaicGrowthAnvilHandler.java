package backpackarsenal.event;

import backpackarsenal.BackpackArsenalMod;
import backpackarsenal.init.ArsenalItems;
import backpackarsenal.upgrade.VoltaicGrowthUpgradeItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * voltaic_growth_charger_upgrade をアンビルで強化するハンドラ。
 *
 * 左 = voltaic_growth_charger_upgrade ( level N )
 * 右 = redstone (dust) または redstone_block
 *
 * 必要 redstone dust 換算 = N + 1
 *   - dust:  1 個 = 1 dust
 *   - block: 1 個 = 9 dust (vanilla の craft 換算と同じ)
 *
 * 必要 ItemStack 数: ceil( needed_dust / 1or9 )
 * XP コスト = N + 1 lvl
 *
 * Creative プレイヤーは XP / material 無視で level を上げられる。
 */
@Mod.EventBusSubscriber(modid = BackpackArsenalMod.MODID)
public class VoltaicGrowthAnvilHandler {

    /** redstone block 1 個 = dust 何個分か。 vanilla の craft レシピと同じ 9。 */
    private static final int DUST_PER_BLOCK = 9;

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (left.isEmpty() || right.isEmpty()) return;
        if (left.getItem() != ArsenalItems.VOLTAIC_GROWTH_CHARGER_UPGRADE.get()) return;

        // dust 単価で計算: dust なら 1、 block なら 9。 それ以外は無効。
        int dustPerItem;
        if (right.getItem() == Items.REDSTONE) {
            dustPerItem = 1;
        } else if (right.getItem() == Items.REDSTONE_BLOCK) {
            dustPerItem = DUST_PER_BLOCK;
        } else {
            return;
        }

        int level = VoltaicGrowthUpgradeItem.getLevel(left);
        Player player = event.getPlayer();
        boolean creative = player != null && player.isCreative();

        // 1 スタック分の素材を使って一気に N レベル上げる ( = バッチ強化 )。
        // フラットコスト: 1 level 上げる毎に REDSTONE_COST_PER_LEVEL dust + XP_COST_PER_LEVEL XP。
        // 制約:
        //   1) 累積 dust ≤ right の dust 換算値 ( right.getCount() × dustPerItem )
        //   2) survival では累積 XP ≤ 39 ( vanilla anvil の "Too Expensive!" 閾値 = 40 lvl )
        //   3) creative 時の安全弁: 1 click あたり最大 1000 levels
        final int dustPerLevel = VoltaicGrowthUpgradeItem.REDSTONE_COST_PER_LEVEL;
        final int xpPerLevel = VoltaicGrowthUpgradeItem.XP_COST_PER_LEVEL;
        long availableDust = (long) right.getCount() * dustPerItem;
        long totalDust = 0;
        long totalXp = 0;
        int batch = 0;
        while (true) {
            if (totalDust + dustPerLevel > availableDust) break;
            if (!creative && totalXp + xpPerLevel > 39) break;
            totalDust += dustPerLevel;
            totalXp += xpPerLevel;
            batch++;
            if (batch >= 1000) break;
        }

        if (batch == 0) return;

        // 必要 ItemStack 数 = ceil(totalDust / dustPerItem)。
        // block 使用時、 端数 dust は失われる ( 例: 11 dust 必要 + block 使用 → 2 block 消費 = 18 dust )。
        int neededItems = (int) ((totalDust + dustPerItem - 1) / dustPerItem);

        ItemStack output = left.copy();
        VoltaicGrowthUpgradeItem.setLevel(output, level + batch);

        // vanilla anvil は cost > 0 でないと "Take" を許可しない。
        // creative でも 1 以上を設定し、 vanilla の anvil.takeResult が
        // player.abilities.instabuild で XP 消費を bypass してくれる。
        int xpCost = (int) Math.max(1, totalXp);

        event.setOutput(output);
        event.setMaterialCost(creative ? 0 : neededItems);
        event.setCost(xpCost);
    }
}
