package backpackarsenal.inventory;

import backpackarsenal.init.ArsenalMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
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

    public ArsenalBackpackContainer(int containerId, Player player, BackpackContext ctx) {
        super(containerId, player, ctx);
        backpackarsenal.BackpackArsenalMod.LOGGER.info(
            "[backpack_arsenal] ArsenalBackpackContainer ctor: side={}, id={}",
            player.level().isClientSide ? "CLIENT" : "SERVER", containerId);
        overrideMenuType();
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
