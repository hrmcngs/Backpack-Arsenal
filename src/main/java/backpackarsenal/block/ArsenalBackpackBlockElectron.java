package backpackarsenal.block;

import backpackarsenal.init.ArsenalItems;
import net.minecraft.world.item.Item;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackBlock;

/**
 * 設置時の見た目を独自 3D モデルで描画するための BackpackBlock サブクラス。
 *
 * SB の {@link BackpackBlock} を継承するので BlockEntity 作成・waterlogged・open
 * 状態などはそのまま動作する。差し替えるのは {@link #asItem()} のみで、
 * pick-block (middle click) や drop で正しく ArsenalBackpackItem を返すようにする。
 *
 * BlockEntityType ({@code ModBlocks.BACKPACK_TILE_TYPE}) には reflection で
 * 我々のブロックを追加する (BackpackArsenalMod#injectBlockIntoBackpackTileType)。
 */
public class ArsenalBackpackBlockElectron extends BackpackBlock {

    public ArsenalBackpackBlockElectron() {
        super();
    }

    @Override
    public Item asItem() {
        return ArsenalItems.ARSENAL_BACKPACK_ELECTRON.get();
    }
}
