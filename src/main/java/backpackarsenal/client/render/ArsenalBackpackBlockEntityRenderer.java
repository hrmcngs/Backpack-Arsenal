package backpackarsenal.client.render;

import backpackarsenal.BackpackArsenalMod;
import backpackarsenal.init.ArsenalBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackBlock;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackBlockEntity;
import net.p3pp3rf1y.sophisticatedbackpacks.client.render.BackpackBlockEntityRenderer;

/**
 * Arsenal Backpack 系ブロック (= {@code arsenal_backpack_electron}) 設置時の
 * 専用 BlockEntityRenderer。
 *
 * 仕組み:
 *   - SB の {@link BackpackBlockEntityRenderer} は全 backpack ブロックを
 *     {@link ItemDisplayContext#FIXED} で描画する。 そのままだとアイテム
 *     フレーム表示と placed block 表示が同じ display 値で連動してしまう。
 *   - 我々は独自の {@link #PLACED_CONTEXT} (= "backpack_arsenal:placed",
 *     fallback FIXED) を作り、 BA のブロックだけこの context で描画する。
 *   - これで JSON 側の {@code display."backpack_arsenal:placed"} を編集すれば
 *     placed block の見た目だけを変えられる ( FIXED = item frame は据え置き)。
 *
 * 同じ {@code BackpackBlockEntityType} (SB 製) を共有しているので、SB バニラの
 * backpack ブロックが対象 BE になった時は SB のオリジナル描画にフォールバックする。
 *
 * Plugin: {@code sb-worn-display-blockbench} の {@code backpack_arsenal:placed} slot で
 * 編集できる。 JSON に display 値が無い間は FIXED が使われるので、何も書かなくても
 * 従来通り動く (= 非破壊的)。
 */
public class ArsenalBackpackBlockEntityRenderer
        implements BlockEntityRenderer<BackpackBlockEntity> {

    /** 設置ブロック描画の display context。
     *
     * ⚠ もとは {@code ItemDisplayContext.create("BACKPACK_ARSENAL_PLACED", ...)} の
     * カスタム context を使っていたが、 Forge 1.20.1 ではカスタム context の
     * moddedTransforms 反映が一部経路で動作せず:
     *   - {@link ChestBackpackLayer} は AIOOBE で crash
     *   - 設置ブロック (本 BER) は crash しないが transform 値が JSON から拾えず、
     *     display.placed の値が無視されサイズが実際と異なる
     *
     * 安全策として vanilla {@link ItemDisplayContext#FIXED} を流用する。 JSON 側は
     * {@code display.fixed} に worn と同じ値を書き、 worn と placed が同じ大きさで
     * 描画されるようにする。 (アイテムフレーム表示も FIXED を使うので同じ見た目に
     * なる ── ここを別にしたい場合は別途 transform を手動適用する hack が必要) */
    public static final ItemDisplayContext PLACED_CONTEXT = ItemDisplayContext.FIXED;

    /** BA ブロック以外 (= SB バニラ) を描画する時に丸投げするオリジナル BER の cache。 */
    private final BackpackBlockEntityRenderer sbFallback = new BackpackBlockEntityRenderer();

    /** 初回呼び出しを 1 度だけログに出すための flag。「大きさ変わらない」時の切り分け用:
     *   - "render() first call" が出ない          → 自前 BER が登録されてない (= SB BER がそのまま動いてる)
     *   - 出るが in-game サイズ変わらない         → display.backpack_arsenal:placed が JSON に保存されてない or model 未リロード */
    private static boolean diagLogged = false;

    public ArsenalBackpackBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {
        // ctx は BER 一般のお作法で受け取るが、本 BER は ItemRenderer 経由なので未使用。
    }

    @Override
    public void render(BackpackBlockEntity be,
                       float partialTicks,
                       PoseStack poseStack,
                       MultiBufferSource bufferSource,
                       int packedLight,
                       int packedOverlay) {
        if (!isArsenalBlock(be)) {
            // SB バニラの backpack ブロックは触らず、 SB BER そのままに丸投げする。
            sbFallback.render(be, partialTicks, poseStack, bufferSource, packedLight, packedOverlay);
            return;
        }

        Direction facing = be.getBlockState().getValue(BackpackBlock.FACING);
        ItemStack stack = be.getBackpackWrapper().getBackpack();
        if (stack.isEmpty()) {
            // wrapper が壊れている時 (SB が動いてない等) は何も描画しない方が安全
            return;
        }

        if (!diagLogged) {
            diagLogged = true;
            // PLACED_CONTEXT で実際に使われる transform を覗いて、 fallback (FIXED) か
            // カスタム値かをログから判別できるようにする。
            // scale が item model の display.fixed と同じなら fallback、 違う値なら
            // display.backpack_arsenal:placed が JSON にあって効いている。
            var bakedModel = Minecraft.getInstance().getItemRenderer().getModel(
                stack, be.getLevel(), null, 0);
            var transform = bakedModel.getTransforms().getTransform(PLACED_CONTEXT);
            BackpackArsenalMod.LOGGER.info(
                "[backpack_arsenal] ArsenalBackpackBlockEntityRenderer.render() first call: " +
                "stack={}, facing={}, context={}, transform.scale={}, translation={}, rotation={}",
                stack.getItem(), facing, PLACED_CONTEXT.getSerializedName(),
                transform.scale, transform.translation, transform.rotation);
        }

        poseStack.pushPose();
        // SB と同じく block の中央 (横軸) に乗せ、 FACING で回転する。
        // 内部 scale / Z 回転は SB BER でも pushPose→popPose に包まれてるため事実上
        // 効いていない (= JSON の display 値だけが大きさ・向きを決める)。
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(Axis.YN.rotationDegrees(facing.toYRot()));

        Minecraft.getInstance().getItemRenderer().renderStatic(
            stack,
            PLACED_CONTEXT,
            packedLight,
            OverlayTexture.NO_OVERLAY,
            poseStack,
            bufferSource,
            be.getLevel(),
            0
        );

        poseStack.popPose();
    }

    private static boolean isArsenalBlock(BackpackBlockEntity be) {
        return be.getBlockState().getBlock()
            == ArsenalBlocks.ARSENAL_BACKPACK_ELECTRON_BLOCK.get();
    }
}
