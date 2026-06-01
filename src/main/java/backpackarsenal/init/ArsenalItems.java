package backpackarsenal.init;

import backpackarsenal.BackpackArsenalMod;
import backpackarsenal.item.ArsenalBackpackItem;
import backpackarsenal.item.VoltaicBladeItem;
import backpackarsenal.upgrade.VoltaicChargerUpgradeItem;
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

    /** Arsenal Backpack (Electron 系) — 充電機能付きの独自グレードバックパック (SB BackpackItem 拡張)。
     *  今後 Fire / Ice 等の variant を追加する想定で suffix を付けている。 */
    public static final RegistryObject<Item> ARSENAL_BACKPACK_ELECTRON =
        REGISTRY.register("arsenal_backpack_electron", ArsenalBackpackItem::new);

    /** Voltaic Charger Upgrade — backpack のアップグレードスロットに挿すと voltaic_blade
     *  の充電速度が枚数だけ倍率アップ (1枚=2倍、2枚=3倍...) */
    public static final RegistryObject<Item> VOLTAIC_CHARGER_UPGRADE =
        REGISTRY.register("voltaic_charger_upgrade", VoltaicChargerUpgradeItem::new);

    /** Basic Backpack — 充電機能なしの普通のバックパック。
     *  SB の BackpackItem をそのまま使う (subclass 無し)。
     *  - 充電スロット無し
     *  - voltaic_blade を入れても充電されない (BackpackScanner で除外される — namespace
     *    が "sophisticatedbackpacks" でも arsenal_backpack でもないので)
     *  - inventory 9 + upgrade 4 (ハードコード、config 連動しない)
     *  - SB 既定の革 backpack block を共用 */
    public static final RegistryObject<Item> BASIC_BACKPACK =
        REGISTRY.register("basic_backpack", () -> new net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem(
            () -> 9,
            () -> 4,
            net.p3pp3rf1y.sophisticatedbackpacks.init.ModBlocks.BACKPACK::get,
            (java.util.function.UnaryOperator<net.minecraft.world.item.Item.Properties>)
                props -> props.rarity(net.minecraft.world.item.Rarity.COMMON).stacksTo(1)
        ));
}
