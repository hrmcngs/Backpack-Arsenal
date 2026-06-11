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
 */
public class ArsenalBackpackConfig {

    // ===== デフォルト値 =====
    public static final int DEFAULT_INVENTORY_SLOTS    = 9;
    public static final int DEFAULT_UPGRADE_SLOTS      = 4;
    /** 設置時 FE 内部バッファの最大容量。 Mekanism cable / 他 FE pipe が吸う。
     *  Integer.MAX_VALUE ( ~2.14 GFE ) で実質無制限。 これで high-multiplier ( Lv10000+ ×
     *  4 slot = ~80 MFE/t 発電 ) でも buffer の "1 tick 分の受け入れ容量" が cable の
     *  ボトルネックにならず、 cable / 消費機械の input cap が真の上限になる。 */
    public static final int DEFAULT_FE_CAPACITY        = Integer.MAX_VALUE;
    /** voltaic_blade 充電中の tick あたり発電量 (FE)。 per-tick fastTick が
     *  この値 × multiplier を毎 tick storage に加算する。 */
    public static final int DEFAULT_FE_GEN_PER_TICK    = 2_000;
    /** 外部パイプが 1 tick で吸い出せる FE の上限。 実質無制限 ( int 上限 ) にしておき、
     *  ボトルネックは消費機械側の input cap で決まるようにする。 high-multiplier ( Lv1000+ )
     *  の growth charger 構成でも storage saturate しないようにするため。 */
    public static final int DEFAULT_FE_MAX_EXTRACT     = Integer.MAX_VALUE;

    // ===== 実行時値 (load() で上書きされる) =====
    public static int inventorySlots    = DEFAULT_INVENTORY_SLOTS;
    public static int upgradeSlots      = DEFAULT_UPGRADE_SLOTS;
    public static int feCapacity        = DEFAULT_FE_CAPACITY;
    public static int feGenPerTick      = DEFAULT_FE_GEN_PER_TICK;
    public static int feMaxExtract      = DEFAULT_FE_MAX_EXTRACT;

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
            feCapacity        = readInt(obj, "feCapacity",        DEFAULT_FE_CAPACITY);
            feGenPerTick      = readInt(obj, "feGenPerTick",      DEFAULT_FE_GEN_PER_TICK);
            feMaxExtract      = readInt(obj, "feMaxExtract",      DEFAULT_FE_MAX_EXTRACT);

            // 旧 default 値 ( feCapacity=100k / feMaxExtract=5k ) の自動アップグレード。
            // 旧 default 以下なら未編集の旧 config と見なし、 新 default に上書きする。
            // ユーザーがカスタム値を入れてた場合は新 default 超えてれば触らない。
            boolean upgraded = false;
            // 旧 default ( 100k / 10M ) は新 default ( Integer.MAX_VALUE ) にアップグレード。
            if (feCapacity <= 10_000_000) { feCapacity = DEFAULT_FE_CAPACITY; upgraded = true; }
            if (feMaxExtract <= 1_000_000) { feMaxExtract = DEFAULT_FE_MAX_EXTRACT; upgraded = true; }
            if (upgraded) {
                BackpackArsenalMod.LOGGER.info(
                    "[{}] Upgraded FE config to new defaults (feCap={}, feOut={}). Saving.",
                    BackpackArsenalMod.MODID, feCapacity, feMaxExtract);
                save();
            }
            BackpackArsenalMod.LOGGER.info(
                "[{}] Loaded config: inv={}, upgrade={}, feCap={}, feGen={}, feOut={}",
                BackpackArsenalMod.MODID, inventorySlots, upgradeSlots,
                feCapacity, feGenPerTick, feMaxExtract);
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

    /** 現在の static field 値を JSON にディスク書き込み (in-game editor 用) */
    public static void save() {
        Path file = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
        try {
            Files.createDirectories(file.getParent());
            JsonObject obj = new JsonObject();
            obj.addProperty("_help_1", "Backpack Arsenal config — edit values and restart game");
            obj.addProperty("_help_2", "OR use /backpack_arsenal reload (op only) for hot reload");
            obj.addProperty("inventorySlots", inventorySlots);
            obj.addProperty("upgradeSlots",   upgradeSlots);
            obj.addProperty("feCapacity",     feCapacity);
            obj.addProperty("feGenPerTick",   feGenPerTick);
            obj.addProperty("feMaxExtract",   feMaxExtract);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(file, gson.toJson(obj), StandardCharsets.UTF_8);
            BackpackArsenalMod.LOGGER.info("[{}] Saved config: inv={}, upgrade={}, fe={}/{}/{}",
                BackpackArsenalMod.MODID, inventorySlots, upgradeSlots,
                feCapacity, feGenPerTick, feMaxExtract);
        } catch (IOException e) {
            BackpackArsenalMod.LOGGER.error(
                "[{}] Failed to save config", BackpackArsenalMod.MODID, e);
        }
    }

    /** デフォルト値を JSON で書き出し (コメント代わりに `_help` キーも入れる) */
    private static void writeDefault(Path file) {
        try {
            Files.createDirectories(file.getParent());
            JsonObject obj = new JsonObject();
            obj.addProperty("_help_1", "Backpack Arsenal config — edit values and restart game");
            obj.addProperty("_help_2", "OR use /backpack_arsenal reload (op only) for hot reload");
            obj.addProperty("inventorySlots", DEFAULT_INVENTORY_SLOTS);
            obj.addProperty("upgradeSlots",   DEFAULT_UPGRADE_SLOTS);
            obj.addProperty("feCapacity",     DEFAULT_FE_CAPACITY);
            obj.addProperty("feGenPerTick",   DEFAULT_FE_GEN_PER_TICK);
            obj.addProperty("feMaxExtract",   DEFAULT_FE_MAX_EXTRACT);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(file, gson.toJson(obj), StandardCharsets.UTF_8);
        } catch (IOException e) {
            BackpackArsenalMod.LOGGER.error(
                "[{}] Failed to write default config", BackpackArsenalMod.MODID, e);
        }
    }

    private static void resetToDefaults() {
        inventorySlots = DEFAULT_INVENTORY_SLOTS;
        upgradeSlots   = DEFAULT_UPGRADE_SLOTS;
        feCapacity     = DEFAULT_FE_CAPACITY;
        feGenPerTick   = DEFAULT_FE_GEN_PER_TICK;
        feMaxExtract   = DEFAULT_FE_MAX_EXTRACT;
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
