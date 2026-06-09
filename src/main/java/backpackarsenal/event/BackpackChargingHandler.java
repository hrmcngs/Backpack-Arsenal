package backpackarsenal.event;

import backpackarsenal.BackpackArsenalMod;
import backpackarsenal.init.ArsenalItems;
import backpackarsenal.item.VoltaicBladeItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Sophisticated Backpacks のインベントリ内にある ThunderKatana を毎tick走査して充電する。
 *
 * 仕様メモ:
 *   - プレイヤーのメインインベントリ全体（ホットバー + メイン + オフハンド + 装備）を
 *     走査し、Sophisticated Backpacks のアイテムを検出。
 *   - 検出した backpack の IItemHandler capability を取り、内側のスロットに ThunderKatana が
 *     あれば充電を加算する。
 *   - サーバーサイドのみ実行。クライアントは描画のために NBT が同期されてくるのを待つ。
 *
 * Sophisticated Backpacks に直接依存せず、Forge の IItemHandler 経由でアクセスするため、
 * 万一 backpacks 側の内部 API が変わっても壊れにくい。
 */
@Mod.EventBusSubscriber(modid = BackpackArsenalMod.MODID)
public class BackpackChargingHandler {

    /** 充電走査間隔 (tick)。 1秒 = 20tick。 充電は連続値積算なので低頻度で OK。 */
    private static final int CHARGE_INTERVAL_TICKS = 10;

    /** Sophisticated Backpacks の modid */
    private static final String SB_MODID = "sophisticatedbackpacks";

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;

        Player player = event.player;
        boolean isChargeTick = (player.tickCount % CHARGE_INTERVAL_TICKS == 0);
        int baseCharge = VoltaicBladeItem.CHARGE_PER_TICK_IN_BACKPACK * CHARGE_INTERVAL_TICKS;

        // inventory + Curios "back" 走査 (SB バニラ + ArsenalBackpack 両対象)。
        // 充電 (重い) は CHARGE_INTERVAL_TICKS 毎、 saya overlay 用の本数同期 (軽い) は
        // 毎 tick 実行する。 saya モデルが backpack 内容の変化に追従するタイミングを早く
        // するため。 syncVoltaicCountToNbt は count 変化時のみ NBT 書き込みするので
        // 毎 tick 呼んでも spam しない。
        backpackarsenal.util.BackpackScanner.forEachAnyBackpack(player, stack -> {
            if (isChargeTick) {
                int multiplier = 1 + countVoltaicChargerUpgrades(stack);
                chargeAllKatanasInside(stack, baseCharge * multiplier);
            }
            if (backpackarsenal.util.BackpackScanner.isArsenalBackpack(stack)) {
                backpackarsenal.item.ArsenalBackpackItem.syncVoltaicCountToNbt(stack);
            }
        });
    }

    /** countVoltaicChargerUpgrades の診断ログを一定間隔で出すための tick counter */
    private static int debugLogTick = 0;

    /**
     * backpack 内の有効な VoltaicChargerUpgrade 枚数。
     *
     * SB の {@code IUpgradeHandler.getTypeWrappers(UpgradeType)} を使う。 これは内部で
     * {@code typeWrappers Map<UpgradeType, List<IUpgradeWrapper>>} を引くので、
     * instanceof で全 slot を走査するより速くて確実。
     *
     * 「機能していない」ケースの切り分けに、 type 別の wrapper 種類も診断ログに出す。
     */
    private static int countVoltaicChargerUpgrades(ItemStack backpackStack) {
        var capOpt = backpackStack.getCapability(
            net.p3pp3rf1y.sophisticatedbackpacks.api.CapabilityBackpackWrapper.getCapabilityInstance());
        if (!capOpt.isPresent()) {
            maybeLog("no IBackpackWrapper cap on stack={}", backpackStack);
            return 0;
        }
        int[] count = {0};
        capOpt.ifPresent(wrapper -> {
            try {
                var upgradeHandler = wrapper.getUpgradeHandler();

                // 主経路: getTypeWrappers で TYPE から直接引く
                java.util.List<backpackarsenal.upgrade.VoltaicChargerUpgradeItem.Wrapper> matched =
                    upgradeHandler.getTypeWrappers(
                        backpackarsenal.upgrade.VoltaicChargerUpgradeItem.TYPE);
                int enabled = 0;
                for (var w : matched) {
                    if (w != null && w.isEnabled()) enabled++;
                }
                count[0] = enabled;

                // 診断: getSlotWrappers の全 entry も併せてダンプして、 SB が我々の
                // item を VoltaicChargerUpgradeItem.Wrapper として認識してるかを確認可能に。
                var allSlots = upgradeHandler.getSlotWrappers();
                if (!allSlots.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    allSlots.forEach((slot, w) -> {
                        sb.append(slot).append("=");
                        sb.append(w == null ? "null"
                            : w.getClass().getSimpleName() + (w.isEnabled() ? "" : "(disabled)"));
                        sb.append(',');
                    });
                    maybeLog("upgradeHandler slots={} typeWrappers(VoltaicCharger)={} enabled={}",
                        sb.toString(), matched.size(), enabled);
                }
            } catch (Throwable t) {
                BackpackArsenalMod.LOGGER.warn(
                    "[backpack_arsenal] countVoltaicChargerUpgrades threw: {}", t.toString());
            }
        });
        return count[0];
    }

    /** 5 秒に 1 回程度ログ出力 (毎 tick だとログが溢れるので throttle) */
    private static void maybeLog(String fmt, Object... args) {
        debugLogTick++;
        if (debugLogTick % 10 == 0) {  // 10回呼ばれるごと = scan interval 10t * 10 = 100t = 5秒
            BackpackArsenalMod.LOGGER.info("[backpack_arsenal] " + fmt, args);
        }
    }

    /** バックパックの中身を走査して VoltaicBlade を充電する。
     *  ArsenalBackpack も vanilla SB backpack も同じパス。
     *  ネストされた sub-backpack は再帰的に処理。 */
    private static void chargeAllKatanasInside(ItemStack backpackStack, int chargeAmount) {
        backpackStack.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler ->
            chargeKatanasInHandler(handler, chargeAmount));
    }

    /** IItemHandler を再帰的に走査（ネストしたバックパックにも対応） */
    private static void chargeKatanasInHandler(IItemHandler handler, int chargeAmount) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack inner = handler.getStackInSlot(slot);
            if (inner.isEmpty()) continue;

            if (inner.getItem() == ArsenalItems.VOLTAIC_BLADE.get()) {
                int before = VoltaicBladeItem.getCharge(inner);
                if (before >= VoltaicBladeItem.getMaxCharge(inner)) continue;

                VoltaicBladeItem.addCharge(inner, chargeAmount);

                // 一部の IItemHandler 実装はスロットを直接書き換えないと変更を保存しないため
                // Modifiable ならスロットに再セットして確実に永続化する。
                if (handler instanceof IItemHandlerModifiable mod) {
                    mod.setStackInSlot(slot, inner);
                }
            } else if (isSophisticatedBackpack(inner)) {
                // ネストされたバックパックの中身もチャージ対象に
                chargeAllKatanasInside(inner, chargeAmount);
            }
        }
    }

    /** ItemStack が Sophisticated Backpacks のバックパックまたは ArsenalBackpack かどうか判定 */
    public static boolean isSophisticatedBackpack(ItemStack stack) {
        if (stack.isEmpty()) return false;
        // 自前の ArsenalBackpack (SB BackpackItem 拡張) も充電対象に含める
        if (stack.getItem() == ArsenalItems.ARSENAL_BACKPACK_ELECTRON.get()) return true;

        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return false;
        if (!SB_MODID.equals(id.getNamespace())) return false;
        // 装飾系や upgrade item を除外: backpack を含むものだけ対象
        return id.getPath().contains("backpack");
    }
}
