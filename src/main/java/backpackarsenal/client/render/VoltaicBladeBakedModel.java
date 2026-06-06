package backpackarsenal.client.render;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.model.BakedModelWrapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Voltaic Blade 用 BakedModel wrapper。 元の BakedModel に対して
 * {@link #getRenderTypes(ItemStack, boolean)} だけ override し、
 * 描画 pass を [base + voltaic_glint] の 2 段にする。
 *
 * vanilla glint は {@link backpackarsenal.item.VoltaicBladeItem#isFoil} で
 * 抑制してるので、ここで返す glint だけが描画される (= V 軸のみスクロール)。
 */
public class VoltaicBladeBakedModel extends BakedModelWrapper<BakedModel> {

    public VoltaicBladeBakedModel(BakedModel original) {
        super(original);
    }

    @Override
    public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
        List<RenderType> base = super.getRenderTypes(stack, fabulous);
        if (!backpackarsenal.item.VoltaicBladeItem.shouldGlint(stack)) {
            return base;
        }
        List<RenderType> result = new ArrayList<>(base);
        result.add(VoltaicGlintRenderType.VOLTAIC_GLINT);
        return result;
    }
}
