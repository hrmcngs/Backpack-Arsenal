package backpackarsenal.client.render;

import backpackarsenal.BackpackArsenalMod;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

/**
 * ModelEvent.ModifyBakingResult で voltaic_blade の inventory BakedModel を
 * {@link VoltaicBladeBakedModel} (= custom glint 付き) に差し替える。
 *
 * これにより vanilla 経路を通って item rendering されたとき、 base + voltaic_glint
 * の 2 段で描画される。 vanilla glint は VoltaicBladeItem.isFoil=false で抑制済み。
 */
@Mod.EventBusSubscriber(
    modid = BackpackArsenalMod.MODID,
    value = Dist.CLIENT,
    bus = Mod.EventBusSubscriber.Bus.MOD
)
public class VoltaicBladeModelOverride {

    @SubscribeEvent
    public static void onModifyBaking(ModelEvent.ModifyBakingResult event) {
        Map<ResourceLocation, BakedModel> models = event.getModels();
        ModelResourceLocation key = new ModelResourceLocation(
            BackpackArsenalMod.MODID, "voltaic_blade", "inventory");
        BakedModel original = models.get(key);
        if (original == null) {
            BackpackArsenalMod.LOGGER.warn(
                "[backpack_arsenal] voltaic_blade base model not found at {} — glint override skipped",
                key);
            return;
        }
        models.put(key, new VoltaicBladeBakedModel(original));
        BackpackArsenalMod.LOGGER.info(
            "[backpack_arsenal] wrapped voltaic_blade BakedModel with custom vertical glint");
    }
}
