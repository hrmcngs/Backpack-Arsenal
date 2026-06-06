package backpackarsenal.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import org.joml.Matrix4f;

/**
 * Voltaic Blade 専用エンチャント glint。vanilla glint がランダム方向の対角スクロール
 * なのに対し、こちらは V 軸 (= 画面縦) のみスクロールさせる。blade を縦向きに
 * 描画している時に「下から上に光が流れる」見え方になる。
 *
 * vanilla glint との差分は {@link #setupVerticalGlint} 内の Matrix4f だけで、
 * shader / texture / blend / depth は全部 vanilla 共用。
 */
public class VoltaicGlintRenderType extends RenderStateShard {

    // RenderStateShard の protected 定数 (NO_CULL / EQUAL_DEPTH_TEST /
    // GLINT_TRANSPARENCY / VIEW_OFFSET_Z_LAYERING) は protected static のため、
    // subclass にしないと外から参照できない。インスタンス化する必要はないので
    // ダミー super 呼び出しで継承だけする。
    private VoltaicGlintRenderType() {
        super("voltaic_glint_unused", () -> {}, () -> {});
    }

    /** 縦方向 (V 軸) のみスクロール。vanilla の setupGlintTexturing 相当を改変。 */
    private static void setupVerticalGlint(float scale) {
        long ms = Util.getMillis() * 8L;
        float dy = (float)(ms % 30000L) / 30000.0F;
        Matrix4f matrix = new Matrix4f().translation(0, dy, 0);
        matrix.scale(scale, scale, scale);
        RenderSystem.setTextureMatrix(matrix);
    }

    private static final RenderStateShard.TexturingStateShard VERTICAL_GLINT_TEXTURING =
        new RenderStateShard.TexturingStateShard(
            "voltaic_glint_texturing",
            () -> setupVerticalGlint(8.0F),
            RenderSystem::resetTextureMatrix
        );

    /** Voltaic Blade 用 glint render type。vanilla の RenderType.ENTITY_GLINT 相当 + V 軸のみスクロール。 */
    public static final RenderType VOLTAIC_GLINT = RenderType.create(
        "voltaic_glint",
        DefaultVertexFormat.BLOCK,
        VertexFormat.Mode.QUADS,
        256,
        false,
        true,
        RenderType.CompositeState.builder()
            .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeEntityGlintShader))
            .setTextureState(new RenderStateShard.TextureStateShard(ItemRenderer.ENCHANTED_GLINT_ITEM, false, false))
            .setWriteMaskState(new RenderStateShard.WriteMaskStateShard(true, false))
            .setCullState(NO_CULL)
            .setDepthTestState(EQUAL_DEPTH_TEST)
            .setTransparencyState(GLINT_TRANSPARENCY)
            .setTexturingState(VERTICAL_GLINT_TEXTURING)
            .setLayeringState(VIEW_OFFSET_Z_LAYERING)
            .createCompositeState(false)
    );
}
