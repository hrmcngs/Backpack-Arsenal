package backpackarsenal.event;

import backpackarsenal.BackpackArsenalMod;
import backpackarsenal.init.ArsenalItems;
import backpackarsenal.inventory.ArsenalBackpackContainer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContainer;
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContext;

/**
 * SB の "Open Backpack" hotkey (B キー既定) や Curios "open curio inventory" 経由で
 * SB の素の BackpackContainer が arsenal_backpack を開いた時に、我々の専用充電スロット
 * 付き ArsenalBackpackContainer に差し替えるインターセプタ。
 *
 * SB の BackpackOpenMessage は openBackpack() で `new BackpackContainer(...)` を直接 new
 * してくるため、ArsenalBackpackItem.use() を完全にバイパスする。これにより B キー経由
 * では我々の addExtraSlot が走らず、充電スロットが見えない。
 *
 * 実装:
 *   1. PlayerContainerEvent.Open を listen
 *   2. 開いた menu が **素の BackpackContainer** (我々のサブクラスではない) で
 *      かつバックパックが arsenal_backpack なら検出
 *   3. server.execute() で次 tick に reopening をスケジュール
 *      (event 内で player.closeContainer() + 新規 openMenu を同期実行すると
 *       packet 順序が壊れるので tick を 1 つずらす)
 *   4. 再 open は ArsenalBackpackContainer 用の MenuType + ctx::toBuffer
 */
@Mod.EventBusSubscriber(modid = BackpackArsenalMod.MODID)
public class BackpackOpenInterceptor {

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        AbstractContainerMenu menu = event.getContainer();
        // 我々の subclass なら何もしない (use() 経由 = 既に我々の container)
        if (menu instanceof ArsenalBackpackContainer) return;
        if (!(menu instanceof BackpackContainer bc)) return;

        BackpackContext ctx;
        try {
            ctx = bc.getBackpackContext();
        } catch (Throwable t) {
            return;
        }
        if (ctx == null) return;

        // backpack の中身が arsenal_backpack か確認
        final boolean isArsenal;
        try {
            isArsenal = ctx.getBackpackWrapper(player).getBackpack().getItem()
                == ArsenalItems.ARSENAL_BACKPACK_ELECTRON.get();
        } catch (Throwable t) {
            return;
        }
        if (!isArsenal) return;

        BackpackArsenalMod.LOGGER.info(
            "[backpack_arsenal] Intercepting SB BackpackContainer for arsenal_backpack, reopening with ours.");

        // 次 tick で差し替え (この event 中の同期 reopen は危険)
        final BackpackContext capturedCtx = ctx;
        player.server.execute(() -> {
            try {
                player.closeContainer();
                SimpleMenuProvider provider = new SimpleMenuProvider(
                    (id, inv, p) -> new ArsenalBackpackContainer(id, p, capturedCtx),
                    capturedCtx.getDisplayName(player)
                );
                NetworkHooks.openScreen(player, provider, capturedCtx::toBuffer);
            } catch (Throwable t) {
                BackpackArsenalMod.LOGGER.error(
                    "[backpack_arsenal] Failed to reopen as ArsenalBackpackContainer", t);
            }
        });
    }
}
