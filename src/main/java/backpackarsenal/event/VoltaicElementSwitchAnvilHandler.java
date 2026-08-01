package backpackarsenal.event;

import backpackarsenal.BackpackArsenalMod;
import backpackarsenal.init.ArsenalItems;
import backpackarsenal.item.VoltaicBladeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 金床で voltaic_blade (左) + voltaic_element_core (右) を合成すると、
 * 刀の属性モードを 電気 (ELECTRIC) ⇄ 雷 (THUNDER) にトグルする。
 *
 * <ul>
 *   <li>充電量・capacitor stage・エンチャント等の NBT は {@code left.copy()} で保持。</li>
 *   <li>core は 1 個消費 ({@link AnvilUpdateEvent#setMaterialCost(int)})。</li>
 *   <li>XP コストは低め ({@link #ANVIL_XP_COST})。</li>
 * </ul>
 *
 * 雷モードはバックパック充電が電気より遅い ( {@link VoltaicBladeItem#addBackpackCharge} で減速 )。
 */
@Mod.EventBusSubscriber(modid = BackpackArsenalMod.MODID)
public class VoltaicElementSwitchAnvilHandler {

    /** 属性切替の XP コスト (lvl)。 */
    private static final int ANVIL_XP_COST = 3;

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (left.isEmpty() || right.isEmpty()) return;
        if (left.getItem() != ArsenalItems.VOLTAIC_BLADE.get()) return;
        if (right.getItem() != ArsenalItems.VOLTAIC_ELEMENT_CORE.get()) return;

        ItemStack output = left.copy();
        // 現在モードの反対にトグル ( setElementMode が ElementType タグも貼り直す )。
        String next = VoltaicBladeItem.isThunderMode(left)
                ? VoltaicBladeItem.MODE_ELECTRIC
                : VoltaicBladeItem.MODE_THUNDER;
        VoltaicBladeItem.setElementMode(output, next);

        // 診断ログ ( capacitor 強化が保持されているか確認用 — 原因切り分け後に削除予定 )。
        BackpackArsenalMod.LOGGER.info(
            "[element-switch] mode {} -> {} | capacitor stages {}->{} | max {}->{} | charge {}->{}",
            VoltaicBladeItem.getElementMode(left), next,
            VoltaicBladeItem.getCapacitorStageCount(left), VoltaicBladeItem.getCapacitorStageCount(output),
            VoltaicBladeItem.getMaxCharge(left), VoltaicBladeItem.getMaxCharge(output),
            VoltaicBladeItem.getCharge(left), VoltaicBladeItem.getCharge(output));

        event.setOutput(output);
        event.setCost(ANVIL_XP_COST);
        event.setMaterialCost(1); // core を 1 個消費
    }
}
