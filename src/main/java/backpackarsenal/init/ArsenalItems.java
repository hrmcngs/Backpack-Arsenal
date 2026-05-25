package backpackarsenal.init;

import backpackarsenal.BackpackArsenalMod;
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

    /** Voltaic Blade — Sophisticated Backpacks 内で充電できる電気属性の小太刀 */
    public static final RegistryObject<Item> VOLTAIC_BLADE =
        REGISTRY.register("voltaic_blade", VoltaicBladeItem::new);
}
