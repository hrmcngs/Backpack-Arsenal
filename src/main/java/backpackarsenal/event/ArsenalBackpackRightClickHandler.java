package backpackarsenal.event;

import backpackarsenal.BackpackArsenalMod;
import backpackarsenal.init.ArsenalItems;
import backpackarsenal.inventory.ArsenalBackpackContainer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContext;

/**
 * Arsenal Backpack を 手持ち右クリックした時に UI を 確実に 開くハンドラ。
 *
 * 背景:
 *   ArsenalBackpackItem.use() を override しているが、 Curios / MAW 等 上流の
 *   RightClickItem listener が arsenal_backpack を「back カーリオ自動装着候補」
 *   として早期に setCanceled(true) してしまい、 use() に到達しないケースがある
 *   ( "設置はできるが 手持ち右クリックで 開かない" 症状の原因 )。
 *   そこで HIGHEST priority で 先回りして 自前で openScreen し、 setCanceled で
 *   上流の自動装着 / 二重処理を抑える。
 *
 * 仕様:
 *   - 通常 ( 非 shift ) の 手持ち右クリック → backpack UI を開く
 *   - shift + 右クリック → 素通り ( Curios 自動装着 / SB 標準挙動を許可 )
 *   - ブロックに当たった右クリックは BlockItem.useOn ( 設置 ) が先に消費するので
 *     ここまで来ない = 設置を阻害しない
 *   - 既に他 listener が cancel 済 の event は処理しない ( double-open 防止 )
 */
@Mod.EventBusSubscriber(modid = BackpackArsenalMod.MODID)
public class ArsenalBackpackRightClickHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        boolean isArsenal = stack.getItem() == ArsenalItems.ARSENAL_BACKPACK_ELECTRON.get();
        if (!isArsenal) return;

        Player player = event.getEntity();
        boolean side = event.getLevel().isClientSide;
        boolean sneaking = player.isShiftKeyDown();
        boolean alreadyCanceled = event.isCanceled();

        // 診断ログ: 何が起きてるか server.log で 1 行で追えるように。
        // dedicated server で「右クリックで開かない」原因を切り分ける用。
        BackpackArsenalMod.LOGGER.info(
            "[arsenal_rc] RightClickItem fire side={} hand={} sneaking={} canceled={} player={}",
            side ? "CLIENT" : "SERVER", event.getHand(), sneaking, alreadyCanceled,
            player.getName().getString());

        if (alreadyCanceled) return;
        if (sneaking) return;

        // 両 side で cancel: client は予測 cancel して Curios 自動装着 を抑え、
        // server は実際の cancel + openScreen 発火。
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);

        if (side) return; // client は cancel だけして抜ける
        if (!(player instanceof ServerPlayer sp)) return;

        InteractionHand hand = event.getHand();
        String handlerName = (hand == InteractionHand.MAIN_HAND) ? "main" : "offhand";
        int slotIndex = (hand == InteractionHand.MAIN_HAND)
            ? player.getInventory().selected
            : 0;
        BackpackContext.Item ctx = new BackpackContext.Item(handlerName, slotIndex);

        SimpleMenuProvider provider = new SimpleMenuProvider(
            (containerId, inv, p) -> new ArsenalBackpackContainer(containerId, p, ctx),
            stack.getHoverName()
        );
        try {
            // 重要: ctx::toBuffer (= type marker + addToBuffer) を使う。
            // ctx::addToBuffer だと ContextType marker が書かれず、 client の
            // BackpackContext.fromBuffer が dispatch table を解釈できず silent crash する。
            NetworkHooks.openScreen(sp, provider, ctx::toBuffer);
            BackpackArsenalMod.LOGGER.info(
                "[arsenal_rc] openScreen called for slot={} ctx={}", slotIndex, handlerName);
        } catch (Throwable t) {
            BackpackArsenalMod.LOGGER.error(
                "[backpack_arsenal] failed to open ArsenalBackpack UI from right-click handler", t);
        }
    }
}
