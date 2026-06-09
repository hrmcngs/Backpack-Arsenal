package backpackarsenal.client.render;

import backpackarsenal.BackpackArsenalMod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedbackpacks.client.render.BackpackLayerRenderer;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * Arsenal Backpack 用 Curios renderer。
 *
 * SB の {@code BackpackCurioRenderer} と同じく {@link BackpackLayerRenderer#renderBackpack}
 * に丸投げして本体描画した上で、 voltaic_blade 納刀中なら追加で
 * {@link SayaBackpackOverlay#renderSayaForBackpack} を呼んで鞘モデルを重ねる。
 *
 * renderBackpack 自身は pushPose/popPose を内側でやらないため、呼んだ後の poseStack は
 * backpack-local 座標 (Y/Z 180°回転 + 背後オフセット) のままになっている。 saya モデルは
 * その座標系の上に乗せれば backpack とぴったり重なる。 saya JSON は backpack と同じ
 * Blockbench 空間で組まれている前提。
 */
public class ArsenalBackpackCurioRenderer implements ICurioRenderer {

    /** 初回呼び出し時に 1度だけログを出すための flag。 saya が出ない時の原因切り分け用:
     *  - "render() called" のログが出ない → Curios renderer 登録自体が効いていない
     *  - 出るが voltaicCount=0 → 通常スロット判定が拾えていない */
    private static boolean diagLoggedRender = false;

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(
            ItemStack stack,
            SlotContext slotContext,
            PoseStack poseStack,
            RenderLayerParent<T, M> renderLayerParent,
            MultiBufferSource bufferSource,
            int packedLight,
            float limbSwing, float limbSwingAmount,
            float partialTicks, float ageInTicks,
            float netHeadYaw, float headPitch) {

        if (stack.isEmpty()) return;
        EntityModel<T> entityModel = renderLayerParent.getModel();
        if (!(entityModel instanceof HumanoidModel)) return;

        @SuppressWarnings("unchecked")
        HumanoidModel<LivingEntity> humanoidModel = (HumanoidModel<LivingEntity>) entityModel;
        LivingEntity wearer = slotContext.entity();
        boolean wearsChestplate = !wearer.getItemBySlot(EquipmentSlot.CHEST).isEmpty();

        if (!diagLoggedRender) {
            diagLoggedRender = true;
            BackpackArsenalMod.LOGGER.info(
                "[backpack_arsenal] ArsenalBackpackCurioRenderer.render() first invocation: " +
                "stack={}, wearer={}, chestplate={} (voltaic count is checked per-frame in SayaBackpackOverlay)",
                stack.getItem(), wearer.getName().getString(), wearsChestplate);
        }

        poseStack.pushPose();
        try {
            BackpackLayerRenderer.renderBackpack(
                humanoidModel, wearer, poseStack, bufferSource, packedLight, stack, wearsChestplate);

            // renderBackpack 後の poseStack は backpack-local 空間に乗ったまま。
            // ここで saya を同じ space で描画すると backpack に重なる。
            SayaBackpackOverlay.renderSayaForBackpackOnBack(stack, poseStack, bufferSource, packedLight);
        } finally {
            poseStack.popPose();
        }
    }
}
