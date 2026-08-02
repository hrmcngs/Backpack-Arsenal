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
import net.minecraft.world.item.DyeableLeatherItem;
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
public class VoltaicBladeItem extends SwordItem implements DyeableLeatherItem {

    public static final String TAG_CHARGE = "BackpackCharge";
    public static final String TAG_ELEMENT_TYPE = "ElementType";
    public static final String TAG_ELEMENT_LEVEL = "ElementLevel";

    /** 手動で盛った element level のブースト ( 金床で強化 / 減少できる )。
     *  実効 element level = 充電由来の基準 level (1〜3) + このブースト。
     *  レベルが高いほど 1 ヒットの充電消費が {@link #CHARGE_COST_PER_ELEMENT_LEVEL} ずつ増える。 */
    public static final String TAG_ELEMENT_LEVEL_BOOST = "ElementLevelBoost";

    /** 属性モード ( 金床で voltaic_element_core と合成してトグル )。
     *  MAW の {@code ElementType.fromString} は equalsIgnoreCase なので大文字表記でよい。 */
    public static final String TAG_ELEMENT_MODE = "ElementMode";
    public static final String MODE_ELECTRIC = "ELECTRIC"; // 電気属性 ( 既定 )
    public static final String MODE_THUNDER  = "THUNDER";  // 雷属性

    /** 雷モードのバックパック充電レート ( 電気の {@value}/{@value} 倍 = 遅い )。
     *  雷は電気より貯まるのが遅い、 というバランス。 */
    public static final int THUNDER_CHARGE_NUMERATOR   = 1;
    public static final int THUNDER_CHARGE_DENOMINATOR = 2;
    /** 雷モードの 1 ヒット消費倍率 ( 電気の {@value} 倍 = 燃費が悪い → ヒット数が少ない )。 */
    public static final int THUNDER_HIT_COST_MULTIPLIER = 2;
    /** capacitor upgrade を適用した stage 配列 (LIFO)。 各 entry は +bonus 量 (256/512/1024)。
     *  これで tier を区別したまま積めるので、 sneak+RC で 1 stage ずつ剥がせる。 */
    public static final String TAG_CAPACITOR_STAGES = "CapacitorStages";
    /** legacy (単一 tier 時代の) bonus 数。 migrate のためだけに残してある — 1.0.0 で書かれた
     *  blade は読み込み時に TAG_CAPACITOR_STAGES へ変換される (各 entry = 1024)。 */
    @Deprecated
    public static final String TAG_CAPACITOR_BONUS = "CapacitorBonus";

    /** レアリティ未設定 / COMMON 時の基準値。getMaxCharge() の出発点。 */
    public static final int BASE_MAX_CHARGE = 1024;
    public static final int CHARGE_COST_PER_HIT = 200;
    public static final int CHARGE_PER_TICK_IN_BACKPACK = 2;
    /** 実効 element level 1 ごとに増える 1 ヒットの追加充電消費 ( 徐々に重くなる )。 */
    public static final int CHARGE_COST_PER_ELEMENT_LEVEL = 100;
    /** element level ブーストの survival 上限 ( creative は無制限 )。 */
    public static final int MAX_ELEMENT_LEVEL_BOOST = 5;
    /** capacitor upgrade tier の bonus 値 (I=+256, II=+512, III=+1024)。 strongest = 1024。 */
    public static final int CAPACITOR_TIER_I_BONUS  = 256;
    public static final int CAPACITOR_TIER_II_BONUS = 512;
    public static final int CAPACITOR_TIER_III_BONUS = 1024;
    /** capacitor upgrade を適用できる最大 stage 数 (survival)。 creative は制限なし。 */
    public static final int MAX_CAPACITOR_BONUS = 5;
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
        return getRarityBaseMaxCharge(stack) + getCapacitorBonusTotal(stack);
    }

    /** rarity だけで決まる base max (capacitor bonus を含まない)。 内部用 + tooltip 表示用。 */
    public static int getRarityBaseMaxCharge(ItemStack stack) {
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

    /** capacitor upgrade を適用した stage 配列 (LIFO)。 各 entry は +bonus 量。
     *  legacy (1.0.0) NBT に {@link #TAG_CAPACITOR_BONUS} (int) が残ってる場合は読み取り時に
     *  自動で N 個の +1024 stage 配列へ変換する (旧 = 全 tier III 扱い)。 */
    public static int[] getCapacitorStages(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return new int[0];
        if (tag.contains(TAG_CAPACITOR_STAGES, net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
            return tag.getIntArray(TAG_CAPACITOR_STAGES);
        }
        if (tag.contains(TAG_CAPACITOR_BONUS, net.minecraft.nbt.Tag.TAG_INT)) {
            int legacy = Math.max(0, tag.getInt(TAG_CAPACITOR_BONUS));
            int[] migrated = new int[legacy];
            java.util.Arrays.fill(migrated, CAPACITOR_TIER_III_BONUS);
            tag.remove(TAG_CAPACITOR_BONUS);
            if (migrated.length > 0) tag.putIntArray(TAG_CAPACITOR_STAGES, migrated);
            return migrated;
        }
        return new int[0];
    }

    /** 現在の stage 総 bonus 合計 (max charge への加算分)。 */
    public static int getCapacitorBonusTotal(ItemStack stack) {
        int sum = 0;
        for (int v : getCapacitorStages(stack)) sum += v;
        return sum;
    }

    public static int getCapacitorStageCount(ItemStack stack) {
        return getCapacitorStages(stack).length;
    }

    /** stage 配列を直接書き込む。 空配列なら tag 削除。 anvil / peel-off 用。 */
    public static void setCapacitorStages(ItemStack stack, int[] stages) {
        CompoundTag tag = stack.getOrCreateTag();
        if (stages.length == 0) {
            tag.remove(TAG_CAPACITOR_STAGES);
        } else {
            tag.putIntArray(TAG_CAPACITOR_STAGES, stages);
        }
        tag.remove(TAG_CAPACITOR_BONUS); // legacy 残骸を清掃
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

        // element level は充電残量比 ( max 依存 ) をやめ、 「充電されていれば固定 base 1 +
        // 手動ブースト」 に統一する。 これで最大チャージ量に関係なくレベルの上がり方が一定になり、
        // ダスト 1 個 = +1 レベル で素直に効く。 MAW はこの ElementLevel を読む。
        if (clamped > 0) {
            tag.putString(TAG_ELEMENT_TYPE, getElementMode(stack));
            tag.putInt(TAG_ELEMENT_LEVEL, 1 + getElementLevelBoost(stack));
        } else {
            tag.remove(TAG_ELEMENT_TYPE);
            tag.remove(TAG_ELEMENT_LEVEL);
        }
    }

    /** 手動 element level ブースト ( 0 以上 )。 */
    public static int getElementLevelBoost(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : Math.max(0, tag.getInt(TAG_ELEMENT_LEVEL_BOOST));
    }

    /** ブーストを設定し ( 0 未満は 0 にクランプ )、 ElementLevel タグを貼り直す。 */
    public static void setElementLevelBoost(ItemStack stack, int boost) {
        int clamped = Math.max(0, boost);
        CompoundTag tag = stack.getOrCreateTag();
        if (clamped == 0) {
            tag.remove(TAG_ELEMENT_LEVEL_BOOST);
        } else {
            tag.putInt(TAG_ELEMENT_LEVEL_BOOST, clamped);
        }
        setCharge(stack, getCharge(stack)); // ElementLevel を再導出
    }

    /** 実効 element level。 充電されていれば 固定 base 1 + ブースト、 未充電なら 0。
     *  最大チャージ量には依存しない ( レベルの上がり方を一定にするため )。 */
    public static int getEffectiveElementLevel(ItemStack stack) {
        return getCharge(stack) > 0 ? 1 + getElementLevelBoost(stack) : 0;
    }

    /** 1 ヒットの充電消費。 実効 element level が高いほど徐々に増える。
     *  雷モードは {@link #THUNDER_HIT_COST_MULTIPLIER} 倍消費する ( 燃費が悪くヒット数が減る )。 */
    public static int getChargeCostPerHit(ItemStack stack) {
        int cost = CHARGE_COST_PER_HIT
                + getEffectiveElementLevel(stack) * CHARGE_COST_PER_ELEMENT_LEVEL;
        if (isThunderMode(stack)) {
            cost *= THUNDER_HIT_COST_MULTIPLIER;
        }
        return cost;
    }

    /** 技 ( スキル ) 発動時の充電消費。 充電が有れば 1 ヒット分消費して true、 無ければ false。
     *  各 SkillAction が発動時にこれを呼ぶ。 */
    public static boolean consumeSkillCharge(ItemStack stack) {
        if (getCharge(stack) <= 0) return false;
        addCharge(stack, -getChargeCostPerHit(stack));
        return true;
    }

    /** 現在の属性モードを返す ( 未設定なら電気 )。 */
    public static String getElementMode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_ELEMENT_MODE)
                && MODE_THUNDER.equalsIgnoreCase(tag.getString(TAG_ELEMENT_MODE))) {
            return MODE_THUNDER;
        }
        return MODE_ELECTRIC;
    }

    public static boolean isThunderMode(ItemStack stack) {
        return MODE_THUNDER.equals(getElementMode(stack));
    }

    /** 属性モードを設定し、 現在の充電量で ElementType タグを貼り直す。 */
    public static void setElementMode(ItemStack stack, String mode) {
        String norm = MODE_THUNDER.equalsIgnoreCase(mode) ? MODE_THUNDER : MODE_ELECTRIC;
        stack.getOrCreateTag().putString(TAG_ELEMENT_MODE, norm);
        setCharge(stack, getCharge(stack)); // ElementType を新モードで再導出
    }

    /** 電気 ⇄ 雷 をトグルし、 新しいモード文字列を返す。 */
    public static String toggleElementMode(ItemStack stack) {
        String next = isThunderMode(stack) ? MODE_ELECTRIC : MODE_THUNDER;
        setElementMode(stack, next);
        return next;
    }

    /** バックパック充電用。 雷モードなら充電レートを {@link #THUNDER_CHARGE_NUMERATOR}/
     *  {@link #THUNDER_CHARGE_DENOMINATOR} に減速する ( 電気より貯まりが遅い )。
     *  微小レートでも進捗 0 にならないよう、 正の入力に対しては最低 1 を保証する。 */
    public static void addBackpackCharge(ItemStack stack, int baseAmount) {
        int amount = baseAmount;
        if (baseAmount > 0 && isThunderMode(stack)) {
            amount = (int) ((long) baseAmount * THUNDER_CHARGE_NUMERATOR / THUNDER_CHARGE_DENOMINATOR);
            if (amount <= 0) amount = 1;
        }
        addCharge(stack, amount);
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

        // element level ブーストが高いほど 1 ヒットの消費が徐々に増える。
        addCharge(stack, -getChargeCostPerHit(stack));

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

    /** glint を出したい条件 (充電有 or 普通の foil)。
     *  vanilla glint は {@link #isFoil} で常に false にして抑制し、
     *  {@code VoltaicBladeBakedModel.getRenderTypes} がこの条件で
     *  カスタム glint (V 軸スクロール) を追加する。 */
    public static boolean shouldGlint(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return getCharge(stack) > 0 || stack.isEnchanted();
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // vanilla glint は無効化 (カスタム glint で代替)。
        return false;
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
        tooltip.add(Component.translatable(
                "item.backpack_arsenal.voltaic_blade.charge_tooltip",
                charge, max
        ).withStyle(ChatFormatting.AQUA));
        int stageCount = getCapacitorStageCount(stack);
        if (stageCount > 0) {
            int totalBonus = getCapacitorBonusTotal(stack);
            tooltip.add(Component.translatable(
                    "item.backpack_arsenal.voltaic_blade.capacitor_tooltip",
                    stageCount, MAX_CAPACITOR_BONUS, totalBonus
            ).withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable(
                    "item.backpack_arsenal.voltaic_blade.capacitor_peel_hint"
            ).withStyle(ChatFormatting.DARK_GRAY));
        }
        int boost = getElementLevelBoost(stack);
        // element level の行は MAW ( ElementalTooltipEvent ) が属性色 + ローマ数字で出す
        // ( "Electric Element N" )。 二重表示を避けるため我々はレベル行を出さず、 充電中は
        // 1 ヒット消費だけ表示する。 その MAW 行への "(charged)" 付与は
        // {@link backpackarsenal.client.VoltaicBladeChargedTooltip} が行う。
        if (charge > 0) {
            // ヒット数は「満タン時に何発撃てるか ( = 最大 ÷ 消費 )」= 刀の能力値で出す。
            //   capacitor で最大↑ → 即増、 属性レベル↑ → 消費↑で減、 と必ず数字が動く
            //   ( 現在チャージ基準だと手持ちは充電し直すまで動かず「固定」に見えるため )。
            //   現在の残量は上の "Charge: X / Y" バーで確認できる。
            int cost = getChargeCostPerHit(stack);
            int hitsMax = (max + cost - 1) / cost;
            tooltip.add(Component.translatable(
                    "item.backpack_arsenal.voltaic_blade.element_hits_tooltip", hitsMax, cost
            ).withStyle(ChatFormatting.GOLD));
        } else if (boost > 0) {
            // 未充電でもブーストは設定済みであることを示す ( 充電すると効く )。
            tooltip.add(Component.translatable(
                    "item.backpack_arsenal.voltaic_blade.element_boost_idle_tooltip", boost
            ).withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable(
                    "item.backpack_arsenal.voltaic_blade.need_charge_tooltip"
            ).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable(
                    "item.backpack_arsenal.voltaic_blade.need_charge_tooltip"
            ).withStyle(ChatFormatting.GRAY));
        }
    }
}
