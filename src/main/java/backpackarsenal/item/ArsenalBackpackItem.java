package backpackarsenal.item;

import backpackarsenal.init.ArsenalItems;
import backpackarsenal.inventory.ArsenalBackpackContainer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.network.NetworkHooks;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackBlock;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem;
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContext;

import java.util.List;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Arsenal Backpack — Sophisticated Backpacks の BackpackItem を継承した独自グレード。
 *
 * 設計コンセプト: スタート時は最小限の容量。
 * Sophisticated Backpacks 標準の Upgrade Card スロット (5枠) を埋めて拡張する想定:
 *   - Stack Upgrade (basic/advanced/...) → 1スロットの最大スタック数を倍々で増やす
 *   - Pickup Upgrade → 自動拾い
 *   - Magnet / Feeding / Compacting 等
 *   - Refill Upgrade → ホットバー自動補充
 *
 * 充電ロジック: BackpackChargingHandler が ArsenalBackpack も対象に含めている。
 * voltaic_blade を中に入れると充電される。
 *
 * インベントリ: 9 スロット (最低限)。アップグレード: 5 スロット (Stack 系含めて柔軟に組める量)。
 * ブロック供給: SB 標準の革製 backpack ブロックを共用 (設置時の見た目はノーマルな revolt 革)。
 */
public class ArsenalBackpackItem extends BackpackItem {

    /** インベントリスロット数 — 最低限の 9 スロット (チェスト1段分のさらに半分以下)。
     *  これより小さくしたい/大きくしたい場合は数字を変える。 */
    public static final int INVENTORY_SLOTS = 9;
    /** アップグレードスロット数 — SB の Stack/Pickup/Magnet 等を 4 枚まで挿せる。
     *  upgrade 列の一番上 (Y=8) は専用充電スロットに譲っているので 4 枚。 */
    public static final int UPGRADE_SLOTS   = 4;

    /** 通常スロット 1 マスあたりの最大スタック数 (voltaic_blade は別。stacksTo(1) の Item 自体の上限が効く) */
    public static final int PER_SLOT_STACK_LIMIT = 9;

    public ArsenalBackpackItem() {
        super(
            // lambda が呼ばれる度に config の現在値を読むので、reload で動的反映可能
            () -> backpackarsenal.init.ArsenalBackpackConfig.inventorySlots,
            () -> backpackarsenal.init.ArsenalBackpackConfig.upgradeSlots,
            // 設置時の見た目をカスタム 3D モデルにするため独自 Block を渡す。
            // 元は ModBlocks.BACKPACK::get (SB 標準) だった。
            (Supplier<BackpackBlock>) backpackarsenal.init.ArsenalBlocks.ARSENAL_BACKPACK_ELECTRON_BLOCK::get,
            (UnaryOperator<Item.Properties>) props -> props.rarity(Rarity.UNCOMMON).stacksTo(1)
        );
    }

    // ─── バニラチェスト防具スロット装備サポート ───────────────────────────
    //
    // ArsenalBackpack を E キーのインベントリ画面のチェストスロットに置けるようにする。
    // 装備されると {@link backpackarsenal.client.render.ChestBackpackLayer} が
    // body の前方に backpack 本体 + saya を描画する。
    //
    // 注意:
    //   - 元は SB の BackpackItem (= ArmorItem ではない) なので、 vanilla の
    //     ArmorItem ベースの装備判定 (Inventory.getEquipmentSlot 等) はそのままだと
    //     チェストへ shift-click が通らない。 Forge の hook 2つを override で解決:
    //       canEquip      : Mob の自動装備可否 / Inventory.getEquipmentSlot
    //       getEquipmentSlot : Curios 等の slot 判定で参照される
    //   - Curios "back" 装着とは独立。 両装着しても落ちないが saya が二重描画される。

    @Override
    public boolean canEquip(ItemStack stack, EquipmentSlot armorType, net.minecraft.world.entity.Entity entity) {
        return armorType == EquipmentSlot.CHEST;
    }

    @Override
    public EquipmentSlot getEquipmentSlot(ItemStack stack) {
        return EquipmentSlot.CHEST;
    }

    // initializeClient() は SB 親に委ねる。
    // SB の BackpackItemStackRenderer (BEWLR) は内部で ItemRenderer.getModel(stack) を呼ぶので、
    // 我々の JSON BakedModel (assets/backpack_arsenal/models/item/arsenal_backpack.json) を
    // 自動で使ってくれる。Curios "back" の身体貼付き位置 (BackpackLayerRenderer 経由) や
    // インベントリ表示も同じ BakedModel が使われるため、override せず親に任せる方が正しく動く。

    // initCapabilities() は super に委ねる。
    // 以前は StackLimitedHandler で 1 スロット 9 個に上限を被せていたが、これが
    // Stack Upgrade (スロット上限を倍々で増やす) の効果を打ち消してしまうため撤去。
    // デフォルト SB インベントリの挙動に揃える。

    /** インベントリでホバー時に通常スロット内の voltaic_blade 本数を表示する。 */
    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        int countInRegular = countVoltaicInRegularSlots(stack);
        if (countInRegular > 0) {
            tooltip.add(Component.translatable(
                    "item.backpack_arsenal.arsenal_backpack_electron.stored_regular",
                    countInRegular
            ).withStyle(ChatFormatting.GRAY));
        }
    }

    /** NBT key — server side で本数を書き込み、 client は NBT 経由でこの数を読む。
     *  SB は ItemStack 内 NBT を全て client 同期するので、これだけで client 描画系が動く。
     *  capability 経由の inventory 走査は client 側で空 handler を返すケースがあるため
     *  rendering からは必ず NBT を見る。 */
    private static final String NBT_VOLTAIC_COUNT_TAG = "BackpackArsenalVoltaicCount";

    /**
     * バックパック内の通常スロットに入っている voltaic_blade の本数を数える。
     *
     * 優先順位:
     *   1. NBT に同期済み count があれば即それを返す (client side で使用)
     *   2. capability 経由でライブ scan (server side、 NBT 未書き込み時のフォールバック)
     */
    public static int countVoltaicInRegularSlots(ItemStack backpack) {
        var nbt = backpack.getTag();
        if (nbt != null && nbt.contains(NBT_VOLTAIC_COUNT_TAG)) {
            return nbt.getInt(NBT_VOLTAIC_COUNT_TAG);
        }
        return liveScanVoltaicCount(backpack);
    }

    /** capability から実際にスロットを走査して数を返す。 server side のみ正確に動く。 */
    private static int liveScanVoltaicCount(ItemStack backpack) {
        IItemHandler handler = backpack.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
        if (handler == null) return 0;
        int count = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            if (handler.getStackInSlot(i).getItem() == ArsenalItems.VOLTAIC_BLADE.get()) {
                count++;
            }
        }
        return count;
    }

    /**
     * server-side で voltaic_blade 本数を scan し、 ItemStack NBT に同期する。
     * tick handler ({@code BackpackChargingHandler}) から定期的に呼ばれる想定。
     *
     * 同じ count が既に書かれていれば書き込まず、NBT 変更通知 → 無駄な client 同期を抑制する。
     */
    public static void syncVoltaicCountToNbt(ItemStack backpack) {
        if (backpack.isEmpty()) return;
        int newCount = liveScanVoltaicCount(backpack);
        var nbt = backpack.getTag();
        int oldCount = (nbt != null && nbt.contains(NBT_VOLTAIC_COUNT_TAG))
            ? nbt.getInt(NBT_VOLTAIC_COUNT_TAG) : -1;
        if (oldCount != newCount) {
            backpack.getOrCreateTag().putInt(NBT_VOLTAIC_COUNT_TAG, newCount);
        }
    }

    /**
     * SB の use() を再現しつつ、開く Menu を ArsenalBackpackContainer に差し替える。
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            try {
                String handlerName = (hand == InteractionHand.MAIN_HAND) ? "main" : "offhand";
                int slotIndex = (hand == InteractionHand.MAIN_HAND)
                    ? player.getInventory().selected
                    : 0;
                BackpackContext.Item ctx = new BackpackContext.Item(handlerName, slotIndex);

                SimpleMenuProvider provider = new SimpleMenuProvider(
                    (containerId, inv, p) -> new ArsenalBackpackContainer(containerId, p, ctx),
                    stack.getHoverName()
                );
                // 重要: ctx::toBuffer (=type marker + addToBuffer) を使う。
                // ctx::addToBuffer だと ContextType marker が書かれず、client の
                // BackpackContext.fromBuffer が dispatch table を解釈できず silent crash する。
                NetworkHooks.openScreen(serverPlayer, provider, ctx::toBuffer);
            } catch (Throwable t) {
                backpackarsenal.BackpackArsenalMod.LOGGER.error(
                    "[backpack_arsenal] use() failed", t);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
