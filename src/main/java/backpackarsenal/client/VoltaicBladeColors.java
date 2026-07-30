package backpackarsenal.client;

import backpackarsenal.BackpackArsenalMod;
import backpackarsenal.init.ArsenalItems;
import backpackarsenal.item.ArsenalBackpackItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 納刀 (saya) の柄を染色対応にする ItemColor 登録。
 *
 * 染色システム:
 *   voltaic_blade は the_four_primitives の拵え (koshirae) 染色対象
 *   ({@code koshirae/fitting_dyeable} タグ)。 抜刀モデル (voltaic_blade.json) は
 *   親MOD の {@code KatanaColorClient} が SwordItem として自動で着色するため、
 *   ここでは登録しない (二重登録は最後勝ちで競合するため)。
 *
 *   tintindex 規約 (親MODと共通): 1 = 柄(tsuka), 2 = grip(tsuba 相当)。
 *
 * saya (納刀) は SayaBackpackOverlay がバックパックの ItemStack として描画するため、
 * 親の KatanaColorClient (voltaic_blade 用) は効かない。 収納中 blade の拵え色を
 * バックパック NBT へ同期した値 ({@link ArsenalBackpackItem#getSyncedTsukaColor} /
 * {@link ArsenalBackpackItem#getSyncedTsubaColor}) で着色する。
 */
@Mod.EventBusSubscriber(modid = BackpackArsenalMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VoltaicBladeColors {

    private VoltaicBladeColors() {}

    /** RGB を不透明な乗算色へ。 無効 (-1) は白 = tint 無し。 */
    private static int tint(int rgb) {
        return rgb < 0 ? 0xFFFFFFFF : (0xFF000000 | rgb);
    }

    // TODO debug: tintindex毎に client が受け取った色が変わった時だけログ (確認後に削除)
    private static final java.util.Map<Integer,Integer> LAST_LOGGED = new java.util.HashMap<>();
    private static int logChange(int tintIndex, int color) {
        Integer prev = LAST_LOGGED.get(tintIndex);
        if (prev == null || prev != color) {
            LAST_LOGGED.put(tintIndex, color);
            backpackarsenal.BackpackArsenalMod.LOGGER.info(
                "[dye-debug] client tint{} -> color={}", tintIndex, color);
        }
        return color;
    }

    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        // バックパック (saya overlay は backpack stack として描画される)。
        //   本体バックパックモデルが tintindex 0/1 を使うため、 saya モデルの柄は 2/3 に割り当てて衝突回避。
        //     tintindex 2 = 納刀中の柄(tsuka), 3 = grip(tsuba)。 収納 voltaic_blade の同期色を使う。
        //   本体モデルの 0/1 は default で白 (tint 無し) = 従来通り。
        event.register((stack, tintIndex) -> {
            switch (tintIndex) {
                case 2:  return tint(logChange(2, ArsenalBackpackItem.getSyncedTsukaColor(stack)));
                case 3:  return tint(logChange(3, ArsenalBackpackItem.getSyncedTsubaColor(stack)));
                default: return 0xFFFFFFFF;
            }
        }, ArsenalItems.ARSENAL_BACKPACK_ELECTRON.get());
    }
}
