package backpackarsenal.init;

import backpackarsenal.BackpackArsenalMod;
import backpackarsenal.item.ArsenalBackpackItem;
import backpackarsenal.item.VoltaicBladeItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * アドオンのアイテム登録。
 */
public class ArsenalItems {

    public static final DeferredRegister<Item> REGISTRY =
        DeferredRegister.create(ForgeRegistries.ITEMS, BackpackArsenalMod.MODID);

    /** Voltaic Blade — Sophisticated Backpacks 内で充電できる電気属性の刀 */
    public static final RegistryObject<Item> VOLTAIC_BLADE =
        REGISTRY.register("voltaic_blade", VoltaicBladeItem::new);

    /** Arsenal Backpack — 充電機能付きの独自グレードバックパック (SB BackpackItem 拡張) */
    public static final RegistryObject<Item> ARSENAL_BACKPACK =
        REGISTRY.register("arsenal_backpack", ArsenalBackpackItem::new);
}
