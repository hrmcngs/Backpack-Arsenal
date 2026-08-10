package backpackarsenal.client.render;

import backpackarsenal.item.ArsenalBackpackItem;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.model.BakedModelWrapper;

import javax.annotation.Nullable;

/** Selects the docked-blade model from the Fire backpack's synchronized NBT. */
public class FireBackpackBakedModel extends BakedModelWrapper<BakedModel> {
    private final ItemOverrides overrides;

    public FireBackpackBakedModel(BakedModel emptyModel, BakedModel loadedModel) {
        super(emptyModel);
        this.overrides = new ItemOverrides() {
            @Override
            public BakedModel resolve(BakedModel model, ItemStack stack,
                                      @Nullable ClientLevel level,
                                      @Nullable LivingEntity entity, int seed) {
                return ArsenalBackpackItem.hasLoadedFireBlade(stack)
                    ? loadedModel : emptyModel;
            }
        };
    }

    @Override
    public ItemOverrides getOverrides() {
        return overrides;
    }
}
