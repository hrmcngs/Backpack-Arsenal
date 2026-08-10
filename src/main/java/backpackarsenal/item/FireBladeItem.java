package backpackarsenal.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;

/** Soldier 11-style blade. Mekanism gas storage and flame attacks are added next. */
public class FireBladeItem extends SwordItem {
    public FireBladeItem() {
        super(new Tier() {
            public int getUses() { return 0; }
            public float getSpeed() { return 4.0F; }
            public float getAttackDamageBonus() { return 4.0F; }
            public int getLevel() { return 2; }
            public int getEnchantmentValue() { return 14; }
            public Ingredient getRepairIngredient() { return Ingredient.of(); }
        }, 3, -2.1F, new Item.Properties().rarity(Rarity.RARE).stacksTo(1));
    }
}
