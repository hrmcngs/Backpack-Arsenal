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

    /** 充電スロットの座標 (Slot 内部用 — Screen 側もこの座標基準で背景描画)。
     *  upgrade slot 列 (x=-19 付近, y=8..98 で 5 スロット) と重なると不可視になるので、
     *  upgrade slot 列の下に置く。Y=104 で 5 スロット (y=98 末尾) との間に 6px の隙間。 */
    public static final int CHARGE_SLOT_X = -22;
    public static final int CHARGE_SLOT_Y = 104;

    private final ChargeSlotInventory chargeInventory;

    public ArsenalBackpackContainer(int containerId, Player player, BackpackContext ctx) {
        super(containerId, player, ctx);
        overrideMenuType();

        IBackpackWrapper wrapper = ctx.getBackpackWrapper(player);
        ItemStack backpackStack = wrapper.getBackpack();
        this.chargeInventory = new ChargeSlotInventory(backpackStack);

        addExtraSlot(new SlotItemHandler(chargeInventory, 0, CHARGE_SLOT_X, CHARGE_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return chargeInventory.isItemValid(0, stack);
            }
        });
    }

    /** AbstractContainerMenu.menuType を private final ごと reflection で上書き。 */
    private void overrideMenuType() {
        try {
            Field f = AbstractContainerMenu.class.getDeclaredField("menuType");
            f.setAccessible(true);
            f.set(this, ArsenalMenuTypes.ARSENAL_BACKPACK_MENU.get());
        } catch (NoSuchFieldException e) {
            // SRG 環境ではフィールド名が違う可能性。よくある SRG 名にフォールバック。
            try {
                Field f = AbstractContainerMenu.class.getDeclaredField("f_38840_");
                f.setAccessible(true);
                f.set(this, ArsenalMenuTypes.ARSENAL_BACKPACK_MENU.get());
            } catch (Exception fallback) {
                throw new RuntimeException(
                    "[backpack_arsenal] Failed to override menuType via reflection. " +
                    "MC mapping changed? Need to update field name.", fallback);
            }
        } catch (Exception e) {
            throw new RuntimeException(
                "[backpack_arsenal] Failed to override menuType via reflection.", e);
        }
    }

    /** MenuType の network factory 用。 */
    public static ArsenalBackpackContainer fromBuffer(int id, Inventory inv, FriendlyByteBuf buf) {
        BackpackContext ctx = BackpackContext.fromBuffer(buf, inv.player.level());
        return new ArsenalBackpackContainer(id, inv.player, ctx);
    }

    public ChargeSlotInventory getChargeInventory() {
        return chargeInventory;
    }
}
