package backpackarsenal.client;

import backpackarsenal.BackpackArsenalMod;
import backpackarsenal.item.VoltaicBladeItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * Geckolib による Voltaic Blade レンダラー。
 *
 * 参照リソース (Geckolib 標準パス):
 *   - モデル: assets/backpack_arsenal/geo/item/voltaic_blade.geo.json
 *   - テクスチャ: assets/backpack_arsenal/textures/item/voltaic_blade.png
 *   - アニメーション: assets/backpack_arsenal/animations/item/voltaic_blade.animation.json
 *
 * Blockbench で File → Convert Project → "Geckolib Animated Model" → エクスポート
 * すると .geo.json が出るので上記モデルパスに上書き保存すれば反映される。
 * rotation 角度の vanilla item model 制約 ({-45,-22.5,0,22.5,45}) からは解放される。
 */
public class VoltaicBladeRenderer extends GeoItemRenderer<VoltaicBladeItem> {

    public VoltaicBladeRenderer() {
        super(new DefaultedItemGeoModel<VoltaicBladeItem>(
            new ResourceLocation(BackpackArsenalMod.MODID, "voltaic_blade")
        ));
    }
}
