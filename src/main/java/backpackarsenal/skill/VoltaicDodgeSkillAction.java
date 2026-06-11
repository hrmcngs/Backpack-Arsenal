package backpackarsenal.skill;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import the_four_primitives_and_weapons.api.ISkillAction;

/**
 * 雷影ステップ (voltaic_step) — ZZZ 無印アンビー風の電気回避。
 *
 * 動作:
 *   - WASD 入力の方向へクイックダッシュ (約 1.8 ブロック分)
 *   - 入力無しの場合は後方ステップ
 *   - 1 秒間の無敵 (invulnerableTime = 20 tick)
 *   - 電気スパーク+残像パーティクル
 *   - 軽い zap 音
 *
 * MAW SkillRegistry の DASH スロットに登録。Voltaic Blade を持ってる時のみ
 * Skill Selection で dash_rush / leap_slash / shadow_step と並んで選択可能。
 *
 * 充電を消費しない (常に発動可、cooldown は MAW 側 DASH cooldown で管理)。
 */
public class VoltaicDodgeSkillAction implements ISkillAction {

    public static final double DODGE_SPEED = 2.0; // setDeltaMovement に渡す速度。MAW dash_rush=2.5、leap=2.0 と同オーダ
    public static final int INVUL_TICKS = 20; // 1秒

    /**
     * MAW 本体 {@code DashSkillHandler#getMovementDirection} と同じ式。
     * WASD があればその方向、無ければ視線方向 (水平成分) を返す。
     *
     * MAW は {@code DodgeRequestPacket} 経由でクライアントの player.zza/xxa を server
     * 側 player にコピーしてから {@code MotionExecutor#executeMotion} を呼ぶので、
     * server 側でも {@code player.zza}/{@code player.xxa} は信用できる。
     */
    private static Vec3 getMovementDirection(Player player) {
        float forward = player.zza;
        float strafe = player.xxa;

        if (forward == 0 && strafe == 0) {
            Vec3 look = player.getLookAngle();
            Vec3 horizontal = new Vec3(look.x, 0, look.z);
            return horizontal.length() > 0.001 ? horizontal.normalize() : new Vec3(0, 0, 1);
        }

        float yawRad = player.getYRot() * ((float) Math.PI / 180F);
        float sinYaw = (float) Math.sin(yawRad);
        float cosYaw = (float) Math.cos(yawRad);
        double moveX = strafe * cosYaw - forward * sinYaw;
        double moveZ = strafe * sinYaw + forward * cosYaw;

        Vec3 dir = new Vec3(moveX, 0, moveZ);
        return dir.length() > 0.001 ? dir.normalize() : new Vec3(0, 0, 1);
    }

    @Override
    public void execute(Player player, float power) {
        Level level = player.level();

        Vec3 dir = getMovementDirection(player);
        // power はダッシュ系では 0 で渡される (MotionExecutor.executeMotion(..., 0.0f))。
        // DASH_SPEED にそのまま乗らないように 1.0 を最低値とする。
        double speed = DODGE_SPEED * Math.max(1.0f, power);

        // push() (= deltaMovement に加算) ではなく setDeltaMovement で即時速度を上書き。
        // 加算だと既存の Y 重力 / 微小水平速度に押し流されて反応が遅く感じる ("ラグい")。
        player.setDeltaMovement(dir.x * speed, 0.2, dir.z * speed);
        player.hurtMarked = true; // velocity sync

        // 無敵タイム
        player.invulnerableTime = Math.max(player.invulnerableTime, INVUL_TICKS);

        // パーティクル + 音 (サーバ側のみ)
        if (level instanceof ServerLevel serverLevel) {
            double x = player.getX();
            double y = player.getY() + player.getBbHeight() * 0.5;
            double z = player.getZ();
            serverLevel.sendParticles(
                ParticleTypes.ELECTRIC_SPARK,
                x, y, z,
                25, 0.4, 0.6, 0.4, 0.4);
            serverLevel.sendParticles(
                ParticleTypes.CLOUD,
                x, y, z,
                6, 0.3, 0.2, 0.3, 0.02);
        }
        level.playSound(null,
            player.getX(), player.getY(), player.getZ(),
            SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.25f, 1.8f);
    }
}
