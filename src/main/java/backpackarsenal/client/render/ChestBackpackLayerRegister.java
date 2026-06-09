package backpackarsenal.client.render;

import backpackarsenal.BackpackArsenalMod;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Player の Renderer (両 skin variant: default / slim) に {@link ChestBackpackLayer} を
 * 追加するためのイベント購読クラス。
 *
 * {@link EntityRenderersEvent.AddLayers} は client setup の途中で MOD bus に発火する。
 * このタイミングで Player renderer の layer list を加工しないと、 in-game レンダリング
 * 時に layer が存在せず描画されない。
 */
@Mod.EventBusSubscriber(
    modid = BackpackArsenalMod.MODID,
    value = Dist.CLIENT,
    bus = Mod.EventBusSubscriber.Bus.MOD
)
public final class ChestBackpackLayerRegister {

    private ChestBackpackLayerRegister() {}

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        int added = 0;
        // default skin (steve)
        if (addPlayerLayer(event, "default")) added++;
        // slim skin (alex)
        if (addPlayerLayer(event, "slim")) added++;
        BackpackArsenalMod.LOGGER.info(
            "[backpack_arsenal] ChestBackpackLayer attached to {} player renderer variant(s)", added);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean addPlayerLayer(EntityRenderersEvent.AddLayers event, String skin) {
        var renderer = event.getSkin(skin);
        if (!(renderer instanceof PlayerRenderer playerRenderer)) {
            BackpackArsenalMod.LOGGER.warn(
                "[backpack_arsenal] skin '{}' renderer is null or not PlayerRenderer; skipping", skin);
            return false;
        }
        // playerRenderer は LivingEntityRenderer<AbstractClientPlayer, PlayerModel<...>>
        // ChestBackpackLayer は <T extends AbstractClientPlayer, M extends HumanoidModel<T>>
        // PlayerModel は HumanoidModel を継承しているので raw cast で代入可能。
        LivingEntityRenderer parent = playerRenderer;
        ChestBackpackLayer layer = new ChestBackpackLayer(parent);
        playerRenderer.addLayer(layer);
        return true;
    }
}
