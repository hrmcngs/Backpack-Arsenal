package backpackarsenal.client.render;

import backpackarsenal.BackpackArsenalMod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/**
 * BakedModel の wrapper。 {@link #applyTransform} だけ独自に処理して、 カスタム
 * {@link ItemDisplayContext} の AIOOBE を確実に避ける。
 *
 * 背景: Forge 1.20.1 の {@link ItemTransforms#getTransform} は
 *   {@code moddedTransforms.get(ctx)} → 無ければ vanilla の synthetic switch table
 * という流れ。 vanilla switch は enum ordinal を index にしたサイズ固定 int[] でできており、
 * カスタム context (ordinal 13+) を hit すると ArrayIndexOutOfBoundsException で死ぬ。
 *
 * 対策: ここでは reflection で {@code moddedTransforms} map を直接読み、 該当 context が
 * あれば適用、 無ければ NO_TRANSFORM。 switch には触らない。 vanilla context が来たら
 * 安全な ordinal なので普通に getTransform を呼ぶ。
 */
public class SafeTransformBakedModel implements BakedModel {

    private static @Nullable Field moddedTransformsField;
    private static boolean reflectionTried = false;

    private final BakedModel wrapped;

    public SafeTransformBakedModel(BakedModel wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public BakedModel applyTransform(ItemDisplayContext type, PoseStack poseStack, boolean applyLeftHandTransform) {
        ItemTransforms transforms = wrapped.getTransforms();
        ItemTransform transform = lookupTransform(transforms, type);
        transform.apply(applyLeftHandTransform, poseStack);
        return this; // return wrapper so further transforms 等はこの safe wrapper 経由になる
    }

    /** moddedTransforms を reflection で直接読む。 vanilla context は ordinal が低いので
     *  普通の getTransform を呼んでも switch が安全に hit する。 */
    private static ItemTransform lookupTransform(ItemTransforms transforms, ItemDisplayContext ctx) {
        // vanilla 既存の 9 値 (NONE + 8) は ordinal 0..8、 synthetic table は size 9 で安全。
        // それ以外 (= mod 由来) は moddedTransforms 直 lookup に切り替え。
        if (ctx.ordinal() < 9) {
            try {
                return transforms.getTransform(ctx);
            } catch (Throwable ignored) {
                // 念のため fallback
            }
        }
        Map<ItemDisplayContext, ItemTransform> modded = getModdedTransforms(transforms);
        if (modded != null) {
            ItemTransform t = modded.get(ctx);
            if (t != null) return t;
            // ctx と同名の別 instance が入ってる可能性 (registration timing バグ等) を考慮
            for (Map.Entry<ItemDisplayContext, ItemTransform> e : modded.entrySet()) {
                if (e.getKey().getSerializedName().equals(ctx.getSerializedName())) {
                    return e.getValue();
                }
            }
        }
        return ItemTransform.NO_TRANSFORM;
    }

    @SuppressWarnings("unchecked")
    private static @Nullable Map<ItemDisplayContext, ItemTransform> getModdedTransforms(ItemTransforms transforms) {
        if (!reflectionTried) {
            reflectionTried = true;
            // Forge の field 名は混淆対策で複数候補を試す
            for (String name : new String[]{"moddedTransforms", "f_111792_"}) {
                try {
                    Field f = ItemTransforms.class.getDeclaredField(name);
                    f.setAccessible(true);
                    moddedTransformsField = f;
                    BackpackArsenalMod.LOGGER.info(
                        "[backpack_arsenal] SafeTransformBakedModel: located moddedTransforms field as '{}'", name);
                    break;
                } catch (NoSuchFieldException ignored) {
                    // 次の候補
                }
            }
            if (moddedTransformsField == null) {
                BackpackArsenalMod.LOGGER.warn(
                    "[backpack_arsenal] SafeTransformBakedModel: moddedTransforms field not found — custom display contexts will be ignored");
            }
        }
        if (moddedTransformsField == null) return null;
        try {
            return (Map<ItemDisplayContext, ItemTransform>) moddedTransformsField.get(transforms);
        } catch (Throwable t) {
            return null;
        }
    }

    // ─── 残りの BakedModel メソッドは wrapped に丸投げ ───────────────────────
    @Override public List<net.minecraft.client.renderer.block.model.BakedQuad> getQuads(
            @Nullable BlockState state, @Nullable Direction direction, RandomSource random) {
        return wrapped.getQuads(state, direction, random);
    }
    @Override public boolean useAmbientOcclusion()  { return wrapped.useAmbientOcclusion(); }
    @Override public boolean isGui3d()              { return wrapped.isGui3d(); }
    @Override public boolean usesBlockLight()       { return wrapped.usesBlockLight(); }
    @Override public boolean isCustomRenderer()     { return wrapped.isCustomRenderer(); }
    @Override public TextureAtlasSprite getParticleIcon() { return wrapped.getParticleIcon(); }
    @Override public ItemTransforms getTransforms() { return wrapped.getTransforms(); }
    @Override public ItemOverrides getOverrides()   { return wrapped.getOverrides(); }
}
