package backpackarsenal.client.render;

import backpackarsenal.BackpackArsenalMod;
import backpackarsenal.item.ArsenalBackpackItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.p3pp3rf1y.sophisticatedbackpacks.client.ClientEventHandler;

import java.util.Map;

/**
 * Arsenal Backpack を背中に背負っているとき、 backpack の上に「鞘 (saya)」モデルを重ね描画する。
 *
 *   - 通常スロットに voltaic_blade が入っている → 刀入りの鞘 (saya_voltaic_blade)
 *   - 入っていない                            → 空鞘 (saya_voltaic_blade_kara)
 *
 * モデル登録: 上記2つは通常の item/block model ではないので、
 * {@link ModelEvent.RegisterAdditional} で明示的に追加バンドル対象として登録する。
 *
 * バンドル後の BakedModel は {@link ModelEvent.ModifyBakingResult} で
 * キャッシュし、レンダリング毎にカクつかないように static で保持する。
 *
 * 描画は {@link ArsenalBackpackCurioRenderer} が SB の renderBackpack 直後に呼ぶ。
 * その時点で poseStack は backpack-local 座標に乗っているので、saya モデルの
 * JSON 座標 (backpack と同じ Blockbench 空間で設計) のまま描画すれば重なる。
 */
@Mod.EventBusSubscriber(
    modid = BackpackArsenalMod.MODID,
    value = Dist.CLIENT,
    bus = Mod.EventBusSubscriber.Bus.MOD
)
public final class SayaBackpackOverlay {

    /** 刀入りの鞘モデル (内部に voltaic_blade あり) の ResourceLocation。 */
    private static final ResourceLocation SAYA_WITH_BLADE_ID =
        new ResourceLocation(BackpackArsenalMod.MODID, "custom/saya/backpack/saya_voltaic_blade");

    /** 空鞘モデル (voltaic_blade なし) の ResourceLocation。 */
    private static final ResourceLocation SAYA_EMPTY_ID =
        new ResourceLocation(BackpackArsenalMod.MODID, "custom/saya/backpack/saya_voltaic_blade_kara");

    /** BakedModel lookup key。 Forge の ModelLoader は RegisterAdditional で渡された
     *  ResourceLocation を {@code new ModelResourceLocation(rl, "")} に包んでから
     *  bakedRegistry に格納する。 HashMap は hashCode() で bucket を引くので、
     *  plain ResourceLocation で {@code get()} すると hash 不一致で取れない
     *  (ModelResourceLocation.hashCode は variant 分余計に積む)。 */
    private static final ModelResourceLocation SAYA_WITH_BLADE_LOOKUP =
        new ModelResourceLocation(SAYA_WITH_BLADE_ID, "");
    private static final ModelResourceLocation SAYA_EMPTY_LOOKUP =
        new ModelResourceLocation(SAYA_EMPTY_ID, "");

    /** ベイク済み saya モデル。初回 ModifyBakingResult までは null。 */
    private static BakedModel cachedSayaWithBladeModel;
    private static BakedModel cachedSayaEmptyModel;

    private SayaBackpackOverlay() {}

    @SubscribeEvent
    public static void onRegisterAdditional(ModelEvent.RegisterAdditional event) {
        event.register(SAYA_WITH_BLADE_ID);
        event.register(SAYA_EMPTY_ID);
        BackpackArsenalMod.LOGGER.info(
            "[backpack_arsenal] registered additional models for baking: {}, {}",
            SAYA_WITH_BLADE_ID, SAYA_EMPTY_ID);
    }

    @SubscribeEvent
    public static void onModifyBaking(ModelEvent.ModifyBakingResult event) {
        var models = event.getModels();
        cachedSayaWithBladeModel = lookup(models, SAYA_WITH_BLADE_ID, SAYA_WITH_BLADE_LOOKUP, "saya_voltaic_blade");
        cachedSayaEmptyModel     = lookup(models, SAYA_EMPTY_ID,      SAYA_EMPTY_LOOKUP,      "saya_voltaic_blade_kara");
    }

    /** 1モデル分の lookup ロジック。 期待 key 形式 → 線形 scan の 2 段。 */
    private static BakedModel lookup(
            Map<ResourceLocation, BakedModel> models,
            ResourceLocation id,
            ModelResourceLocation primaryKey,
            String label) {
        BakedModel model = models.get(primaryKey);
        if (model == null) {
            for (var entry : models.entrySet()) {
                ResourceLocation k = entry.getKey();
                if (k.getNamespace().equals(id.getNamespace())
                        && k.getPath().equals(id.getPath())) {
                    model = entry.getValue();
                    BackpackArsenalMod.LOGGER.info(
                        "[backpack_arsenal] {} found via fallback scan: stored-key={} (class={})",
                        label, k, k.getClass().getSimpleName());
                    break;
                }
            }
        }
        if (model == null) {
            BackpackArsenalMod.LOGGER.warn(
                "[backpack_arsenal] {} BakedModel not found in {} keys — overlay variant disabled",
                label, models.size());
            return null;
        }
        BackpackArsenalMod.LOGGER.info(
            "[backpack_arsenal] cached {} BakedModel ({})", label, model.getClass().getSimpleName());
        return model;
    }

    /**
     * 装着 backpack のローカル座標に乗った poseStack のまま、 saya モデルを描画する。
     *
     * 描画モデルの選択:
     *   voltaicCount > 0 → 刀入り (saya_voltaic_blade)
     *   voltaicCount == 0 → 空鞘 (saya_voltaic_blade_kara)
     *
     * 装着コンテキスト別ラッパ:
     *   {@link #renderSayaForBackpackOnBack} : SB worn (Curios back)
     *   {@link #renderSayaForBackpackOnChest}: backpack_arsenal:chestplate (バニラ胸防具スロット)
     */
    public static void renderSayaForBackpack(
            ItemStack backpackStack,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            ItemDisplayContext context,
            String contextLabel) {
        if (backpackStack.isEmpty()) {
            logSkipOnce("backpackStack.isEmpty()");
            return;
        }

        int voltaicCount = ArsenalBackpackItem.countVoltaicInRegularSlots(backpackStack);
        BakedModel model = (voltaicCount > 0) ? cachedSayaWithBladeModel : cachedSayaEmptyModel;
        String variantName = (voltaicCount > 0) ? "with-blade" : "empty";

        if (model == null) {
            // Bake event を取り逃した可能性があるので runtime に 1 度だけ lookup
            tryRuntimeLookup();
            model = (voltaicCount > 0) ? cachedSayaWithBladeModel : cachedSayaEmptyModel;
            if (model == null) {
                logSkipOnce(variantName + " model is null (bake failed or lookup miss)");
                return;
            }
        }

        logDrawOnce(contextLabel + "/" + variantName, voltaicCount);

        // chestplate context (= AIOOBE 経路) はカスタム ordinal 越え対策で safe wrapper 経由。
        // worn / その他は ordinal が低い or moddedTransforms に必ず entry があるので素のまま。
        BakedModel renderModel = (context == backpackarsenal.BackpackArsenalMod.CHESTPLATE_CONTEXT)
            ? new SafeTransformBakedModel(model)
            : model;
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        itemRenderer.render(
            backpackStack,
            context,
            false,
            poseStack,
            bufferSource,
            packedLight,
            OverlayTexture.NO_OVERLAY,
            renderModel
        );
    }

    /** Curios back 装備時 (SB BackpackLayerRenderer.renderBackpack 直後) の saya 描画。
     *  saya JSON の {@code display.sophisticatedbackpacks:worn} が適用される。 */
    public static void renderSayaForBackpackOnBack(
            ItemStack backpackStack,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight) {
        renderSayaForBackpack(backpackStack, poseStack, bufferSource, packedLight,
            ClientEventHandler.WORN, "back");
    }

    /** バニラチェスト防具スロット装備時の saya 描画。
     *  saya JSON の {@code display.backpack_arsenal:chestplate} が適用される。
     *  context は {@link ArsenalBackpackBlockEntityRenderer} と並びの
     *  {@link backpackarsenal.client.render.ChestBackpackLayer#CHESTPLATE_CONTEXT}。 */
    public static void renderSayaForBackpackOnChest(
            ItemStack backpackStack,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            ItemDisplayContext chestContext) {
        renderSayaForBackpack(backpackStack, poseStack, bufferSource, packedLight,
            chestContext, "chest");
    }

    private static String lastSkipReason = null;
    private static String lastDrawVariant = null;
    private static boolean runtimeLookupAttempted = false;

    /** Bake event を取り逃した時の runtime fallback。 ModelManager から直接引く。
     *  成功/失敗どちらでも 1 度だけ試行する (= 毎フレーム呼び出されても重くない)。 */
    private static void tryRuntimeLookup() {
        if (runtimeLookupAttempted) return;
        runtimeLookupAttempted = true;
        try {
            var mm = Minecraft.getInstance().getModelManager();
            if (cachedSayaWithBladeModel == null) {
                BakedModel m = mm.getModel(SAYA_WITH_BLADE_LOOKUP);
                if (m != null && m != mm.getMissingModel()) {
                    cachedSayaWithBladeModel = m;
                    BackpackArsenalMod.LOGGER.info(
                        "[backpack_arsenal] saya_voltaic_blade recovered at runtime ({})",
                        m.getClass().getSimpleName());
                } else {
                    BackpackArsenalMod.LOGGER.warn(
                        "[backpack_arsenal] saya_voltaic_blade runtime lookup failed (got {})",
                        m == null ? "null" : "missingModel");
                }
            }
            if (cachedSayaEmptyModel == null) {
                BakedModel m = mm.getModel(SAYA_EMPTY_LOOKUP);
                if (m != null && m != mm.getMissingModel()) {
                    cachedSayaEmptyModel = m;
                    BackpackArsenalMod.LOGGER.info(
                        "[backpack_arsenal] saya_voltaic_blade_kara recovered at runtime ({})",
                        m.getClass().getSimpleName());
                } else {
                    BackpackArsenalMod.LOGGER.warn(
                        "[backpack_arsenal] saya_voltaic_blade_kara runtime lookup failed (got {})",
                        m == null ? "null" : "missingModel");
                }
            }
        } catch (Throwable t) {
            BackpackArsenalMod.LOGGER.error(
                "[backpack_arsenal] saya runtime lookup threw: {}", t.toString());
        }
    }

    /** スキップ理由が変わった時だけ log。毎フレーム同じ理由を吐いても spam するだけなので
     *  前回と同じ理由なら抑制する。 */
    private static void logSkipOnce(String reason) {
        if (!reason.equals(lastSkipReason)) {
            BackpackArsenalMod.LOGGER.info("[backpack_arsenal] saya overlay skipped: {}", reason);
            lastSkipReason = reason;
        }
    }

    /** variant が切り替わった時だけ log。 with-blade ↔ empty の遷移を 1 回だけ記録。 */
    private static void logDrawOnce(String variantName, int voltaicCount) {
        if (!variantName.equals(lastDrawVariant)) {
            BackpackArsenalMod.LOGGER.info(
                "[backpack_arsenal] saya overlay drawing variant={} (voltaicCount={})",
                variantName, voltaicCount);
            lastDrawVariant = variantName;
            lastSkipReason = null;
        }
    }
}
