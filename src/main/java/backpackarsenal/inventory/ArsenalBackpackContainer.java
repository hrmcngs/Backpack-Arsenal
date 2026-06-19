package backpackarsenal.inventory;

import backpackarsenal.init.ArsenalMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContainer;
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContext;

import java.lang.reflect.Field;

/**
 * Arsenal Backpack 専用 Container。SB の BackpackContainer を継承する。
 *
 * 過去には専用充電スロット (extra slot) を addExtraSlot で追加していたが、
 * SB の StorageContainerMenuBase 内部の slot indexing (realInventorySlots /
 * extraSlotsSize) が super() 完了後の追加と整合しないため、通常スロット 1 つ分が
 * 表示から欠ける事象が発生した。現在は extra slot を持たず、純粋に MenuType 差し替え
 * (ArsenalBackpackScreen 経由のカスタム描画) 目的のみ。
 *
 * 充電は BackpackChargingHandler が通常インベントリスロットを直接走査して行う
 * (vanilla SB と同じコードパス)。
 *
 * 注意: SB の BackpackContainer は super() 内で ModItems.BACKPACK_CONTAINER_TYPE を
 * MenuType として渡してくるため、reflection で AbstractContainerMenu.menuType フィールドを
 * 我々の MenuType (ARSENAL_BACKPACK_MENU) に上書きする。
 */
public class ArsenalBackpackContainer extends BackpackContainer {

    /** placed backpack の FE 現在量 (client side ミラー)。 placed でない場合は 0、
     *  feCapacityClient == 0 なら UI 描画を抑制。 */
    private int feStoredClient = 0;
    private int feCapacityClient = 0;
    /** 直近 10-tick (0.5 秒) の発電量。 FE/s = この値 × 2。 */
    private int feGenPerIntervalClient = 0;
    /** ctx / player を render 側からも引けるよう保持。 client で wrapper を直接読んで
     *  charger 寄与を計算するために使う ( DataSlot 経由は network truncation / 同期遅延
     *  / wrapper stale 問題が積み重なって信頼性が低かったので廃止 )。 */
    private final BackpackContext capturedCtx;
    private final Player capturedPlayer;

    public ArsenalBackpackContainer(int containerId, Player player, BackpackContext ctx) {
        super(containerId, player, ctx);
        this.capturedCtx = ctx;
        this.capturedPlayer = player;
        overrideMenuType();
        attachFeDataSlots(player, ctx);
    }

    public BackpackContext getCapturedCtx() { return capturedCtx; }
    public Player getCapturedPlayer() { return capturedPlayer; }

    /** 現在の FE 量 (client 側でミラーされた値)。 placed でない場合は 0。 */
    public int feStored() { return feStoredClient; }
    /** FE 容量 (client 側でミラーされた値)。 0 なら placed backpack ではない or BE 不在。 */
    public int feCapacity() { return feCapacityClient; }
    /** 直近 0.5 秒の発電量 (FE)。 FE/s = この値 × 2。 */
    public int feGenPerInterval() { return feGenPerIntervalClient; }

    /** 直近の multiplierContribution 結果と それを 計算した nanoTime。 */
    private int cachedMultiplierContribution = 0;
    private long cachedMultiplierAtNs = 0L;
    /** キャッシュ有効期間 ( 100 ms )。 UI 描画は 60-200 FPS で 走るが、 upgrade slot の
     *  内容変更は player 操作なので 100 ms 遅延で 視覚的に 違和感ない一方、
     *  実 計算回数は ~10 Hz まで 落ちて 大幅な CPU 節約になる。 */
    private static final long MULTIPLIER_CACHE_TTL_NS = 100_000_000L;

    /** charger 倍率寄与合計 ( client 側で wrapper を直接読んで計算 )。 multiplier = 1 + これ。
     *  held / placed 両対応。 DataSlot 経由は信頼性が低かったので 毎呼び出し計算に変更したが、
     *  render() から 毎フレーム呼ばれる ホットパスなので、 100 ms TTL で 軽くキャッシュする。 */
    public int multiplierContribution() {
        long now = System.nanoTime();
        if (now - cachedMultiplierAtNs < MULTIPLIER_CACHE_TTL_NS) {
            return cachedMultiplierContribution;
        }
        int value;
        try {
            value = sumChargerContributions(capturedCtx.getBackpackWrapper(capturedPlayer));
        } catch (Throwable t) {
            value = 0;
        }
        cachedMultiplierContribution = value;
        cachedMultiplierAtNs = now;
        return value;
    }

    /**
     * placed backpack の場合のみ、その BE が持つ {@link IEnergyStorage} を 4 個の
     * {@link DataSlot} で client に sync する (1 int = 2 short)。
     * BackpackContext$Block でない時は何もしない (= held backpack には FE は付かない)。
     */
    private void attachFeDataSlots(Player player, BackpackContext ctx) {
        if (!(ctx instanceof BackpackContext.Block blockCtx)) return;

        BlockPos pos = blockCtx.getBackpackPosition(player);
        Level level = player.level();
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return;
        IEnergyStorage storage = be.getCapability(ForgeCapabilities.ENERGY).orElse(null);
        if (storage == null) return;
        // 発電量を引くために BE → Provider lookup ( server side のみ意味がある)。
        final BlockEntity beRef = be;

        // FE current — server 側で storage.getEnergyStored() を読み、 16-bit short × 2 で sync。
        addDataSlot(new DataSlot() {
            public int get() { return storage.getEnergyStored() & 0xFFFF; }
            public void set(int v) {
                feStoredClient = (feStoredClient & 0xFFFF0000) | (v & 0xFFFF);
            }
        });
        addDataSlot(new DataSlot() {
            public int get() { return (storage.getEnergyStored() >>> 16) & 0xFFFF; }
            public void set(int v) {
                feStoredClient = (feStoredClient & 0xFFFF) | ((v & 0xFFFF) << 16);
            }
        });
        // FE capacity — 値は不変なので 1 回 sync すれば十分。
        addDataSlot(new DataSlot() {
            public int get() { return storage.getMaxEnergyStored() & 0xFFFF; }
            public void set(int v) {
                feCapacityClient = (feCapacityClient & 0xFFFF0000) | (v & 0xFFFF);
            }
        });
        addDataSlot(new DataSlot() {
            public int get() { return (storage.getMaxEnergyStored() >>> 16) & 0xFFFF; }
            public void set(int v) {
                feCapacityClient = (feCapacityClient & 0xFFFF) | ((v & 0xFFFF) << 16);
            }
        });
        // FE generation rate ( 直近 10-tick 発電量、 0.5 秒分 )。 16-bit 二分割。
        addDataSlot(new DataSlot() {
            public int get() {
                var p = backpackarsenal.energy.BackpackFeEvents.getProvider(beRef);
                return p == null ? 0 : (p.lastGenPerInterval & 0xFFFF);
            }
            public void set(int v) {
                feGenPerIntervalClient = (feGenPerIntervalClient & 0xFFFF0000) | (v & 0xFFFF);
            }
        });
        addDataSlot(new DataSlot() {
            public int get() {
                var p = backpackarsenal.energy.BackpackFeEvents.getProvider(beRef);
                return p == null ? 0 : ((p.lastGenPerInterval >>> 16) & 0xFFFF);
            }
            public void set(int v) {
                feGenPerIntervalClient = (feGenPerIntervalClient & 0xFFFF) | ((v & 0xFFFF) << 16);
            }
        });
    }

    /** {@link backpackarsenal.event.BackpackChargingHandler#sumVoltaicChargerContributions}
     *  と同一ロジック。 サーバー側 gen 計算はこの形で正しく動いているので UI sync も
     *  揃える ( raw slot 走査だと SB 内部の何かが噛んで 0 を返すケースが観測された )。 */
    private static int sumChargerContributions(
            net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper wrapper) {
        if (wrapper == null) return 0;
        try {
            int s = 0;
            for (var w : wrapper.getUpgradeHandler().getTypeWrappers(
                    backpackarsenal.upgrade.VoltaicChargerUpgradeItem.TYPE)) {
                if (w != null && w.isEnabled()) s += w.getMultiplierContribution();
            }
            for (var w : wrapper.getUpgradeHandler().getTypeWrappers(
                    backpackarsenal.upgrade.VoltaicGrowthUpgradeItem.TYPE)) {
                if (w != null && w.isEnabled()) s += w.getMultiplierContribution();
            }
            return s;
        } catch (Throwable t) {
            return 0;
        }
    }

    /** AbstractContainerMenu.menuType を private final ごと reflection で上書き。 */
    private void overrideMenuType() {
        Throwable mojangFailure = null;
        try {
            Field f = AbstractContainerMenu.class.getDeclaredField("menuType");
            f.setAccessible(true);
            f.set(this, ArsenalMenuTypes.ARSENAL_BACKPACK_MENU.get());
            return;
        } catch (Throwable t) {
            mojangFailure = t;
        }
        try {
            Field f = AbstractContainerMenu.class.getDeclaredField("f_38840_");
            f.setAccessible(true);
            f.set(this, ArsenalMenuTypes.ARSENAL_BACKPACK_MENU.get());
            return;
        } catch (Throwable fallback) {
            backpackarsenal.BackpackArsenalMod.LOGGER.error(
                "[backpack_arsenal] overrideMenuType FAILED: mojang ex={}, srg ex={}",
                mojangFailure, fallback);
            throw new RuntimeException(
                "[backpack_arsenal] Failed to override menuType via reflection.", fallback);
        }
    }

    /** MenuType の network factory 用。 */
    public static ArsenalBackpackContainer fromBuffer(int id, Inventory inv, FriendlyByteBuf buf) {
        net.minecraft.world.level.Level level = inv.player.level();
        BackpackContext ctx = BackpackContext.fromBuffer(buf, level);
        return new ArsenalBackpackContainer(id, inv.player, ctx);
    }
}
