package backpackarsenal.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Voltaic Blade — 電気属性の刀
 *
 * 登録上は刀 (weapon_types: katana)。挙動上は通常の刀より少しだけ攻撃速度が速い。
 * Sophisticated Backpack に収納している間に NBT "BackpackCharge" が増え、
 * 充電量に応じて MAW の電気属性 (ElementType="ELECTRIC", ElementLevel=N) が
 * 自動付与される。攻撃時に充電を消費する。
 *
 * チャージ→ElementLevel 対応:
 *   0       …… 無属性
 *   1 〜 999 …… ElementLevel 1
 *   1000以上 …… ElementLevel 2
 *   3000以上 …… ElementLevel 3
 *   最大値: 6000 (MAX_CHARGE)
 * 1ヒット消費: 200
 *
 * 例外特技: 雷叩きつけ (slam)
 *   通常の刀は持っていない移動 — Sneak + 右クリック で前方 AOE 叩きつけを発動。
 *   通常右クリックは MAW の刀ロジック (納刀/抜刀) に PASS。
 *   ダメージは充電 ElementLevel でスケール。充電があれば消費するが無くても撃てる。
 */
public class VoltaicBladeItem extends SwordItem {

    public static final String TAG_CHARGE = "BackpackCharge";
    public static final String TAG_ELEMENT_TYPE = "ElementType";
    public static final String TAG_ELEMENT_LEVEL = "ElementLevel";

    public static final int MAX_CHARGE = 6000;
    public static final int CHARGE_COST_PER_HIT = 200;
    public static final int CHARGE_PER_TICK_IN_BACKPACK = 2;

    /** 雷叩きつけ — 1 回の充電消費 (充電が無くても発動はする) */
    public static final int SLAM_CHARGE_COST = 400;
    /** 雷叩きつけ — クールダウン (tick) */
    public static final int SLAM_COOLDOWN_TICKS = 25;
    /** 雷叩きつけ — AOE 半径 */
    public static final double SLAM_RADIUS = 2.5;
    /** 雷叩きつけ — プレイヤー前方どれだけ先に AOE 中心を置くか */
    public static final double SLAM_FORWARD = 1.8;

    public VoltaicBladeItem() {
        super(
            new Tier() {
                public int getUses()                 { return 0; }
                public float getSpeed()              { return 4f; }
                public float getAttackDamageBonus()  { return 4f; }
                public int getLevel()                { return 2; }
                public int getEnchantmentValue()     { return 14; }
                public Ingredient getRepairIngredient() { return Ingredient.of(); }
            },
            3,
            -2.1f,
            new Item.Properties().rarity(Rarity.RARE)
        );
    }

    // ==================== チャージ操作 ====================

    public static int getCharge(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(TAG_CHARGE);
    }

    public static void setCharge(ItemStack stack, int charge) {
        int clamped = Math.max(0, Math.min(MAX_CHARGE, charge));
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(TAG_CHARGE, clamped);

        int level = chargeToElementLevel(clamped);
        if (level > 0) {
            tag.putString(TAG_ELEMENT_TYPE, "ELECTRIC");
            tag.putInt(TAG_ELEMENT_LEVEL, level);
        } else {
            tag.remove(TAG_ELEMENT_TYPE);
            tag.remove(TAG_ELEMENT_LEVEL);
        }
    }

    public static int chargeToElementLevel(int charge) {
        if (charge >= 3000) return 3;
        if (charge >= 1000) return 2;
        if (charge >= 1)    return 1;
        return 0;
    }

    public static void addCharge(ItemStack stack, int delta) {
        setCharge(stack, getCharge(stack) + delta);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean result = super.hurtEnemy(stack, target, attacker);

        if (attacker.level().isClientSide) return result;
        if (!(attacker instanceof Player)) return result;

        int charge = getCharge(stack);
        if (charge <= 0) return result;

        addCharge(stack, -CHARGE_COST_PER_HIT);

        if (attacker.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                ParticleTypes.ELECTRIC_SPARK,
                target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                20, 0.3, 0.4, 0.3, 0.15
            );
            serverLevel.sendParticles(
                ParticleTypes.CRIT,
                target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                6, 0.2, 0.2, 0.2, 0.05
            );
        }
        attacker.level().playSound(null,
            attacker.getX(), attacker.getY(), attacker.getZ(),
            SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.35f, 1.6f);

        return result;
    }

    // ==================== 雷叩きつけ (slam) ====================

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 通常右クリックは本体MOD (刀の納刀/抜刀) に任せるため PASS。
        // Sneak + 右クリック でだけ叩きつけ発動。
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        if (!level.isClientSide) {
            performSlam(level, player, stack);
            if (getCharge(stack) > 0) {
                addCharge(stack, -SLAM_CHARGE_COST);
            }
        }

        player.swing(hand);
        player.getCooldowns().addCooldown(this, SLAM_COOLDOWN_TICKS);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private void performSlam(Level level, Player player, ItemStack stack) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        Vec3 look = player.getLookAngle();
        Vec3 origin = player.position();
        // 前方 SLAM_FORWARD 先の位置を中心に判定 (高さは足元から +2 まで広く取る)
        Vec3 center = new Vec3(
            origin.x + look.x * SLAM_FORWARD,
            origin.y,
            origin.z + look.z * SLAM_FORWARD
        );

        AABB area = new AABB(
            center.x - SLAM_RADIUS, center.y - 1.0, center.z - SLAM_RADIUS,
            center.x + SLAM_RADIUS, center.y + 2.0, center.z + SLAM_RADIUS
        );

        int charge = getCharge(stack);
        int elementLevel = chargeToElementLevel(charge);
        // 充電なし: 5.0、Lv1: 6.5、Lv2: 8.0、Lv3: 9.5
        float slamDamage = 5.0f + elementLevel * 1.5f;
        boolean charged = charge > 0;

        List<LivingEntity> targets = serverLevel.getEntitiesOfClass(
            LivingEntity.class, area, e -> e != player && e.isAlive());

        for (LivingEntity target : targets) {
            target.hurt(target.damageSources().playerAttack(player), slamDamage);

            // 中心から外へ + 少し上に持ち上げるノックバック
            Vec3 diff = target.position().subtract(center);
            double dist = Math.max(0.001, diff.horizontalDistance());
            target.push(diff.x / dist * 0.65, 0.45, diff.z / dist * 0.65);
        }

        // インパクト基本エフェクト (常に出る)
        serverLevel.sendParticles(
            ParticleTypes.EXPLOSION,
            center.x, center.y + 0.3, center.z,
            3, 0.4, 0.1, 0.4, 0.05);
        serverLevel.sendParticles(
            ParticleTypes.CLOUD,
            center.x, center.y + 0.2, center.z,
            18, SLAM_RADIUS * 0.5, 0.05, SLAM_RADIUS * 0.5, 0.1);

        serverLevel.playSound(null, center.x, center.y, center.z,
            SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35f, 1.3f);

        // 充電あり: 雷エフェクト + 雷鳴音 を追加
        if (charged) {
            serverLevel.sendParticles(
                ParticleTypes.ELECTRIC_SPARK,
                center.x, center.y + 0.4, center.z,
                40, SLAM_RADIUS * 0.6, 0.4, SLAM_RADIUS * 0.6, 0.4);
            serverLevel.playSound(null, center.x, center.y, center.z,
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.5f, 1.5f);
        }
    }

    // ==================== 表示 ====================

    @Override
    public boolean isFoil(ItemStack stack) {
        return getCharge(stack) > 0 || super.isFoil(stack);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getCharge(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0f * getCharge(stack) / (float) MAX_CHARGE);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float ratio = getCharge(stack) / (float) MAX_CHARGE;
        int r = 0x55 + Math.round(0xAA * ratio);
        int g = 0x88 + Math.round(0x77 * ratio);
        int b = 0xFF;
        return (r << 16) | (g << 8) | b;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        int charge = getCharge(stack);
        int lv = chargeToElementLevel(charge);
        tooltip.add(Component.translatable(
                "item.backpack_arsenal.voltaic_blade.charge_tooltip",
                charge, MAX_CHARGE
        ).withStyle(ChatFormatting.AQUA));
        if (lv > 0) {
            tooltip.add(Component.translatable(
                    "item.backpack_arsenal.voltaic_blade.element_tooltip", lv
            ).withStyle(ChatFormatting.LIGHT_PURPLE));
        } else {
            tooltip.add(Component.translatable(
                    "item.backpack_arsenal.voltaic_blade.need_charge_tooltip"
            ).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable(
                "item.backpack_arsenal.voltaic_blade.slam_tooltip"
        ).withStyle(ChatFormatting.YELLOW));
    }
}
