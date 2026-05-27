package backpackarsenal.init;

import backpackarsenal.BackpackArsenalMod;
import backpackarsenal.inventory.ArsenalBackpackContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * MenuType レジストリ。Arsenal Backpack 用の独自 MenuType を登録する。
 * SB の MenuType をそのまま使うと client 側で SB factory が走り extraSlot が
 * 消えるので、自前 MenuType を持つ必要がある (ArsenalBackpackContainer 参照)。
 */
public class ArsenalMenuTypes {

    public static final DeferredRegister<MenuType<?>> REGISTRY =
        DeferredRegister.create(ForgeRegistries.MENU_TYPES, BackpackArsenalMod.MODID);

    public static final RegistryObject<MenuType<ArsenalBackpackContainer>> ARSENAL_BACKPACK_MENU =
        REGISTRY.register(
            "arsenal_backpack",
            () -> IForgeMenuType.create(ArsenalBackpackContainer::fromBuffer)
        );
}
