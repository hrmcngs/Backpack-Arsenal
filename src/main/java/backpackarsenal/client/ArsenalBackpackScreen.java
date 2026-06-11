package backpackarsenal.client;

import backpackarsenal.inventory.ArsenalBackpackContainer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
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

        // placed backpack なら FE 残量を表示。 held backpack (feCapacity == 0) では描画しない。
        ArsenalBackpackContainer menu = (ArsenalBackpackContainer) this.menu;
        int feMax = menu.feCapacity();
        if (feMax > 0) {
            int fe = menu.feStored();
            int pct = Math.round(100f * fe / feMax);

            // バー: 幅 100px、 高さ 4px、 タイトル直下に。
            int barX = leftPos + 8;
            int barY = topPos + 4;
            int barW = 100;
            int barH = 4;
            int filled = Math.round(barW * (fe / (float) feMax));
            // 枠
            gfx.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xFF202020);
            // 背景 (空き)
            gfx.fill(barX, barY, barX + barW, barY + barH, 0xFF404040);
            // 充電量 (青系)
            if (filled > 0) {
                gfx.fill(barX, barY, barX + filled, barY + barH, 0xFF55BBFF);
            }

            // 数値テキスト (バー右隣)
            String text = ChatFormatting.AQUA + String.format("%,d / %,d FE (%d%%)", fe, feMax, pct);
            gfx.drawString(this.font, text, barX + barW + 4, barY - 1, 0xFFFFFF, false);
        }
    }
}
