package backpackarsenal.init;

import backpackarsenal.BackpackArsenalMod;
import backpackarsenal.block.ArsenalBackpackBlockElectron;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Arsenal Backpack 系のブロック登録。
 *
 * 各 backpack variant に対応する独自 Block を持つことで設置時の見た目を
 * カスタム 3D モデルで描画できる。BlockEntity は SB の BackpackBlockEntity を
 * そのまま使う (reflection で BackpackBlockEntityType.validBlocks に追加)。
 */
public class ArsenalBlocks {

    public static final DeferredRegister<Block> REGISTRY =
        DeferredRegister.create(ForgeRegistries.BLOCKS, BackpackArsenalMod.MODID);

    public static final RegistryObject<ArsenalBackpackBlockElectron> ARSENAL_BACKPACK_ELECTRON_BLOCK =
        REGISTRY.register("arsenal_backpack_electron", ArsenalBackpackBlockElectron::new);
}
