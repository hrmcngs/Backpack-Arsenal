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

    /** Mekanism の純正 FE タブアイコン ( assets/mekanism/gui/tabs/energy_info_fe.png 26×26 )。
     *  緑の雷マーク + "FE" 文字。 gui/energy.png は別物 ( anvil 風 ) なので使わない。 */
    private static final ResourceLocation MEK_ENERGY_ICON =
        new ResourceLocation("mekanism", "gui/tabs/energy_info_fe.png");

    public ArsenalBackpackScreen(ArsenalBackpackContainer container, Inventory inv, Component title) {
        super(container, inv, title);
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partial, int mouseX, int mouseY) {
        super.renderBg(gfx, partial, mouseX, mouseY);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        gfx.blit(CUSTOM_BG, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
    }

    /**
     * FE 表示は {@link #render} 側で super 後に描画する。
     * {@link #renderBg} で描くと SB の title / slot の上にスロット ItemStack や
     * tooltip 描画が後から重なって覆い隠されるので、 最上位で描く必要がある。
     */
    /** FE アイコンサイズ ( Mekanism の純正タブアイコンに合わせて 26×26 )。 */
    private static final int FE_ICON_SIZE = 26;

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partial) {
        super.render(gfx, mouseX, mouseY, partial);
        ArsenalBackpackContainer menu = (ArsenalBackpackContainer) this.menu;

        // FE アイコン位置: GUI 真下、 左端から少しオフセットしたところ。
        //   ( player inventory より下なので JEI / 創造インベントリ と重ならない )。
        //   下側パネル方式と同じ位置を踏襲。
        int iconX = leftPos + 4;
        int iconY = topPos + imageHeight + 2;

        // Mekanism 純正テクスチャを blit ( 緑の雷 + "FE" 文字 )。
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        gfx.blit(MEK_ENERGY_ICON, iconX, iconY, 0, 0, FE_ICON_SIZE, FE_ICON_SIZE, FE_ICON_SIZE, FE_ICON_SIZE);

        // ============== アイコン右に「発電量」だけ常時表示 ==============
        //   multiplierContribution() は client side で wrapper を直接読んで計算するので
        //   held / placed どちらでも正しい multiplier が即座に取れる ( DataSlot 同期遅延 /
        //   16-bit network truncation の影響を受けない )。
        int multiplierTotal = 1 + menu.multiplierContribution();
        long genPerTick = (long) backpackarsenal.init.ArsenalBackpackConfig.feGenPerTick
                * multiplierTotal;
        int textX = iconX + FE_ICON_SIZE + 4;
        int textY = iconY + (FE_ICON_SIZE - this.font.lineHeight) / 2; // 縦中央寄せ
        gfx.drawString(this.font,
            ChatFormatting.GREEN + "+" + formatEnergyPerTick(genPerTick),
            textX, textY, 0xFFFFFF, false);

        // ============== アイコンホバーで tooltip ( 発電量だけ ) ==============
        if (mouseX >= iconX && mouseX < iconX + FE_ICON_SIZE
            && mouseY >= iconY && mouseY < iconY + FE_ICON_SIZE) {
            gfx.renderTooltip(this.font,
                java.util.List.of(Component.literal(
                    ChatFormatting.WHITE + "Output: " + ChatFormatting.AQUA
                    + formatEnergyPerTick(genPerTick))),
                java.util.Optional.empty(), mouseX, mouseY);
        }
    }

    /** "X.YY {prefix}FE/t" 形式 ( Mekanism の Energy Cube tooltip と同じ )。 prefix は k/M/G/T。 */
    private static String formatEnergyPerTick(long n) {
        return formatEnergy(n) + "/t";
    }

    /** "X.YY {prefix}FE" 形式。 prefix は数値ではなく単位側につく ( "3.27 MFE" )。 */
    private static String formatEnergy(long n) {
        if (n < 1_000L) return n + " FE";
        if (n < 1_000_000L) return String.format("%.2f kFE", n / 1_000.0);
        if (n < 1_000_000_000L) return String.format("%.2f MFE", n / 1_000_000.0);
        if (n < 1_000_000_000_000L) return String.format("%.2f GFE", n / 1_000_000_000.0);
        return String.format("%.2f TFE", n / 1_000_000_000_000.0);
    }

}
