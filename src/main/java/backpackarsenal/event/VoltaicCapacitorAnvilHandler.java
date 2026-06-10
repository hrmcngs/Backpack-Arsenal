package backpackarsenal.event;

import backpackarsenal.BackpackArsenalMod;
import backpackarsenal.init.ArsenalItems;
import backpackarsenal.item.VoltaicBladeItem;
import backpackarsenal.item.VoltaicCapacitorUpgradeItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;
import java.util.Collections;

/**
 * voltaic_blade の最大充電 を capacitor upgrade で強化、 およびハサミで剥がす仕組み。
 *
 * (A) アンビル合成 (左=blade、右=capacitor X) — 強化
 *     右のスタック数だけ一気に消費して bonus stage を append する。
 *     - Survival: 合計 {@link VoltaicBladeItem#MAX_CAPACITOR_BONUS} stage まで
 *     - Creative: 無制限 (player.isCreative() で bypass)
 *     - XP コスト: stage idx に応じてスケール
 *
 * (B) 手持ちで剥がす (blade 主手 + shears 副手 + sneak + 右クリック) — strip
 *     stage 全部 + エンチャント全部を消す。 shears は 1 ダメージ (Unbreaking で確率回避)。
 *     capacitor item は戻らない (= 完全剥がし)。
 *
 * (C) アンビルで剥がす (左=blade、右=shears) — anvil strip
 *     stage 全部を消す。 エンチャントは保持。 shears は 1 ダメージ + XP コスト。
 *     {@link AnvilRepairEvent} で take 後にダメージ済み shears を inventory に戻す。
 *
 * クラフト台で剥がす (D) は {@link backpackarsenal.recipe.VoltaicBladeShearStripRecipe} で別途実装。
 */
@Mod.EventBusSubscriber(modid = BackpackArsenalMod.MODID)
public class VoltaicCapacitorAnvilHandler {

    /** アンビルで shears strip した時の XP コスト (lvl) */
    private static final int ANVIL_SHEARS_STRIP_XP_COST = 5;

    // ───────────────────────────────────────────────────────
    // (A) anvil bulk apply (capacitor) / (C) anvil strip (shears)
    // ───────────────────────────────────────────────────────
    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (left.isEmpty() || right.isEmpty()) return;
        if (left.getItem() != ArsenalItems.VOLTAIC_BLADE.get()) return;

        // ── (A) capacitor で強化 ──
        if (right.getItem() instanceof VoltaicCapacitorUpgradeItem cap) {
            int[] currentStages = VoltaicBladeItem.getCapacitorStages(left);
            Player player = event.getPlayer();
            boolean creative = player != null && player.isCreative();
            int maxStages = creative ? Integer.MAX_VALUE : VoltaicBladeItem.MAX_CAPACITOR_BONUS;
            int remaining = maxStages - currentStages.length;
            if (remaining <= 0) return;

            int toApply = Math.min(remaining, right.getCount());
            int bonusValue = cap.getBonusValue();

            int[] newStages = Arrays.copyOf(currentStages, currentStages.length + toApply);
            Arrays.fill(newStages, currentStages.length, newStages.length, bonusValue);

            ItemStack output = left.copy();
            VoltaicBladeItem.setCapacitorStages(output, newStages);

            int xpCost = 0;
            for (int i = 0; i < toApply; i++) {
                xpCost += 1 + (currentStages.length + i) * 2;
            }
            event.setOutput(output);
            event.setCost(Math.max(1, xpCost));
            event.setMaterialCost(toApply);
            return;
        }

        // ── (C) shears で strip (エンチャント保持) ──
        if (right.getItem() instanceof ShearsItem) {
            int[] stages = VoltaicBladeItem.getCapacitorStages(left);
            if (stages.length == 0) return; // 剥がす stage 無し

            ItemStack output = left.copy();
            VoltaicBladeItem.setCapacitorStages(output, new int[0]);
            // エンチャントには手を加えない (= 保持される)

            event.setOutput(output);
            event.setCost(ANVIL_SHEARS_STRIP_XP_COST);
            event.setMaterialCost(1); // shears は vanilla 側で消費される。 AnvilRepairEvent で
                                       // ダメージ 1 だけ受けた shears を player に戻す。
        }
    }

    /** (C) のフォローアップ: アンビルで shears を使った時、 take 後に「ダメージ 1 の shears」を
     *  player に戻すことで「消費」を「1 ダメージ」に置き換える。 Unbreaking は hurtAndBreak が
     *  処理する。 */
    @SubscribeEvent
    public static void onAnvilRepair(AnvilRepairEvent event) {
        ItemStack right = event.getRight();
        ItemStack output = event.getOutput();
        if (output.getItem() != ArsenalItems.VOLTAIC_BLADE.get()) return;
        if (!(right.getItem() instanceof ShearsItem)) return;
        // (C) の結果は stages が 0、 capacitor 経由なら 0 以外。 念のためチェック。
        if (VoltaicBladeItem.getCapacitorStageCount(output) != 0) return;

        Player p = event.getEntity();
        ItemStack returned = right.copy();
        returned.setCount(1);
        // 1 ダメージ。 Unbreaking で確率回避される (vanilla hurtAndBreak の仕様)。
        returned.hurtAndBreak(1, p, e -> {});
        if (returned.isEmpty()) return; // shears 破壊。 戻さない。
        if (!p.getInventory().add(returned)) {
            p.drop(returned, false);
        }
    }

    // ───────────────────────────────────────────────────────
    // (B) 手持ち strip: blade 主手 + shears 副手 + sneak + 右クリック
    // ───────────────────────────────────────────────────────
    @SubscribeEvent
    public static void onInHandShearsStrip(PlayerInteractEvent.RightClickItem event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        Player p = event.getEntity();
        if (!p.isShiftKeyDown()) return;

        ItemStack main = event.getItemStack();
        if (main.getItem() != ArsenalItems.VOLTAIC_BLADE.get()) return;
        ItemStack offhand = p.getOffhandItem();
        if (!(offhand.getItem() instanceof ShearsItem)) return;

        int[] stages = VoltaicBladeItem.getCapacitorStages(main);
        boolean hasStages = stages.length > 0;
        boolean hasEnchants = !main.getAllEnchantments().isEmpty();
        if (!hasStages && !hasEnchants) return;

        if (!p.level().isClientSide) {
            if (hasStages) VoltaicBladeItem.setCapacitorStages(main, new int[0]);
            if (hasEnchants) {
                EnchantmentHelper.setEnchantments(Collections.emptyMap(), main);
            }
            // Unbreaking 配慮あり ダメージ
            offhand.hurtAndBreak(1, p, e -> e.broadcastBreakEvent(EquipmentSlot.OFFHAND));
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(),
                SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 0.8f, 1.1f);
        }
        event.setCancellationResult(InteractionResult.sidedSuccess(p.level().isClientSide));
        event.setCanceled(true);
    }

    /** capacitor bonus 値 → 対応する registry item (剥がし戻し用、 anvil strip は使わない)。 */
    @SuppressWarnings("unused")
    private static Item capacitorItemForBonus(int bonus) {
        if (bonus == VoltaicBladeItem.CAPACITOR_TIER_III_BONUS) return ArsenalItems.VOLTAIC_CAPACITOR_UPGRADE.get();
        if (bonus == VoltaicBladeItem.CAPACITOR_TIER_II_BONUS)  return ArsenalItems.VOLTAIC_CAPACITOR_UPGRADE_II.get();
        if (bonus == VoltaicBladeItem.CAPACITOR_TIER_I_BONUS)   return ArsenalItems.VOLTAIC_CAPACITOR_UPGRADE_I.get();
        return null;
    }
}
