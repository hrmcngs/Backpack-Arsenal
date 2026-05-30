package backpackarsenal.init;

import backpackarsenal.BackpackArsenalMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * GUI レイアウト / インベントリサイズ等を JSON ファイルで編集可能にする設定クラス。
 *
 * 設定ファイル:
 *   <minecraft>/config/backpack_arsenal.json
 *
 * 起動時に自動生成 (無ければデフォルト値で書き出し)、起動毎に読み込み。
 * 値を編集したらゲーム再起動で反映。
 *
 * Python ヘルパー (tools/edit_config.py) で対話的にも編集可能。
 *
 * フィールド説明:
 *   inventorySlots   — backpack 内インベントリスロット数 (default 9)
 *   upgradeSlots     — backpack 内 upgrade スロット数 (default 4)
 *   chargeSlotX      — 専用充電スロットの X 座標 (GUI 左上基準, default -19)
 *   chargeSlotY      — 専用充電スロットの Y 座標 (GUI 左上基準, default 8)
 *   perSlotStackLimit — 通常スロット 1 マスあたりの最大スタック数 (default 9)
 */
public class ArsenalBackpackConfig {

    // ===== デフォルト値 =====
    public static final int DEFAULT_INVENTORY_SLOTS    = 9;
    public static final int DEFAULT_UPGRADE_SLOTS      = 4;
    public static final int DEFAULT_CHARGE_SLOT_X      = -19;
    public static final int DEFAULT_CHARGE_SLOT_Y      = 8;
    public static final int DEFAULT_PER_SLOT_STACK_LIMIT = 9;

    // ===== 実行時値 (load() で上書きされる) =====
    public static int inventorySlots    = DEFAULT_INVENTORY_SLOTS;
    public static int upgradeSlots      = DEFAULT_UPGRADE_SLOTS;
    public static int chargeSlotX       = DEFAULT_CHARGE_SLOT_X;
    public static int chargeSlotY       = DEFAULT_CHARGE_SLOT_Y;
    public static int perSlotStackLimit = DEFAULT_PER_SLOT_STACK_LIMIT;

    private static final String FILE_NAME = "backpack_arsenal.json";

    /** mod 起動時に呼ぶ。ファイルが無ければ default を書き出し、あれば読み込み。 */
    public static void load() {
        Path configDir = FMLPaths.CONFIGDIR.get();
        Path file = configDir.resolve(FILE_NAME);

        if (!Files.exists(file)) {
            writeDefault(file);
            BackpackArsenalMod.LOGGER.info("[{}] Created default config at {}",
                BackpackArsenalMod.MODID, file);
            return;
        }

        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            inventorySlots    = readInt(obj, "inventorySlots",    DEFAULT_INVENTORY_SLOTS);
            upgradeSlots      = readInt(obj, "upgradeSlots",      DEFAULT_UPGRADE_SLOTS);
            chargeSlotX       = readInt(obj, "chargeSlotX",       DEFAULT_CHARGE_SLOT_X);
            chargeSlotY       = readInt(obj, "chargeSlotY",       DEFAULT_CHARGE_SLOT_Y);
            perSlotStackLimit = readInt(obj, "perSlotStackLimit", DEFAULT_PER_SLOT_STACK_LIMIT);
            BackpackArsenalMod.LOGGER.info(
                "[{}] Loaded config: inv={}, upgrade={}, chargeSlot=({},{}), stackLimit={}",
                BackpackArsenalMod.MODID,
                inventorySlots, upgradeSlots, chargeSlotX, chargeSlotY, perSlotStackLimit);
        } catch (Exception e) {
            BackpackArsenalMod.LOGGER.error(
                "[{}] Failed to load config, using defaults", BackpackArsenalMod.MODID, e);
            resetToDefaults();
        }
    }

    /** ディスクから再読込 (`/backpack_arsenal reload` コマンド用) */
    public static void reload() {
        load();
    }

    /** デフォルト値を JSON で書き出し (コメント代わりに `_help` キーも入れる) */
    private static void writeDefault(Path file) {
        try {
            Files.createDirectories(file.getParent());
            JsonObject obj = new JsonObject();
            obj.addProperty("_help_1", "Backpack Arsenal config — edit values and restart game");
            obj.addProperty("_help_2", "OR use /backpack_arsenal reload (op only) for hot reload");
            obj.addProperty("inventorySlots",    DEFAULT_INVENTORY_SLOTS);
            obj.addProperty("upgradeSlots",      DEFAULT_UPGRADE_SLOTS);
            obj.addProperty("chargeSlotX",       DEFAULT_CHARGE_SLOT_X);
            obj.addProperty("chargeSlotY",       DEFAULT_CHARGE_SLOT_Y);
            obj.addProperty("perSlotStackLimit", DEFAULT_PER_SLOT_STACK_LIMIT);
            obj.addProperty("_help_3", "chargeSlotX/Y は GUI 左上 (leftPos, topPos) からのオフセット (px)");
            obj.addProperty("_help_4", "upgrade 列の X は -19, Y は 8/26/44/62 (4 slot 時)");
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(file, gson.toJson(obj), StandardCharsets.UTF_8);
        } catch (IOException e) {
            BackpackArsenalMod.LOGGER.error(
                "[{}] Failed to write default config", BackpackArsenalMod.MODID, e);
        }
    }

    private static void resetToDefaults() {
        inventorySlots    = DEFAULT_INVENTORY_SLOTS;
        upgradeSlots      = DEFAULT_UPGRADE_SLOTS;
        chargeSlotX       = DEFAULT_CHARGE_SLOT_X;
        chargeSlotY       = DEFAULT_CHARGE_SLOT_Y;
        perSlotStackLimit = DEFAULT_PER_SLOT_STACK_LIMIT;
    }

    private static int readInt(JsonObject obj, String key, int def) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            try {
                return obj.get(key).getAsInt();
            } catch (Exception ignored) {}
        }
        return def;
    }
}
