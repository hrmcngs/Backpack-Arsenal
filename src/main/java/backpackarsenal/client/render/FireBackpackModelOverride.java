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

/** Registers the non-item loaded model and wires it to the Fire backpack item. */
@Mod.EventBusSubscriber(modid = BackpackArsenalMod.MODID, value = Dist.CLIENT,
    bus = Mod.EventBusSubscriber.Bus.MOD)
public class FireBackpackModelOverride {
    private static final ResourceLocation LOADED =
        new ResourceLocation(BackpackArsenalMod.MODID, "item/arsenal_backpack_fire_loaded");

    @SubscribeEvent
    public static void registerAdditional(ModelEvent.RegisterAdditional event) {
        event.register(LOADED);
    }

    @SubscribeEvent
    public static void modifyModels(ModelEvent.ModifyBakingResult event) {
        Map<ResourceLocation, BakedModel> models = event.getModels();
        ModelResourceLocation itemKey = new ModelResourceLocation(
            BackpackArsenalMod.MODID, "arsenal_backpack_fire", "inventory");
        BakedModel empty = models.get(itemKey);
        BakedModel loaded = models.get(LOADED);
        if (empty == null || loaded == null) {
            BackpackArsenalMod.LOGGER.warn("Fire backpack model switch unavailable: empty={}, loaded={}",
                empty != null, loaded != null);
            return;
        }
        models.put(itemKey, new FireBackpackBakedModel(empty, loaded));
    }
}
