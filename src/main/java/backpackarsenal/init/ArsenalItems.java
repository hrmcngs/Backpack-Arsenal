package backpackarsenal.init;

import backpackarsenal.BackpackArsenalMod;
import backpackarsenal.item.ArsenalBackpackItem;
import backpackarsenal.item.VoltaicBladeItem;
import backpackarsenal.item.VoltaicCapacitorUpgradeItem;
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

    /** Voltaic Charger Upgrade tier 0 (base). 寄与 +1 (1枚で multiplier=2)。
     *  上位 tier は {@link #VOLTAIC_CHARGER_UPGRADE_TIER_1} 〜 {@link #VOLTAIC_CHARGER_UPGRADE_TIER_4}。 */
    public static final RegistryObject<VoltaicChargerUpgradeItem> VOLTAIC_CHARGER_UPGRADE =
        REGISTRY.register("voltaic_charger_upgrade", () -> new VoltaicChargerUpgradeItem(0));
    public static final RegistryObject<VoltaicChargerUpgradeItem> VOLTAIC_CHARGER_UPGRADE_TIER_1 =
        REGISTRY.register("voltaic_charger_upgrade_tier_1", () -> new VoltaicChargerUpgradeItem(1));
    public static final RegistryObject<VoltaicChargerUpgradeItem> VOLTAIC_CHARGER_UPGRADE_TIER_2 =
        REGISTRY.register("voltaic_charger_upgrade_tier_2", () -> new VoltaicChargerUpgradeItem(2));
    public static final RegistryObject<VoltaicChargerUpgradeItem> VOLTAIC_CHARGER_UPGRADE_TIER_3 =
        REGISTRY.register("voltaic_charger_upgrade_tier_3", () -> new VoltaicChargerUpgradeItem(3));
    public static final RegistryObject<VoltaicChargerUpgradeItem> VOLTAIC_CHARGER_UPGRADE_TIER_4 =
        REGISTRY.register("voltaic_charger_upgrade_tier_4", () -> new VoltaicChargerUpgradeItem(4));
    public static final RegistryObject<VoltaicChargerUpgradeItem> VOLTAIC_CHARGER_UPGRADE_TIER_5 =
        REGISTRY.register("voltaic_charger_upgrade_tier_5", () -> new VoltaicChargerUpgradeItem(5));

    /** Voltaic Capacitor Upgrade tier I (+256/use)。 一番安価。 */
    public static final RegistryObject<VoltaicCapacitorUpgradeItem> VOLTAIC_CAPACITOR_UPGRADE_I =
        REGISTRY.register("voltaic_capacitor_upgrade_i",
            () -> new VoltaicCapacitorUpgradeItem(backpackarsenal.item.VoltaicBladeItem.CAPACITOR_TIER_I_BONUS));
    /** Voltaic Capacitor Upgrade tier II (+512/use)。 */
    public static final RegistryObject<VoltaicCapacitorUpgradeItem> VOLTAIC_CAPACITOR_UPGRADE_II =
        REGISTRY.register("voltaic_capacitor_upgrade_ii",
            () -> new VoltaicCapacitorUpgradeItem(backpackarsenal.item.VoltaicBladeItem.CAPACITOR_TIER_II_BONUS));
    /** Voltaic Capacitor Upgrade tier III (+1024/use)。 strongest。 既存 ID を維持 (1.0.0 から残ってる物との互換)。 */
    public static final RegistryObject<VoltaicCapacitorUpgradeItem> VOLTAIC_CAPACITOR_UPGRADE =
        REGISTRY.register("voltaic_capacitor_upgrade",
            () -> new VoltaicCapacitorUpgradeItem(backpackarsenal.item.VoltaicBladeItem.CAPACITOR_TIER_III_BONUS));

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
