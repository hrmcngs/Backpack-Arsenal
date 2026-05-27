package backpackarsenal.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import the_four_primitives_and_weapons.item.rarity.WeaponRarity;

import java.util.List;

/**
 * Voltaic Blade — 電気属性の刀
 *
 * 登録上は刀 (weapon_types: katana)。挙動上は通常の刀より少しだけ攻撃速度が速い。
 * Sophisticated Backpack に収納している間に NBT "BackpackCharge" が増え、
 * 充電量に応じて MAW の電気属性 (ElementType="ELECTRIC", ElementLevel=N) が
 * 自動付与される。攻撃時に充電を消費する。
 *
 * チャージ→ElementLevel 対応 (maxCharge に対する割合で判定、レアリティに依存):
 *   0           …… 無属性
 *   ≥ 1         …… ElementLevel 1
 *   ≥ max / 6   …… ElementLevel 2
 *   ≥ max / 2   …… ElementLevel 3
 *
 * 最大チャージ (MAW WeaponRarity 連動):
 *   COMMON / unset …… 1024
 *   UNCOMMON       …… 2048
 *   RARE           …… 4096
 *   EPIC           …… 6144
 *   LEGENDARY      …… 8192
 *   FORBIDDEN      …… 12288
 * 1ヒット消費: 200
 *
 * 例外特技: 雷振り下ろし (voltaic_slam_down)
 *   MAW の SkillRegistry に "voltaic_slam_down" として登録され、Skill Selection 画面で
 *   1st/2nd/3rd Hit/Charged のいずれかに割り当てて発動する (sneak+RC ハンドラは無し)。
 *   実装は backpackarsenal.skill.SlamDownSkillAction。
 *   充電 ElementLevel に応じてダメージスケール、充電消費は 400。
 */
public class VoltaicBladeItem extends SwordItem {

    public static final String TAG_CHARGE = "BackpackCharge";
    public static final String TAG_ELEMENT_TYPE = "ElementType";
    public static final String TAG_ELEMENT_LEVEL = "ElementLevel";

    /** レアリティ未設定 / COMMON 時の基準値。getMaxCharge() の出発点。 */
    public static final int BASE_MAX_CHARGE = 1024;
    public static final int CHARGE_COST_PER_HIT = 200;
    public static final int CHARGE_PER_TICK_IN_BACKPACK = 2;
    // 雷振り下ろし の数値は SlamDownSkillAction 側に定義 (MAW Skill Selection 経由でのみ発動)。

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

    /**
     * MAW の WeaponRarity に応じた最大チャージ。レアリティ未設定 = COMMON 扱い。
     * RarityForge でレアリティが上がると最大チャージも段階的に増える。
     */
    public static int getMaxCharge(ItemStack stack) {
        WeaponRarity rarity = WeaponRarity.getFromStack(stack);
        if (rarity == null) return BASE_MAX_CHARGE;
        switch (rarity) {
            case UNCOMMON:  return 2048;
            case RARE:      return 4096;
            case EPIC:      return 6144;
            case LEGENDARY: return 8192;
            case FORBIDDEN: return 12288;
            case COMMON:
            default:        return BASE_MAX_CHARGE;
        }
    }

    public static int getCharge(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(TAG_CHARGE);
    }

    public static void setCharge(ItemStack stack, int charge) {
        int max = getMaxCharge(stack);
        int clamped = Math.max(0, Math.min(max, charge));
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(TAG_CHARGE, clamped);

        int level = chargeToElementLevel(clamped, max);
        if (level > 0) {
            tag.putString(TAG_ELEMENT_TYPE, "ELECTRIC");
            tag.putInt(TAG_ELEMENT_LEVEL, level);
        } else {
            tag.remove(TAG_ELEMENT_TYPE);
            tag.remove(TAG_ELEMENT_LEVEL);
        }
    }

    /** Element level (1〜3) を max に対する充電割合で算出。 */
    public static int chargeToElementLevel(int charge, int maxCharge) {
        if (maxCharge <= 0) return 0;
        if (charge >= maxCharge / 2) return 3;
        if (charge >= maxCharge / 6) return 2;
        if (charge >= 1)             return 1;
        return 0;
    }

    /** Stack の rarity を読んで element level を返す便宜メソッド。 */
    public static int chargeToElementLevel(ItemStack stack) {
        return chargeToElementLevel(getCharge(stack), getMaxCharge(stack));
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
        int max = getMaxCharge(stack);
        if (max <= 0) return 0;
        return Math.round(13.0f * getCharge(stack) / (float) max);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        int max = getMaxCharge(stack);
        float ratio = max <= 0 ? 0f : getCharge(stack) / (float) max;
        int r = 0x55 + Math.round(0xAA * ratio);
        int g = 0x88 + Math.round(0x77 * ratio);
        int b = 0xFF;
        return (r << 16) | (g << 8) | b;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        int charge = getCharge(stack);
        int max = getMaxCharge(stack);
        int lv = chargeToElementLevel(charge, max);
        tooltip.add(Component.translatable(
                "item.backpack_arsenal.voltaic_blade.charge_tooltip",
                charge, max
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
    }
}
