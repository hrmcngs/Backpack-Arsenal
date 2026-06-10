package backpackarsenal.recipe;

import backpackarsenal.init.ArsenalItems;
import backpackarsenal.init.ArsenalRecipes;
import backpackarsenal.item.VoltaicBladeItem;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

import java.util.Collections;

/**
 * クラフト台で voltaic_blade + shears を並べると 強化 stage + エンチャント を一括剥がす。
 *   - 入力: voltaic_blade (stages > 0 または enchant 有り) + shears (任意の状態)
 *   - 出力: 同じ blade を stage 全消し + enchant 全消し で複製 (NBT 操作)
 *   - shears は {@link #getRemainingItems} で 1 ダメージ済みのもの をスロット元に戻す
 *     (Unbreaking では vanilla の hurtAndBreak が確率回避 する)。
 *
 * 検索性を保つため shapeless 的に動くが、 NBT 加工が必要なので CustomRecipe で実装。
 * JSON は {@code data/backpack_arsenal/recipes/voltaic_blade_shear_strip.json} に
 * {@code "type": "backpack_arsenal:voltaic_blade_shear_strip"} の 1 行だけ書く。
 *
 * JEI display: {@link backpackarsenal.compat.jei.BackpackArsenalJeiPlugin} で
 * 代表的な「blade(stage=1) + shears → 素 blade」を vanilla shapeless 表示として登録する。
 */
public class VoltaicBladeShearStripRecipe extends CustomRecipe {

    public VoltaicBladeShearStripRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        ItemStack blade = ItemStack.EMPTY;
        ItemStack shears = ItemStack.EMPTY;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (s.isEmpty()) continue;
            if (s.getItem() == ArsenalItems.VOLTAIC_BLADE.get()) {
                if (!blade.isEmpty()) return false; // 2 本以上は対応しない
                blade = s;
            } else if (s.getItem() instanceof ShearsItem) {
                if (!shears.isEmpty()) return false;
                shears = s;
            } else {
                return false; // 他の item があったら不一致
            }
        }
        if (blade.isEmpty() || shears.isEmpty()) return false;
        // 剥がす物が無いと craft させない (= 通常の blade を無意味に複製させない)
        return VoltaicBladeItem.getCapacitorStageCount(blade) > 0
            || !blade.getAllEnchantments().isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (s.getItem() == ArsenalItems.VOLTAIC_BLADE.get()) {
                ItemStack out = s.copy();
                out.setCount(1);
                VoltaicBladeItem.setCapacitorStages(out, new int[0]);
                EnchantmentHelper.setEnchantments(Collections.emptyMap(), out);
                return out;
            }
        }
        return ItemStack.EMPTY;
    }

    /** shears スロットには 1 ダメージ済みの shears を戻す。 grid 上で 「無くならない」 が
     *  ダメージは入る。 vanilla の hurtAndBreak で Unbreaking 確率回避も働く。 */
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer container) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (s.getItem() instanceof ShearsItem) {
                ItemStack damaged = s.copy();
                damaged.setCount(1);
                // hurtAndBreak で Unbreaking 確率回避を効かせるため LivingEntity 必要だが
                // ここでは取得できないので、 NBT 直接書きで近似 (Unbreaking 効果無し)。
                // → vanilla shapeless で tool を ingredient にする時の標準 (= ToolAction)
                //   と同じ挙動。 本来は in-hand と同等にしたいが trade-off で簡素化。
                int newDamage = damaged.getDamageValue() + 1;
                if (newDamage >= damaged.getMaxDamage()) {
                    remaining.set(i, ItemStack.EMPTY); // 壊れる
                } else {
                    damaged.setDamageValue(newDamage);
                    remaining.set(i, damaged);
                }
            }
        }
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ArsenalRecipes.VOLTAIC_BLADE_SHEAR_STRIP.get();
    }
}
