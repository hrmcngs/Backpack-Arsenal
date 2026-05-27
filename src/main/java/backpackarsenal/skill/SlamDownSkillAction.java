package backpackarsenal.skill;

import backpackarsenal.item.VoltaicBladeItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import the_four_primitives_and_weapons.api.ISkillAction;

import java.util.List;

/**
 * 雷振り下ろし (voltaic_slam_down) — VoltaicBlade 専用の MAW モーションハンドラ。
 *
 * MAW の Skill Selection 画面で 1st/2nd/3rd Hit / Charged に「雷振り下ろし」を
 * 割り当てたとき、MAW の MotionExecutor が該当タイミングでこの execute(Player, float)
 * を呼ぶ。Sneak+右クリックの起動は廃止 (use() ハンドラを撤去)。
 *
 * power は MAW から渡される強度 (0.0〜1.0)、ダメージは充電 ElementLevel × 1.5 + 5.0 × power。
 * voltaic_blade を持っていない状態で呼ばれることは無い想定 (requiredWeaponClass で絞り済み)。
 */
public class SlamDownSkillAction implements ISkillAction {

    public static final double SLAM_RADIUS = 2.5;
    public static final double SLAM_FORWARD = 1.8;

    @Override
    public void execute(Player player, float power) {
        Level level = player.level();
        if (!(level instanceof ServerLevel serverLevel)) return;

        // ダメージ計算: VoltaicBlade なら充電 ElementLevel でブースト、それ以外は素のベース 5.0。
        ItemStack held = player.getMainHandItem();
        int elementLevel = 0;
        if (held.getItem() instanceof VoltaicBladeItem) {
            elementLevel = VoltaicBladeItem.chargeToElementLevel(VoltaicBladeItem.getCharge(held));
            // 充電があれば消費 (VoltaicBladeItem#use() と同じ消費量)
            int charge = VoltaicBladeItem.getCharge(held);
            if (charge > 0) {
                VoltaicBladeItem.addCharge(held, -400);
            }
        }
        float slamDamage = (5.0f + elementLevel * 1.5f) * Math.max(0.5f, power);

        // AOE 中心: プレイヤー前方 SLAM_FORWARD
        Vec3 look = player.getLookAngle();
        Vec3 origin = player.position();
        Vec3 center = new Vec3(
            origin.x + look.x * SLAM_FORWARD,
            origin.y,
            origin.z + look.z * SLAM_FORWARD
        );

        AABB area = new AABB(
            center.x - SLAM_RADIUS, center.y - 1.0, center.z - SLAM_RADIUS,
            center.x + SLAM_RADIUS, center.y + 2.0, center.z + SLAM_RADIUS
        );

        List<LivingEntity> targets = serverLevel.getEntitiesOfClass(
            LivingEntity.class, area, e -> e != player && e.isAlive());

        for (LivingEntity target : targets) {
            target.hurt(target.damageSources().playerAttack(player), slamDamage);
            // 中心から外へ + 少し浮かせるノックバック
            Vec3 diff = target.position().subtract(center);
            double dist = Math.max(0.001, diff.horizontalDistance());
            target.push(diff.x / dist * 0.65, 0.45, diff.z / dist * 0.65);
        }

        // インパクトエフェクト
        serverLevel.sendParticles(ParticleTypes.EXPLOSION,
            center.x, center.y + 0.3, center.z, 3, 0.4, 0.1, 0.4, 0.05);
        serverLevel.sendParticles(ParticleTypes.CLOUD,
            center.x, center.y + 0.2, center.z, 18, SLAM_RADIUS * 0.5, 0.05, SLAM_RADIUS * 0.5, 0.1);
        serverLevel.playSound(null, center.x, center.y, center.z,
            SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35f, 1.3f);

        // 充電あり: 雷スパーク + 雷鳴音
        if (elementLevel > 0) {
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                center.x, center.y + 0.4, center.z, 40, SLAM_RADIUS * 0.6, 0.4, SLAM_RADIUS * 0.6, 0.4);
            serverLevel.playSound(null, center.x, center.y, center.z,
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.5f, 1.5f);
        }
    }
}
