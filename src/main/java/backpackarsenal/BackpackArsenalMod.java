package backpackarsenal;

import backpackarsenal.init.ArsenalItems;
import backpackarsenal.network.BackpackArsenalNetwork;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
        modBus.addListener(this::onCommonSetup);

        LOGGER.info("[{}] Loaded — backpacks now charge MAW weapons.", MODID);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(BackpackArsenalNetwork::register);
    }
}
