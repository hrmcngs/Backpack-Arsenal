package backpackarsenal;

import backpackarsenal.init.ArsenalItems;
import backpackarsenal.network.BackpackArsenalNetwork;
import backpackarsenal.skill.SlamDownSkillAction;
import backpackarsenal.skill.VoltaicDodgeSkillAction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import the_four_primitives_and_weapons.skill.PlayerSkillData;
import the_four_primitives_and_weapons.skill.SkillRegistry;

import java.util.EnumSet;

/**
 * Backpack Arsenal — メインクラス
 *
 * Sophisticated Backpacks を「鞘 (saya)」として扱い、収納中の武器を徐々に充電する
 * "The four primitives and Weapons" アドオン。
 *
 * - 武器は VoltaicBlade 1種類のみ（登録上は刀、挙動上は通常の刀より少し速い）
 * - 充電は ItemStack の NBT "BackpackCharge" (int) に保存
 * - 充電量に応じて MAW の ElementType / ElementLevel を自動付与し、
 *   本体MOD の ElementalDamageEvent により電気属性ダメージが発生する。
 * - 例外特技: Sneak + 右クリック で雷叩きつけ (VoltaicBladeItem#use)
 * - 単押し抜刀 (MAW の R キー) を本体より先にフックし、Sophisticated Backpack
 *   内の voltaic_blade を優先で引き抜く (BackpackDrawClient + BackpackArsenalNetwork)
 */
@Mod(BackpackArsenalMod.MODID)
public class BackpackArsenalMod {

    public static final String MODID = "backpack_arsenal";
    public static final Logger LOGGER = LogManager.getLogger(BackpackArsenalMod.class);

    public BackpackArsenalMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Config を最初に読み込む (Item 登録より前) — Item の lambda が config 値を参照するため
        backpackarsenal.init.ArsenalBackpackConfig.load();

        ArsenalItems.REGISTRY.register(modBus);
        backpackarsenal.init.ArsenalBlocks.REGISTRY.register(modBus);
        backpackarsenal.init.ArsenalMenuTypes.REGISTRY.register(modBus);
        backpackarsenal.init.ArsenalCreativeTab.REGISTRY.register(modBus);
        modBus.addListener(this::onCommonSetup);
        modBus.addListener(this::onClientSetup);

        // /backpack_arsenal reload コマンド登録
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
            backpackarsenal.command.ArsenalReloadCommand.class);

        LOGGER.info("[{}] Loaded — backpacks now charge MAW weapons.", MODID);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            BackpackArsenalNetwork.register();
            registerSkills();
            injectBlockIntoBackpackTileType();
        });
    }

    /**
     * SB の {@code BackpackBlockEntityType.validBlocks} に独自ブロックを reflection で追加。
     *
     * これがないと、設置したブロックに BackpackBlockEntity を attach できず、置いた瞬間に
     * 「無効な BlockEntity」として剥がれる/動作しない。
     *
     * {@code BlockEntityType.validBlocks} は {@code ImmutableSet<Block>} なので、
     * 新規 ImmutableSet を作って差し替える。Mojang / SRG 両 mapping にフォールバック。
     */
    private void injectBlockIntoBackpackTileType() {
        try {
            net.minecraft.world.level.block.entity.BlockEntityType<?> beType =
                net.p3pp3rf1y.sophisticatedbackpacks.init.ModBlocks.BACKPACK_TILE_TYPE.get();
            net.minecraft.world.level.block.Block ourBlock =
                backpackarsenal.init.ArsenalBlocks.ARSENAL_BACKPACK_ELECTRON_BLOCK.get();

            java.lang.reflect.Field validBlocksField;
            try {
                validBlocksField = net.minecraft.world.level.block.entity.BlockEntityType.class
                    .getDeclaredField("validBlocks");
            } catch (NoSuchFieldException e) {
                // SRG (production) fallback
                validBlocksField = net.minecraft.world.level.block.entity.BlockEntityType.class
                    .getDeclaredField("f_58915_");
            }
            validBlocksField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Set<net.minecraft.world.level.block.Block> oldValid =
                (java.util.Set<net.minecraft.world.level.block.Block>) validBlocksField.get(beType);
            java.util.Set<net.minecraft.world.level.block.Block> newValid =
                new java.util.HashSet<>(oldValid);
            newValid.add(ourBlock);
            validBlocksField.set(beType, java.util.Set.copyOf(newValid));
            LOGGER.info("[{}] Injected {} into BackpackBlockEntityType.validBlocks (now {} entries)",
                MODID, ourBlock, newValid.size());
        } catch (Throwable t) {
            LOGGER.error(
                "[{}] Failed to inject custom block into BackpackBlockEntityType — placed block won't function as backpack",
                MODID, t);
        }
    }

    /** Screen を MenuType に紐付ける (client のみ)。
     *  BackpackScreen の generic は BackpackContainer (parent) 固定なので、
     *  generic 不一致を raw cast で回避する。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void onClientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("[{}] onClientSetup: registering MenuScreens for {}",
                MODID, backpackarsenal.init.ArsenalMenuTypes.ARSENAL_BACKPACK_MENU.get());
            net.minecraft.client.gui.screens.MenuScreens.register(
                (net.minecraft.world.inventory.MenuType)
                    backpackarsenal.init.ArsenalMenuTypes.ARSENAL_BACKPACK_MENU.get(),
                (net.minecraft.client.gui.screens.MenuScreens.ScreenConstructor)
                    (menu, inv, title) -> {
                        LOGGER.info("[{}] ScreenConstructor invoked: menu class={}",
                            MODID, menu.getClass().getName());
                        return new backpackarsenal.client.ArsenalBackpackScreen(
                            (backpackarsenal.inventory.ArsenalBackpackContainer) menu, inv, title);
                    }
            );
            LOGGER.info("[{}] onClientSetup: MenuScreens.register completed", MODID);

            // Curios "back" 装備時の身体貼付き描画のため、SB の BackpackCurioRenderer を
            // arsenal_backpack 用に登録する。SB は CuriosCompat.onAttachCapabilities で
            // namespace="sophisticatedbackpacks" の item のみに renderer を attach するので、
            // 我々の namespace="backpack_arsenal" には自分で登録する必要がある。
            // renderer は SB の BackpackLayerRenderer.renderBackpack を呼び出すので、
            // 我々の JSON BakedModel が WORN context で描画される。
            //
            // try-catch でラップ: Curios API のバージョン不整合や SB 内部 class へのアクセス
            // 失敗が起きても MenuScreens 登録 (上記) はもう完了済みなので、右クリック GUI は
            // 影響を受けない。
            try {
                top.theillusivec4.curios.api.client.CuriosRendererRegistry.register(
                    backpackarsenal.init.ArsenalItems.ARSENAL_BACKPACK_ELECTRON.get(),
                    net.p3pp3rf1y.sophisticatedbackpacks.compat.curios.BackpackCurioRenderer::new
                );
                top.theillusivec4.curios.api.client.CuriosRendererRegistry.register(
                    backpackarsenal.init.ArsenalItems.BASIC_BACKPACK.get(),
                    net.p3pp3rf1y.sophisticatedbackpacks.compat.curios.BackpackCurioRenderer::new
                );
            } catch (Throwable t) {
                LOGGER.error("[{}] Failed to register Curios renderer for backpacks: {}",
                    MODID, t.toString());
            }
        });
    }

    /**
     * MAW の SkillRegistry に「雷振り下ろし」モーションを登録する。
     *
     * MAW 本体に既に "slam_down" モーションが存在する (sword/greatsword 用) ため、
     * 衝突を避けて独自 ID "voltaic_slam_down" で登録する。
     *
     * 互換スロット: 1st/2nd/3rd Hit / Charged (4種) のみ。
     *   Shift+Right-click は MAW 既定の Guard 専用 (use() ハンドラも撤去済み)。
     *
     * 必須武器クラス: VoltaicBladeItem (Java クラス simple name)
     *   ※ MAW の validate は loadout.getWeaponClass() (= item.getClass().getSimpleName())
     *     と motion.requiredWeaponClass を equals() で比較する。
     */
    private void registerSkills() {
        String weaponClass = backpackarsenal.item.VoltaicBladeItem.class.getSimpleName();

        SkillRegistry.register(
            "voltaic_slam_down",
            "雷振り下ろし",
            "充電に応じて威力が上がる前方AOE叩きつけ。Voltaic Blade 専用。充電あり時は AOE 中心へ模擬落雷も降ろす。",
            SkillRegistry.MotionCategory.SPECIAL,
            EnumSet.of(
                PlayerSkillData.AttackSlot.FIRST_HIT,
                PlayerSkillData.AttackSlot.SECOND_HIT,
                PlayerSkillData.AttackSlot.THIRD_HIT,
                PlayerSkillData.AttackSlot.CHARGED
            ),
            weaponClass,
            new SlamDownSkillAction()
        );

        SkillRegistry.register(
            "voltaic_dodge",
            "雷影回避",
            "後方へクイックダッシュ + 1秒間無敵 + 電気スパーク。Voltaic Blade 専用 (right-click スロットに割当)。",
            SkillRegistry.MotionCategory.SPECIAL,
            EnumSet.of(PlayerSkillData.AttackSlot.RIGHT_CLICK),
            weaponClass,
            new VoltaicDodgeSkillAction()
        );

        LOGGER.info("[{}] Registered MAW motions: voltaic_slam_down, voltaic_dodge (VoltaicBladeItem)", MODID);
    }
}
