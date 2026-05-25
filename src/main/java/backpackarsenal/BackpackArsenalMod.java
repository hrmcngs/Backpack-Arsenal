package backpackarsenal;

import backpackarsenal.init.ArsenalItems;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Backpack Arsenal — メインクラス
 *
 * Sophisticated Backpacks を「鞘 (saya)」として扱い、収納中の武器を徐々に充電する
 * "The four primitives and Weapons" アドオン。
 *
 * - 武器は VoltaicBlade 1種類のみ（通常の刀より少し短く、少し速い小太刀寄り）
 * - 充電は ItemStack の NBT "BackpackCharge" (int) に保存
 * - 充電量に応じて MAW の ElementType / ElementLevel を自動付与し、
 *   本体MOD の ElementalDamageEvent により電気属性ダメージが発生する。
 */
@Mod(BackpackArsenalMod.MODID)
public class BackpackArsenalMod {

    public static final String MODID = "backpack_arsenal";
    public static final Logger LOGGER = LogManager.getLogger(BackpackArsenalMod.class);

    public BackpackArsenalMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();

        ArsenalItems.REGISTRY.register(bus);

        LOGGER.info("[{}] Loaded — backpacks now charge MAW weapons.", MODID);
    }
}
