package backpackarsenal.init;

import backpackarsenal.BackpackArsenalMod;
import backpackarsenal.recipe.VoltaicBladeShearStripRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 独自 Recipe Serializer の登録。 今は
 * {@link VoltaicBladeShearStripRecipe} (blade + shears で全段階 + エンチャント剥がし) のみ。
 *
 * DeferredRegister は {@link BackpackArsenalMod} の ctor で mod bus に attach する。
 */
public class ArsenalRecipes {

    public static final DeferredRegister<RecipeSerializer<?>> REGISTRY =
        DeferredRegister.create(Registries.RECIPE_SERIALIZER, BackpackArsenalMod.MODID);

    /** voltaic_blade + shears → stripped blade。 JSON 側 {@code "type": "backpack_arsenal:voltaic_blade_shear_strip"}。 */
    public static final RegistryObject<SimpleCraftingRecipeSerializer<VoltaicBladeShearStripRecipe>>
        VOLTAIC_BLADE_SHEAR_STRIP =
            REGISTRY.register("voltaic_blade_shear_strip",
                () -> new SimpleCraftingRecipeSerializer<>(VoltaicBladeShearStripRecipe::new));
}
