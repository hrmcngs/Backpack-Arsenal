package backpackarsenal.compat.jei;

import backpackarsenal.BackpackArsenalMod;
import backpackarsenal.init.ArsenalItems;
import backpackarsenal.item.VoltaicBladeItem;
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
