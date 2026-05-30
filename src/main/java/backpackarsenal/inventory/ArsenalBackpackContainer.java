package backpackarsenal.inventory;

import backpackarsenal.init.ArsenalMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContainer;
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContext;

import java.lang.reflect.Field;

/**
 * Arsenal Backpack 専用 Container。SB の BackpackContainer を継承し、
 * 専用充電スロット (voltaic_blade 限定 1スロット) を addExtraSlot で追加する。
 *
 * 注意: SB の BackpackContainer は super() 内で ModItems.BACKPACK_CONTAINER_TYPE を
 * MenuType として渡してくるため、そのままだと client/server で同期する MenuType が
 * SB のものになり、client 側で SB factory で再構築 → 我々のスロットが消える。
 * これを回避するため reflection で AbstractContainerMenu.menuType フィールドを
 * 我々の MenuType (ArsenalMenuTypes.ARSENAL_BACKPACK_MENU) に上書きする。
 */
public class ArsenalBackpackContainer extends BackpackContainer {

    /** 充電スロットの座標は ArsenalBackpackConfig から取得 (config/backpack_arsenal.json 編集可)。
     *  default は upgrade 列の一番上 (X=-19, Y=8)。
     *  これらは Screen 側の背景描画でも参照する (互換のため public static のまま、しかし
     *  実際は Config の値を使う). */
    public static int CHARGE_SLOT_X = backpackarsenal.init.ArsenalBackpackConfig.DEFAULT_CHARGE_SLOT_X;
    public static int CHARGE_SLOT_Y = backpackarsenal.init.ArsenalBackpackConfig.DEFAULT_CHARGE_SLOT_Y;

    private final ChargeSlotInventory chargeInventory;

    public ArsenalBackpackContainer(int containerId, Player player, BackpackContext ctx) {
        super(containerId, player, ctx);
        // Config の最新値を取得 (reload した直後にもこの GUI 構築で反映される)
        int slotX = backpackarsenal.init.ArsenalBackpackConfig.chargeSlotX;
        int slotY = backpackarsenal.init.ArsenalBackpackConfig.chargeSlotY;
        // 互換のため static field も同期させる (Screen 側の背景描画が参照)
        CHARGE_SLOT_X = slotX;
        CHARGE_SLOT_Y = slotY;
        backpackarsenal.BackpackArsenalMod.LOGGER.info(
            "[backpack_arsenal] ArsenalBackpackContainer ctor: side={}, id={}, chargeSlot=({},{})",
            player.level().isClientSide ? "CLIENT" : "SERVER", containerId, slotX, slotY);
        overrideMenuType();

        IBackpackWrapper wrapper = ctx.getBackpackWrapper(player);
        ItemStack backpackStack = wrapper.getBackpack();
        this.chargeInventory = new ChargeSlotInventory(backpackStack);

        addExtraSlot(new SlotItemHandler(chargeInventory, 0, slotX, slotY) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return chargeInventory.isItemValid(0, stack);
            }
        });
    }

    /** AbstractContainerMenu.menuType を private final ごと reflection で上書き。 */
    private void overrideMenuType() {
        Throwable mojangFailure = null;
        try {
            Field f = AbstractContainerMenu.class.getDeclaredField("menuType");
            f.setAccessible(true);
            f.set(this, ArsenalMenuTypes.ARSENAL_BACKPACK_MENU.get());
            backpackarsenal.BackpackArsenalMod.LOGGER.info(
                "[backpack_arsenal] overrideMenuType: 'menuType' field set OK");
            return;
        } catch (Throwable t) {
            mojangFailure = t;
        }
        // SRG 環境ではフィールド名が違う可能性。よくある SRG 名にフォールバック。
        try {
            Field f = AbstractContainerMenu.class.getDeclaredField("f_38840_");
            f.setAccessible(true);
            f.set(this, ArsenalMenuTypes.ARSENAL_BACKPACK_MENU.get());
            backpackarsenal.BackpackArsenalMod.LOGGER.info(
                "[backpack_arsenal] overrideMenuType: 'f_38840_' field set OK");
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
        var log = backpackarsenal.BackpackArsenalMod.LOGGER;
        log.info("[backpack_arsenal] fromBuffer step 1: id={}, inv={}, buf.size={}",
            id, inv, buf.readableBytes());
        log.info("[backpack_arsenal] fromBuffer step 2: inv.player={}", inv.player);
        net.minecraft.world.level.Level level;
        try {
            level = inv.player.level();
            log.info("[backpack_arsenal] fromBuffer step 3: level={}", level);
        } catch (Throwable t) {
            log.error("[backpack_arsenal] fromBuffer step 3 FAILED", t);
            throw new RuntimeException("fromBuffer step 3", t);
        }
        BackpackContext ctx;
        try {
            ctx = BackpackContext.fromBuffer(buf, level);
            log.info("[backpack_arsenal] fromBuffer step 4: ctx={}", ctx);
        } catch (Throwable t) {
            log.error("[backpack_arsenal] fromBuffer step 4 FAILED (BackpackContext.fromBuffer)", t);
            throw new RuntimeException("fromBuffer step 4", t);
        }
        try {
            ArsenalBackpackContainer c = new ArsenalBackpackContainer(id, inv.player, ctx);
            log.info("[backpack_arsenal] fromBuffer step 5: ctor OK");
            return c;
        } catch (Throwable t) {
            log.error("[backpack_arsenal] fromBuffer step 5 FAILED (ctor)", t);
            throw new RuntimeException("fromBuffer step 5", t);
        }
    }

    public ChargeSlotInventory getChargeInventory() {
        return chargeInventory;
    }
}
