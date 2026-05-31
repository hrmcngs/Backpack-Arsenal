package backpackarsenal.client;

import backpackarsenal.inventory.ArsenalBackpackContainer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.p3pp3rf1y.sophisticatedbackpacks.client.gui.BackpackScreen;

/**
 * Arsenal Backpack 用 Screen。SB の BackpackScreen を継承する。
 *
 * 過去には専用充電スロット + F8 編集モードを実装していたが、SB の slot indexing と
 * 整合しないため通常スロットが欠ける事象が発生したので撤去した。現在は SB 標準パネルの
 * 上にユーザー編集可能な透明 PNG (arsenal_backpack_bg.png) を重ねるだけのシンプル構成。
 *
 * カスタム見た目を入れたい時は assets/backpack_arsenal/textures/gui/arsenal_backpack_bg.png
 * を編集する。完全に透明なら SB のパネルがそのまま見える。
 */
public class ArsenalBackpackScreen extends BackpackScreen {

    /** 独自パネル overlay テクスチャ (assets/backpack_arsenal/textures/gui/arsenal_backpack_bg.png) — 256x256 PNG。
     *  SB のパネル背景に上から被せる。透明なら SB のパネルがそのまま見える。 */
    private static final ResourceLocation CUSTOM_BG =
        new ResourceLocation("backpack_arsenal", "textures/gui/arsenal_backpack_bg.png");

    public ArsenalBackpackScreen(ArsenalBackpackContainer container, Inventory inv, Component title) {
        super(container, inv, title);
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partial, int mouseX, int mouseY) {
        super.renderBg(gfx, partial, mouseX, mouseY);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        gfx.blit(CUSTOM_BG, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
    }
}
