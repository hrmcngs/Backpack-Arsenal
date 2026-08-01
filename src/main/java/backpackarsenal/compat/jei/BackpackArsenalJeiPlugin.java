package backpackarsenal.compat.jei;

import backpackarsenal.BackpackArsenalMod;
import backpackarsenal.init.ArsenalItems;
import backpackarsenal.item.VoltaicBladeItem;
import backpackarsenal.upgrade.VoltaicGrowthUpgradeItem;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.vanilla.IJeiAnvilRecipe;
import mezz.jei.api.recipe.vanilla.IVanillaRecipeFactory;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI 連携。 JEI は @JeiPlugin annotation で起動時に discover するので、 このクラスは
 * mod main code から参照不要 (JEI 無し環境では class load も発生しない)。
 *
 * Anvil カテゴリへの登録:
 *   各 capacitor tier (I/II/III) について、 stage 0→1 から stage 4→5 までの 5 段階分の
 *   anvil recipe を登録する。 JEI の {@code AnvilRecipeMaker.findLevelsCost} は実際に
 *   fake AnvilMenu を作って Forge の {@code AnvilUpdateEvent} を発火するので、
 *   {@link backpackarsenal.event.VoltaicCapacitorAnvilHandler} が設定した XP コストが
 *   そのまま JEI 上に "Enchantment Cost: N" として表示される (stage が進むほど重くなる
 *   = 1→3→5→7→9 lvl)。
 *
 * crafting recipe (capacitor I/II/III の作成、 charger upgrade 全 tier、 base
 * voltaic_charger_upgrade) は vanilla {@code minecraft:crafting_shaped/smithing_transform}
 * で書いているので JEI が自動 discover する。 ここでは何もする必要なし。
 */
@JeiPlugin
public class BackpackArsenalJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID =
        new ResourceLocation(BackpackArsenalMod.MODID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        IVanillaRecipeFactory factory = registration.getVanillaRecipeFactory();

        List<IJeiAnvilRecipe> anvilRecipes = new ArrayList<>();
        anvilRecipes.addAll(buildTierRecipes(factory,
            ArsenalItems.VOLTAIC_CAPACITOR_UPGRADE_I.get(), VoltaicBladeItem.CAPACITOR_TIER_I_BONUS, "i"));
        anvilRecipes.addAll(buildTierRecipes(factory,
            ArsenalItems.VOLTAIC_CAPACITOR_UPGRADE_II.get(), VoltaicBladeItem.CAPACITOR_TIER_II_BONUS, "ii"));
        anvilRecipes.addAll(buildTierRecipes(factory,
            ArsenalItems.VOLTAIC_CAPACITOR_UPGRADE.get(), VoltaicBladeItem.CAPACITOR_TIER_III_BONUS, "iii"));

        // shears で stage 剥がし (anvil) — 入力 blade は stage 1 を例示、 出力は stage 0
        anvilRecipes.add(factory.createAnvilRecipe(
            bladeWithStages(stageArray(1, VoltaicBladeItem.CAPACITOR_TIER_III_BONUS)),
            List.of(new ItemStack(net.minecraft.world.item.Items.SHEARS)),
            List.of(bladeWithStages(new int[0])),
            new ResourceLocation(BackpackArsenalMod.MODID, "anvil/shears_strip")
        ));

        // 成長型 charger upgrade の anvil レシピ。 フラットコスト ( level に依らず一定 )。
        //   1 dust = 1 level、 1 block = 9 levels ( 1 click で消費 )。
        //   サバイバルでは XP 39 lvl 上限で 1 click あたり最大 39 levels まで強化可能。
        // JEI 表示は代表 1 例ずつ ( Lv 0 → 1, Lv 0 → 9 )。
        final int dustPerLevel = VoltaicGrowthUpgradeItem.REDSTONE_COST_PER_LEVEL;
        anvilRecipes.add(factory.createAnvilRecipe(
            growthChargerAt(0),
            List.of(new ItemStack(net.minecraft.world.item.Items.REDSTONE, dustPerLevel)),
            List.of(growthChargerAt(dustPerLevel)),
            new ResourceLocation(BackpackArsenalMod.MODID, "anvil/growth_charger_per_dust")
        ));
        anvilRecipes.add(factory.createAnvilRecipe(
            growthChargerAt(0),
            List.of(new ItemStack(net.minecraft.world.item.Items.REDSTONE_BLOCK, 1)),
            List.of(growthChargerAt(9 / dustPerLevel)),
            new ResourceLocation(BackpackArsenalMod.MODID, "anvil/growth_charger_per_block")
        ));

        // 属性切替 (voltaic_element_core) の anvil レシピ。 電気⇄雷 の両方向を表示。
        //   VoltaicElementSwitchAnvilHandler が cost 3 を設定するので JEI にもそう出る。
        int demoCharge = VoltaicBladeItem.getMaxCharge(new ItemStack(ArsenalItems.VOLTAIC_BLADE.get()));
        anvilRecipes.add(factory.createAnvilRecipe(
            bladeChargedMode(demoCharge, VoltaicBladeItem.MODE_ELECTRIC),
            List.of(new ItemStack(ArsenalItems.VOLTAIC_ELEMENT_CORE.get())),
            List.of(bladeChargedMode(demoCharge, VoltaicBladeItem.MODE_THUNDER)),
            new ResourceLocation(BackpackArsenalMod.MODID, "anvil/element_switch_to_thunder")
        ));
        anvilRecipes.add(factory.createAnvilRecipe(
            bladeChargedMode(demoCharge, VoltaicBladeItem.MODE_THUNDER),
            List.of(new ItemStack(ArsenalItems.VOLTAIC_ELEMENT_CORE.get())),
            List.of(bladeChargedMode(demoCharge, VoltaicBladeItem.MODE_ELECTRIC)),
            new ResourceLocation(BackpackArsenalMod.MODID, "anvil/element_switch_to_electric")
        ));

        // element level ブースト 強化 (glowstone_dust で +1)。 boost 0→1 .. 4→5 の各段階を表示。
        //   VoltaicElementLevelAnvilHandler が cost = 上げ先レベル ( 1→5 ) を設定するので
        //   JEI 上で徐々に重くなるのが見える。
        for (int b = 0; b < VoltaicBladeItem.MAX_ELEMENT_LEVEL_BOOST; b++) {
            anvilRecipes.add(factory.createAnvilRecipe(
                bladeChargedBoost(demoCharge, b),
                List.of(new ItemStack(net.minecraft.world.item.Items.GLOWSTONE_DUST)),
                List.of(bladeChargedBoost(demoCharge, b + 1)),
                new ResourceLocation(BackpackArsenalMod.MODID, "anvil/element_boost_up_" + b)
            ));
        }
        // element level ブースト 減少 (redstone で -1)。 代表 1 例 ( boost 1 → 0 )。
        anvilRecipes.add(factory.createAnvilRecipe(
            bladeChargedBoost(demoCharge, 1),
            List.of(new ItemStack(net.minecraft.world.item.Items.REDSTONE)),
            List.of(bladeChargedBoost(demoCharge, 0)),
            new ResourceLocation(BackpackArsenalMod.MODID, "anvil/element_boost_down")
        ));
        // block でまとめて up/down ( 1 個で survival 上限まで / 0 まで )。
        anvilRecipes.add(factory.createAnvilRecipe(
            bladeChargedBoost(demoCharge, 0),
            List.of(new ItemStack(net.minecraft.world.item.Items.GLOWSTONE)),
            List.of(bladeChargedBoost(demoCharge, VoltaicBladeItem.MAX_ELEMENT_LEVEL_BOOST)),
            new ResourceLocation(BackpackArsenalMod.MODID, "anvil/element_boost_up_block")
        ));
        anvilRecipes.add(factory.createAnvilRecipe(
            bladeChargedBoost(demoCharge, VoltaicBladeItem.MAX_ELEMENT_LEVEL_BOOST),
            List.of(new ItemStack(net.minecraft.world.item.Items.REDSTONE_BLOCK)),
            List.of(bladeChargedBoost(demoCharge, 0)),
            new ResourceLocation(BackpackArsenalMod.MODID, "anvil/element_boost_down_block")
        ));

        registration.addRecipes(RecipeTypes.ANVIL, anvilRecipes);

        // Item info (JEI の "ℹ" タブ)
        ItemStack baseBlade = new ItemStack(ArsenalItems.VOLTAIC_BLADE.get());
        registration.addIngredientInfo(
            baseBlade, VanillaTypes.ITEM_STACK,
            Component.translatable("jei.backpack_arsenal.voltaic_blade.info"));

        registration.addIngredientInfo(
            new ItemStack(ArsenalItems.VOLTAIC_CAPACITOR_UPGRADE_I.get()),
            VanillaTypes.ITEM_STACK,
            Component.translatable("jei.backpack_arsenal.voltaic_capacitor_upgrade.info"));
        registration.addIngredientInfo(
            new ItemStack(ArsenalItems.VOLTAIC_CAPACITOR_UPGRADE_II.get()),
            VanillaTypes.ITEM_STACK,
            Component.translatable("jei.backpack_arsenal.voltaic_capacitor_upgrade.info"));
        registration.addIngredientInfo(
            new ItemStack(ArsenalItems.VOLTAIC_CAPACITOR_UPGRADE.get()),
            VanillaTypes.ITEM_STACK,
            Component.translatable("jei.backpack_arsenal.voltaic_capacitor_upgrade.info"));

        registration.addIngredientInfo(
            new ItemStack(ArsenalItems.VOLTAIC_CHARGER_UPGRADE.get()),
            VanillaTypes.ITEM_STACK,
            Component.translatable("jei.backpack_arsenal.voltaic_charger_upgrade.info"));

        registration.addIngredientInfo(
            growthChargerAt(0),
            VanillaTypes.ITEM_STACK,
            Component.translatable("jei.backpack_arsenal.voltaic_growth_charger_upgrade.info"));

        registration.addIngredientInfo(
            new ItemStack(ArsenalItems.VOLTAIC_ELEMENT_CORE.get()),
            VanillaTypes.ITEM_STACK,
            Component.translatable("jei.backpack_arsenal.voltaic_element_core.info"));
    }

    /** 指定 charge + 属性モードの blade。 属性ツールチップ ( 電気/雷 ) を JEI 上で見せる用。 */
    private static ItemStack bladeChargedMode(int charge, String mode) {
        ItemStack s = new ItemStack(ArsenalItems.VOLTAIC_BLADE.get());
        VoltaicBladeItem.setElementMode(s, mode);
        VoltaicBladeItem.setCharge(s, charge);
        return s;
    }

    /** 指定 charge + element level ブーストの blade。 ブースト表示を JEI 上で見せる用。 */
    private static ItemStack bladeChargedBoost(int charge, int boost) {
        ItemStack s = new ItemStack(ArsenalItems.VOLTAIC_BLADE.get());
        VoltaicBladeItem.setCharge(s, charge);
        VoltaicBladeItem.setElementLevelBoost(s, boost);
        return s;
    }

    /** 指定 level の growth charger upgrade itemstack を作る。 */
    private static ItemStack growthChargerAt(int level) {
        ItemStack s = new ItemStack(ArsenalItems.VOLTAIC_GROWTH_CHARGER_UPGRADE.get());
        if (level > 0) VoltaicGrowthUpgradeItem.setLevel(s, level);
        return s;
    }

    /** 1 capacitor tier について stage 0→1 .. 4→5 の 5 recipe を生成。 stage が進むと
     *  AnvilUpdateEvent で XP cost が 1→3→5→7→9 になるので JEI 上で重くなっていくのが見える。 */
    private static List<IJeiAnvilRecipe> buildTierRecipes(
            IVanillaRecipeFactory factory,
            net.minecraft.world.item.Item capacitor,
            int bonusValue,
            String tierSuffix) {
        List<IJeiAnvilRecipe> out = new ArrayList<>(VoltaicBladeItem.MAX_CAPACITOR_BONUS);
        for (int currentStage = 0; currentStage < VoltaicBladeItem.MAX_CAPACITOR_BONUS; currentStage++) {
            ItemStack input = bladeWithStages(stageArray(currentStage, bonusValue));
            ItemStack output = bladeWithStages(stageArray(currentStage + 1, bonusValue));
            ResourceLocation uid = new ResourceLocation(BackpackArsenalMod.MODID,
                "anvil/capacitor_" + tierSuffix + "_stage_" + currentStage);
            out.add(factory.createAnvilRecipe(input, List.of(new ItemStack(capacitor)), List.of(output), uid));
        }
        return out;
    }

    /** 「全部 bonusValue が入った N 段の stage 配列」 を作る。 表示用 (tooltip "Capacitor: N/5 (+X max)" に効く)。 */
    private static int[] stageArray(int stageCount, int bonusValue) {
        int[] a = new int[stageCount];
        java.util.Arrays.fill(a, bonusValue);
        return a;
    }

    private static ItemStack bladeWithStages(int[] stages) {
        ItemStack s = new ItemStack(ArsenalItems.VOLTAIC_BLADE.get());
        if (stages.length > 0) {
            VoltaicBladeItem.setCapacitorStages(s, stages);
        }
        return s;
    }
}
