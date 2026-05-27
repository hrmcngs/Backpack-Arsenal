package backpackarsenal;

import backpackarsenal.init.ArsenalItems;
import backpackarsenal.network.BackpackArsenalNetwork;
import backpackarsenal.skill.SlamDownSkillAction;
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

        ArsenalItems.REGISTRY.register(modBus);
        backpackarsenal.init.ArsenalMenuTypes.REGISTRY.register(modBus);
        modBus.addListener(this::onCommonSetup);
        modBus.addListener(this::onClientSetup);

        LOGGER.info("[{}] Loaded — backpacks now charge MAW weapons.", MODID);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            BackpackArsenalNetwork.register();
            registerSkills();
        });
    }

    /** Screen を MenuType に紐付ける (client のみ)。
     *  BackpackScreen の generic は BackpackContainer (parent) 固定なので、
     *  generic 不一致を raw cast で回避する。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void onClientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            net.minecraft.client.gui.screens.MenuScreens.register(
                (net.minecraft.world.inventory.MenuType)
                    backpackarsenal.init.ArsenalMenuTypes.ARSENAL_BACKPACK_MENU.get(),
                (net.minecraft.client.gui.screens.MenuScreens.ScreenConstructor)
                    (menu, inv, title) -> new backpackarsenal.client.ArsenalBackpackScreen(
                        (backpackarsenal.inventory.ArsenalBackpackContainer) menu, inv, title)
            );
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
        SkillRegistry.register(
            "voltaic_slam_down",
            "雷振り下ろし",
            "充電に応じて威力が上がる前方AOE叩きつけ。Voltaic Blade 専用。充電があれば 400 消費して雷ダメージ追加。",
            SkillRegistry.MotionCategory.SPECIAL,
            EnumSet.of(
                PlayerSkillData.AttackSlot.FIRST_HIT,
                PlayerSkillData.AttackSlot.SECOND_HIT,
                PlayerSkillData.AttackSlot.THIRD_HIT,
                PlayerSkillData.AttackSlot.CHARGED
            ),
            backpackarsenal.item.VoltaicBladeItem.class.getSimpleName(),
            new SlamDownSkillAction()
        );
        LOGGER.info("[{}] Registered MAW motion 'voltaic_slam_down' (雷振り下ろし) for VoltaicBladeItem", MODID);
    }
}
