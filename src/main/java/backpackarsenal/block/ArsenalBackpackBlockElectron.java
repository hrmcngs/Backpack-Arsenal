package backpackarsenal.block;

import backpackarsenal.BackpackArsenalMod;
import backpackarsenal.init.ArsenalItems;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackBlock;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 設置時の見た目を独自 3D モデルで描画する BackpackBlock サブクラス。
 *
 * SB の {@link BackpackBlock} を継承するので BlockEntity / waterlogged / open 状態は
 * そのまま動作する。差し替え点:
 *   1. {@link #asItem()} で ArsenalBackpackItem を返す (pick-block / drop 用)
 *   2. {@link #getShape}/{@link #getCollisionShape} でモデル elements をなぞった shape を返す
 *
 * 当たり判定は class 初期化時にモデル JSON
 * ({@code assets/backpack_arsenal/models/item/arsenal_backpack_electron.json}) を読み、
 * 各 element の AABB を結合した VoxelShape を構築する。
 *   - per-element の rotation (Blockbench の rotate) を考慮して回転後 AABB を計算
 *   - 0..16 voxel 範囲外もそのまま採用 (block 境界を超える背の高い背負い等に対応)
 *   - 4 方向の FACING に対して回転バリアントを cache
 *
 * BlockEntityType ({@code ModBlocks.BACKPACK_TILE_TYPE}) には reflection で
 * 我々のブロックを追加する (BackpackArsenalMod#injectBlockIntoBackpackTileType)。
 */
public class ArsenalBackpackBlockElectron extends BackpackBlock {

    private static final String MODEL_RESOURCE =
        "/assets/backpack_arsenal/models/item/arsenal_backpack_electron.json";

    /** FACING ごとの voxel shape (NORTH/EAST/SOUTH/WEST)。class init で 1 度だけ構築。 */
    private static final Map<Direction, VoxelShape> SHAPES = buildShapes();

    public ArsenalBackpackBlockElectron() {
        super();
    }

    @Override
    public Item asItem() {
        return ArsenalItems.ARSENAL_BACKPACK_ELECTRON.get();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        Direction facing = state.getValue(FACING);
        VoxelShape s = SHAPES.get(facing);
        return s != null ? s : Shapes.block();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return getShape(state, level, pos, ctx);
    }

    // ─── shape construction ─────────────────────────────────────────

    private static Map<Direction, VoxelShape> buildShapes() {
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        VoxelShape north = loadShapeFromModel();
        map.put(Direction.NORTH, north);
        map.put(Direction.EAST,  rotateYAroundBlockCenter(north, Direction.EAST));
        map.put(Direction.SOUTH, rotateYAroundBlockCenter(north, Direction.SOUTH));
        map.put(Direction.WEST,  rotateYAroundBlockCenter(north, Direction.WEST));
        return map;
    }

    /** モデル JSON の elements 配列を読んで {@link VoxelShape} を構築。
     *  rotation がある element は回転後 AABB を採用する。 */
    private static VoxelShape loadShapeFromModel() {
        try (InputStream is = ArsenalBackpackBlockElectron.class.getResourceAsStream(MODEL_RESOURCE)) {
            if (is == null) {
                BackpackArsenalMod.LOGGER.warn(
                    "[backpack_arsenal] model resource not found: {} — using full block collision", MODEL_RESOURCE);
                return Shapes.block();
            }
            JsonObject model = JsonParser.parseReader(
                new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!model.has("elements")) return Shapes.block();
            JsonArray elements = model.getAsJsonArray("elements");

            VoxelShape shape = Shapes.empty();
            int boxCount = 0, rotated = 0;
            for (JsonElement el : elements) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                if (!o.has("from") || !o.has("to")) continue;
                double[] from = parseVec3(o.getAsJsonArray("from"));
                double[] to   = parseVec3(o.getAsJsonArray("to"));
                if (from == null || to == null) continue;

                // [from, to] の AABB を取得
                double[] aabb = {
                    Math.min(from[0], to[0]), Math.min(from[1], to[1]), Math.min(from[2], to[2]),
                    Math.max(from[0], to[0]), Math.max(from[1], to[1]), Math.max(from[2], to[2])
                };

                // rotation がある場合は box の 8 corner を回転して AABB を再計算
                if (o.has("rotation")) {
                    JsonObject rot = o.getAsJsonObject("rotation");
                    double angleDeg = rot.has("angle") ? rot.get("angle").getAsDouble() : 0;
                    if (angleDeg != 0 && rot.has("axis") && rot.has("origin")) {
                        String axis = rot.get("axis").getAsString();
                        double[] origin = parseVec3(rot.getAsJsonArray("origin"));
                        if (origin != null) {
                            aabb = rotateBoxAABB(aabb, axis, angleDeg, origin);
                            rotated++;
                        }
                    }
                }

                if (aabb[3] - aabb[0] < 0.0001
                    || aabb[4] - aabb[1] < 0.0001
                    || aabb[5] - aabb[2] < 0.0001) continue;

                shape = Shapes.or(shape, Block.box(aabb[0], aabb[1], aabb[2], aabb[3], aabb[4], aabb[5]));
                boxCount++;
            }
            shape = shape.optimize();
            BackpackArsenalMod.LOGGER.info(
                "[backpack_arsenal] built collision shape from {} elements ({} rotated)", boxCount, rotated);
            return shape.isEmpty() ? Shapes.block() : shape;
        } catch (Throwable t) {
            BackpackArsenalMod.LOGGER.error(
                "[backpack_arsenal] failed to build shape from model — using full block", t);
            return Shapes.block();
        }
    }

    /** 軸 (x/y/z) 周りに angle (度) で box を回転し、回転後の AABB を返す。 */
    private static double[] rotateBoxAABB(double[] aabb, String axis, double angleDeg, double[] origin) {
        double rad = Math.toRadians(angleDeg);
        double cos = Math.cos(rad), sin = Math.sin(rad);

        // 8 corners
        double[][] corners = {
            {aabb[0], aabb[1], aabb[2]}, {aabb[3], aabb[1], aabb[2]},
            {aabb[0], aabb[4], aabb[2]}, {aabb[3], aabb[4], aabb[2]},
            {aabb[0], aabb[1], aabb[5]}, {aabb[3], aabb[1], aabb[5]},
            {aabb[0], aabb[4], aabb[5]}, {aabb[3], aabb[4], aabb[5]},
        };

        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (double[] c : corners) {
            double dx = c[0] - origin[0], dy = c[1] - origin[1], dz = c[2] - origin[2];
            double nx, ny, nz;
            switch (axis) {
                case "x" -> {
                    nx = dx;
                    ny = dy * cos - dz * sin;
                    nz = dy * sin + dz * cos;
                }
                case "y" -> {
                    nx = dx * cos + dz * sin;
                    ny = dy;
                    nz = -dx * sin + dz * cos;
                }
                case "z" -> {
                    nx = dx * cos - dy * sin;
                    ny = dx * sin + dy * cos;
                    nz = dz;
                }
                default -> { nx = dx; ny = dy; nz = dz; }
            }
            double rx = nx + origin[0], ry = ny + origin[1], rz = nz + origin[2];
            if (rx < minX) minX = rx; if (rx > maxX) maxX = rx;
            if (ry < minY) minY = ry; if (ry > maxY) maxY = ry;
            if (rz < minZ) minZ = rz; if (rz > maxZ) maxZ = rz;
        }
        return new double[]{ minX, minY, minZ, maxX, maxY, maxZ };
    }

    private static double[] parseVec3(JsonArray a) {
        if (a == null || a.size() != 3) return null;
        try {
            return new double[]{ a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble() };
        } catch (Exception e) {
            return null;
        }
    }

    /** Y 軸回りに blockstates の y=90/180/270 と同じ回転を box 単位で適用。
     *  座標系: (x, z) は block center (8, 8) を中心に回転。 */
    private static VoxelShape rotateYAroundBlockCenter(VoxelShape src, Direction facing) {
        AtomicReference<VoxelShape> acc = new AtomicReference<>(Shapes.empty());
        // forAllBoxes は 0..1 範囲の座標で box を渡す
        src.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
            // 0..16 にスケール
            double ax1 = x1 * 16, ay1 = y1 * 16, az1 = z1 * 16;
            double ax2 = x2 * 16, ay2 = y2 * 16, az2 = z2 * 16;
            double nx1, nz1, nx2, nz2;
            switch (facing) {
                case EAST -> { // 90° CW: (x, z) → (16 - z, x)
                    nx1 = 16 - az2; nz1 = ax1;
                    nx2 = 16 - az1; nz2 = ax2;
                }
                case SOUTH -> { // 180°: (x, z) → (16 - x, 16 - z)
                    nx1 = 16 - ax2; nz1 = 16 - az2;
                    nx2 = 16 - ax1; nz2 = 16 - az1;
                }
                case WEST -> { // 270° CW: (x, z) → (z, 16 - x)
                    nx1 = az1; nz1 = 16 - ax2;
                    nx2 = az2; nz2 = 16 - ax1;
                }
                default -> {
                    nx1 = ax1; nz1 = az1;
                    nx2 = ax2; nz2 = az2;
                }
            }
            acc.set(Shapes.or(acc.get(), Block.box(nx1, ay1, nz1, nx2, ay2, nz2)));
        });
        return acc.get().optimize();
    }
}
