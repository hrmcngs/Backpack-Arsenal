package backpackarsenal.client;

import backpackarsenal.BackpackArsenalMod;
import backpackarsenal.init.ArsenalItems;
import backpackarsenal.item.ArsenalBackpackItem;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * voltaic_blade の柄 (tuka + grip) を染色対応にする ItemColor 登録。
 *
 * 染色の仕組み:
 *   {@link backpackarsenal.item.VoltaicBladeItem} が {@link DyeableLeatherItem} を実装しているため、
 *   バニラの {@code crafting_special_armordye} レシピで「本体 + 染料」を作業台に置くと
 *   NBT {@code display.color} に色が焼き込まれる (革防具と同じ)。
 *
 * tint 対象:
 *   - 抜刀モデル (voltaic_blade.json)         : 柄 = tintindex 0 → 本体の display.color
 *   - 納刀モデル (saya_voltaic_blade.json)     : 柄 = tintindex 2
 *       ・バックパック overlay で描画される時は ItemStack が「バックパック」なので
 *         収納中 voltaic_blade の同期色 ({@link ArsenalBackpackItem#getSyncedVoltaicColor}) を使う。
 *       ・MAW 等で voltaic_blade 自身の stack として描画される場合に備え、
 *         本体側 provider も tintindex 2 を本体色として扱う。
 *   いずれも未染色なら -1 (= 乗算なし = 元テクスチャそのまま)。刃 (tint 無し) は非対象。
 */
@Mod.EventBusSubscriber(modid = BackpackArsenalMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VoltaicBladeColors {

    private VoltaicBladeColors() {}

    /** display.color を不透明な乗算色に変換 (未染色は -1)。 */
    private static int opaqueOrNoTint(int rgb) {
        return rgb == -1 ? -1 : (0xFF000000 | rgb);
    }

    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        // 本体 (抜刀 tintindex 0 / 納刀を自 stack 描画する経路 tintindex 2)
        event.register((stack, tintIndex) -> {
            if (tintIndex != 0 && tintIndex != 2) {
                return -1;
            }
            if (stack.getItem() instanceof DyeableLeatherItem dyeable && dyeable.hasCustomColor(stack)) {
                return opaqueOrNoTint(dyeable.getColor(stack));
            }
            return -1;
        }, ArsenalItems.VOLTAIC_BLADE.get());

        // バックパック (saya overlay は backpack stack として描画される)。
        // tintindex 2 = 納刀中の柄。 収納 voltaic_blade の同期色を使う。
        // tintindex 0 / 1 はバックパック本体モデル用なので tint しない (-1)。
        event.register((stack, tintIndex) -> {
            if (tintIndex != 2) {
                return -1;
            }
            return opaqueOrNoTint(ArsenalBackpackItem.getSyncedVoltaicColor(stack));
        }, ArsenalItems.ARSENAL_BACKPACK_ELECTRON.get());
    }
}
