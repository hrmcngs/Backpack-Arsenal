package backpackarsenal.init;

import backpackarsenal.BackpackArsenalMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Backpack Arsenal 専用のクリエイティブタブ。
 * 追加した 3 アイテム (voltaic_blade / arsenal_backpack / voltaic_charger_upgrade) を
 * 1 つのタブに集約。
 *
 * 1.20.1 ではタブに登録しないと creative search / JEI の両方で出てこないので必須。
 */
public class ArsenalCreativeTab {

    public static final DeferredRegister<CreativeModeTab> REGISTRY =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BackpackArsenalMod.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN_TAB = REGISTRY.register(
        "main",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.backpack_arsenal.main"))
            .icon(() -> new ItemStack(ArsenalItems.ARSENAL_BACKPACK.get()))
            .displayItems((params, output) -> {
                output.accept(ArsenalItems.VOLTAIC_BLADE.get());
                output.accept(ArsenalItems.ARSENAL_BACKPACK.get());
                output.accept(ArsenalItems.BASIC_BACKPACK.get());
                output.accept(ArsenalItems.VOLTAIC_CHARGER_UPGRADE.get());
            })
            .build()
    );
}
