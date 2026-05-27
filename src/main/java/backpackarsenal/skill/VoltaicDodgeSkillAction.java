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
 * 雷影回避 (voltaic_dodge) — ZZZ 無印アンビー風の電気回避。
 *
 * 動作:
 *   - 視線の後方へクイックダッシュ (約 1.8 ブロック分)
 *   - 1 秒間の無敵 (invulnerableTime = 20 tick)
 *   - 電気スパーク+残像パーティクル
 *   - 軽い zap 音
 *
 * MAW SkillRegistry の RIGHT_CLICK スロットに登録。Voltaic Blade を持ってる時のみ
 * Skill Selection で割り当て可能。
 *
 * 充電を消費しない (常に発動可、cooldown はクライアント側 R\_click の都合で MAW 側管理)。
 */
public class VoltaicDodgeSkillAction implements ISkillAction {

    public static final double DODGE_DISTANCE = 1.8;
    public static final int INVUL_TICKS = 20; // 1秒

    @Override
    public void execute(Player player, float power) {
        Level level = player.level();

        // 後方ベクトル (look の真逆、水平のみ)
        Vec3 look = player.getLookAngle();
        double yawX = -look.x;
        double yawZ = -look.z;
        double horizLen = Math.sqrt(yawX * yawX + yawZ * yawZ);
        if (horizLen < 0.001) {
            // ほぼ真上/真下を見ている時のフォールバック: 体の前方の真後ろ
            float yawRad = (float) Math.toRadians(player.getYRot() + 180);
            yawX = -Math.sin(yawRad);
            yawZ =  Math.cos(yawRad);
            horizLen = 1.0;
        }
        double pushScale = DODGE_DISTANCE * Math.max(0.5f, power);
        player.push(
            (yawX / horizLen) * pushScale * 0.55,
            0.15,
            (yawZ / horizLen) * pushScale * 0.55
        );
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
