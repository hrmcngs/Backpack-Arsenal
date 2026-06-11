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

    /** saya overlay 用同期は毎 tick やる必要が無いので、 こちらも軽く間引く。
     *  4 tick = 0.2 秒 ごとなら backpack 内容変更後の追従はほぼ即時。 */
    private static final int SAYA_SYNC_INTERVAL_TICKS = 4;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;

        Player player = event.player;
        int tc = player.tickCount;
        boolean isChargeTick = (tc % CHARGE_INTERVAL_TICKS == 0);
        boolean isSayaTick   = (tc % SAYA_SYNC_INTERVAL_TICKS == 0);
        // 両方 false の tick は inventory スキャン自体スキップ ( 4 / 5 の確率で early-out )。
        if (!isChargeTick && !isSayaTick) return;

        int baseCharge = VoltaicBladeItem.CHARGE_PER_TICK_IN_BACKPACK * CHARGE_INTERVAL_TICKS;

        // inventory + Curios "back" 走査 ( SB バニラ + ArsenalBackpack 両対象 )。
        // 充電 ( 重い ) は CHARGE_INTERVAL_TICKS 毎、 saya overlay 用の本数同期は
        // SAYA_SYNC_INTERVAL_TICKS 毎。
        backpackarsenal.util.BackpackScanner.forEachAnyBackpack(player, stack -> {
            if (isChargeTick) {
                int multiplier = 1 + sumVoltaicChargerContributions(stack);
                chargeAllKatanasInside(stack, baseCharge * multiplier);
            }
            if (isSayaTick && backpackarsenal.util.BackpackScanner.isArsenalBackpack(stack)) {
                backpackarsenal.item.ArsenalBackpackItem.syncVoltaicCountToNbt(stack);
            }
        });
    }

    /**
     * backpack 内の有効な VoltaicChargerUpgrade の倍率寄与合計。
     *
     * 全 tier (base 〜 tier 4) は同じ {@code UpgradeType} を共有してるので
     * {@code getTypeWrappers(TYPE)} 1回で全部取れる。 各 wrapper の
     * {@link backpackarsenal.upgrade.VoltaicChargerUpgradeItem.Wrapper#getMultiplierContribution()}
     * = (tier+1)^2 を合計する。
     */
    private static int sumVoltaicChargerContributions(ItemStack backpackStack) {
        var capOpt = backpackStack.getCapability(
            net.p3pp3rf1y.sophisticatedbackpacks.api.CapabilityBackpackWrapper.getCapabilityInstance());
        if (!capOpt.isPresent()) return 0;
        int[] sum = {0};
        capOpt.ifPresent(wrapper -> {
            try {
                int s = 0;
                // 既存の固定 tier charger
                for (var w : wrapper.getUpgradeHandler().getTypeWrappers(
                        backpackarsenal.upgrade.VoltaicChargerUpgradeItem.TYPE)) {
                    if (w != null && w.isEnabled()) s += w.getMultiplierContribution();
                }
                // 成長型 charger ( level 可変 )
                for (var w : wrapper.getUpgradeHandler().getTypeWrappers(
                        backpackarsenal.upgrade.VoltaicGrowthUpgradeItem.TYPE)) {
                    if (w != null && w.isEnabled()) s += w.getMultiplierContribution();
                }
                sum[0] = s;
            } catch (Throwable t) {
                BackpackArsenalMod.LOGGER.warn(
                    "[backpack_arsenal] sumVoltaicChargerContributions threw: {}", t.toString());
            }
        });
        return sum[0];
    }

    /** バックパックの中身を走査して VoltaicBlade を充電する。
     *  ArsenalBackpack も vanilla SB backpack も同じパス。
     *  ネストされた sub-backpack は再帰的に処理。 */
    private static void chargeAllKatanasInside(ItemStack backpackStack, int chargeAmount) {
        backpackStack.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler ->
            chargeKatanasInHandler(handler, chargeAmount));
    }

    /** 手持ち backpack 内の Mekanism tool 等 ( IEnergyStorage cap 持ち ) に 1 回で
     *  渡せる FE 量。 voltaic_blade と同じ感覚で 10 tick あたり ~2000 FE = 200 FE/tick = 4000 FE/秒。 */
    private static final int HELD_FE_PER_INTERVAL = 2000;

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
            } else {
                // Mekanism tool / 他 mod のバッテリ式アイテム ( IEnergyStorage cap 持ち ) も充電。
                // 設置 backpack と違って FE バッファが無いので一定量を直接渡す。
                inner.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY)
                    .ifPresent(target -> {
                        if (target.canReceive() && target.getEnergyStored() < target.getMaxEnergyStored()) {
                            target.receiveEnergy(HELD_FE_PER_INTERVAL, false);
                        }
                    });
                if (handler instanceof IItemHandlerModifiable mod) {
                    mod.setStackInSlot(slot, inner);
                }
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
