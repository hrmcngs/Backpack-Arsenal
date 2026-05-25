package backpackarsenal.event;

import backpackarsenal.BackpackArsenalMod;
import backpackarsenal.init.ArsenalItems;
import backpackarsenal.item.VoltaicBladeItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
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

    /** 走査間隔 (tick) — 1秒 = 20tick */
    private static final int SCAN_INTERVAL_TICKS = 10;

    /** Sophisticated Backpacks の modid */
    private static final String SB_MODID = "sophisticatedbackpacks";

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;
        if (event.player.tickCount % SCAN_INTERVAL_TICKS != 0) return;

        Player player = event.player;
        Inventory inv = player.getInventory();

        int chargePerScan = VoltaicBladeItem.CHARGE_PER_TICK_IN_BACKPACK * SCAN_INTERVAL_TICKS;

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (!isSophisticatedBackpack(stack)) continue;
            chargeAllKatanasInside(stack, chargePerScan);
        }
    }

    /** バックパックの中身を走査して ThunderKatana を充電する */
    private static void chargeAllKatanasInside(ItemStack backpackStack, int chargeAmount) {
        backpackStack.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
            chargeKatanasInHandler(handler, chargeAmount);
        });
    }

    /** IItemHandler を再帰的に走査（ネストしたバックパックにも対応） */
    private static void chargeKatanasInHandler(IItemHandler handler, int chargeAmount) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack inner = handler.getStackInSlot(slot);
            if (inner.isEmpty()) continue;

            if (inner.getItem() == ArsenalItems.VOLTAIC_BLADE.get()) {
                int before = VoltaicBladeItem.getCharge(inner);
                if (before >= VoltaicBladeItem.MAX_CHARGE) continue;

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

    /** ItemStack が Sophisticated Backpacks のバックパックかどうか判定 */
    public static boolean isSophisticatedBackpack(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return false;
        if (!SB_MODID.equals(id.getNamespace())) return false;
        // 装飾系や upgrade item を除外: backpack を含むものだけ対象
        return id.getPath().contains("backpack");
    }
}
