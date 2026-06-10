package backpackarsenal.client.render;

import backpackarsenal.BackpackArsenalMod;
import backpackarsenal.init.ArsenalItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * バニラのチェスト防具スロット (EquipmentSlot.CHEST) に ArsenalBackpack を装備した時、
 * プレイヤーの胸に backpack 本体 + saya を描画する RenderLayer。
 *
 *   1. {@link AbstractClientPlayer} の chest slot を毎フレーム見る
 *   2. ArsenalBackpack が入っていれば、 body の前方に backpack 本体を描画
 *      (display context = {@link #CHESTPLATE_CONTEXT})
 *   3. 続けて {@link SayaBackpackOverlay#renderSayaForBackpackOnChest} で saya を重ねる
 *
 * Curios back の {@link ArsenalBackpackCurioRenderer} と独立した経路。 両方の装備を
 * 同時に行うと両方表示される (前後で重ならないので破綻はしない)。
 *
 * 登録は {@code EntityRenderersEvent.AddLayers} 経由 ({@link backpackarsenal.client.render.ChestBackpackLayerRegister})。
 */
public class ChestBackpackLayer<T extends AbstractClientPlayer, M extends HumanoidModel<T>>
        extends RenderLayer<T, M> {

    /** バニラチェスト防具スロット装着時の display context。
     *  JSON 側で {@code display.backpack_arsenal:chestplate} を編集すると反映される。
     *  fallback は FIXED ({@link BackpackArsenalMod#CHESTPLATE_CONTEXT} の create() 第 3 引数)。
     *
     *  ⚠ 過去 AIOOBE で FIXED 直流用していたが、 {@link SafeTransformBakedModel} で
     *  applyTransform を override して moddedTransforms を直接読むことで安全化。 */
    public static final ItemDisplayContext CHESTPLATE_CONTEXT = BackpackArsenalMod.CHESTPLATE_CONTEXT;

    /** 初回描画ログ用フラグ。 RenderLayer が想定通り呼ばれているか切り分けるため。 */
    private static boolean diagLogged = false;

    public ChestBackpackLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack,
                       MultiBufferSource bufferSource,
                       int packedLight,
                       T player,
                       float limbSwing, float limbSwingAmount,
                       float partialTicks, float ageInTicks,
                       float netHeadYaw, float headPitch) {

        ItemStack chestStack = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chestStack.isEmpty()) return;
        if (chestStack.getItem() != ArsenalItems.ARSENAL_BACKPACK_ELECTRON.get()) return;

        if (!diagLogged) {
            diagLogged = true;
            BackpackArsenalMod.LOGGER.info(
                "[backpack_arsenal] ChestBackpackLayer first render: player={}, chestStack={}",
                player.getName().getString(), chestStack.getItem());
        }

        M model = getParentModel();
        poseStack.pushPose();
        try {
            // body の translateAndRotate でプレイヤーの胴体に追従。 body 正面 (z+) が
            // プレイヤーの胸方向。 模型座標 (16-unit pixel) と一致させるため Y/Z 反転と
            // 縮小は display.backpack_arsenal:chestplate 側 (scale 0.7 等) で吸収する。
            model.body.translateAndRotate(poseStack);
            // Y軸 180° 回転で「正面」を一旦 -Z 方向 (= プレイヤーの背中) に向ける、
            // その後 Z軸 180° 回転で上下も flip → 結果としてアイテムフレーム的な、
            // model の +Z 面 = プレイヤーの胸前方を向く配置になる。
            // (SB BackpackLayerRenderer.translateRotateAndScale と同じ慣習)
            poseStack.mulPose(Axis.YP.rotationDegrees(180f));
            poseStack.mulPose(Axis.ZP.rotationDegrees(180f));
            // body 正面に少し前へ押し出す。 値は display 側で再調整できるが、 体に
            // 完全埋没を防ぐ最低限の前進を入れておく。
            poseStack.translate(0.0, -0.25, -0.32);

            // backpack 本体描画 (SafeTransformBakedModel で wrap して AIOOBE 完全回避)
            var bakedModel = Minecraft.getInstance().getItemRenderer().getModel(
                chestStack, player.level(), player, 0);
            Minecraft.getInstance().getItemRenderer().render(
                chestStack,
                CHESTPLATE_CONTEXT,
                false,
                poseStack,
                bufferSource,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                new SafeTransformBakedModel(bakedModel)
            );

            // saya overlay (同じ poseStack のまま、 saya 用 chestplate display 適用)
            SayaBackpackOverlay.renderSayaForBackpackOnChest(
                chestStack, poseStack, bufferSource, packedLight, CHESTPLATE_CONTEXT);
        } finally {
            poseStack.popPose();
        }
    }
}
