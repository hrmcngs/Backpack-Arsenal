package backpackarsenal.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackBlock;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem;
import net.p3pp3rf1y.sophisticatedbackpacks.init.ModBlocks;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Arsenal Backpack — Sophisticated Backpacks の BackpackItem を継承した独自グレード。
 *
 * SB 通常バックパックと同じ機能 (インベントリ / アップグレード / 設置 / GUI) を持ち、
 * このアドオンの {@link backpackarsenal.event.BackpackChargingHandler} の充電対象にも
 * 自動で含まれる。
 *
 * スロット: 96 + 12 アップグレード (ネザライト級と同等)。
 * ブロック供給: ネザライトバックパックブロックを共用 (設置時の見た目はネザライトのもの)。
 */
public class ArsenalBackpackItem extends BackpackItem {

    /** インベントリスロット数 — Iron 級 (54) を基準にした中規模。SB の grid (1行12〜14マス) に
     *  対して 54 = 約4〜5行で表示される。多すぎる場合は数字を下げてください。 */
    public static final int INVENTORY_SLOTS = 54;
    /** アップグレードスロット数 — Iron 級と同じ 5。多すぎたら下げる。 */
    public static final int UPGRADE_SLOTS   = 5;

    public ArsenalBackpackItem() {
        super(
            () -> INVENTORY_SLOTS,
            () -> UPGRADE_SLOTS,
            (Supplier<BackpackBlock>) ModBlocks.NETHERITE_BACKPACK::get,
            (UnaryOperator<Item.Properties>) props -> props.rarity(Rarity.EPIC).stacksTo(1)
        );
    }
}
