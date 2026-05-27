package backpackarsenal.client;

import backpackarsenal.inventory.ArsenalBackpackContainer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.p3pp3rf1y.sophisticatedbackpacks.client.gui.BackpackScreen;

/**
 * Arsenal Backpack 用 Screen。SB の BackpackScreen を継承し、専用充電スロットの
 * 背景枠を追加描画する。SB のスロット描画は menu.slots を順に回るので、
 * addExtraSlot で足したスロットも自動で描画される。背景画像が無いと
 * スロット枠が描かれず浮いて見えるので、ここで vanilla の slot 背景を上書き描く。
 */
public class ArsenalBackpackScreen extends BackpackScreen {

    /** vanilla の inventory.png — slot 18x18 を含む */
    private static final ResourceLocation VANILLA_INVENTORY =
        new ResourceLocation("textures/gui/container/inventory.png");

    public ArsenalBackpackScreen(ArsenalBackpackContainer container, Inventory inv, Component title) {
        super(container, inv, title);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partial) {
        super.render(gfx, mouseX, mouseY, partial);
        drawChargeSlotBackground(gfx);
    }

    /** vanilla の inventory.png から空スロット 18x18 を切り出して charge slot 位置に重ねる。 */
    private void drawChargeSlotBackground(GuiGraphics gfx) {
        int x = leftPos + ArsenalBackpackContainer.CHARGE_SLOT_X - 1;
        int y = topPos + ArsenalBackpackContainer.CHARGE_SLOT_Y - 1;
        // inventory.png 内の hotbar slot 領域 (7,141) を 18x18 で切り出して再利用
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        gfx.blit(VANILLA_INVENTORY, x, y, 7, 141, 18, 18);
    }
}
