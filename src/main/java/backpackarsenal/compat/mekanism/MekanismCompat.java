package backpackarsenal.compat.mekanism;

import backpackarsenal.BackpackArsenalMod;
import backpackarsenal.energy.BackpackFeProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.ModList;

/**
 * Mekanism mod が読み込まれているときだけ Mekanism API を触る dispatcher。
 *
 * 重要: **このクラスは Mekanism の型を import しない**。 Mekanism 型を import するクラス
 * ({@link MekanismEnergyAdapter}) は {@link #attachStrictEnergyCap} の内側で初めて
 * 参照される。 JVM の lazy class loading により、 Mekanism 未導入環境では
 * MekanismEnergyAdapter は class load されない → NoClassDefFoundError にならない。
 *
 * 利用側 ( {@code BackpackFeEvents.onAttachBlockEntity} ) は無条件に
 * {@link #tryAttachStrictEnergy} を呼ぶだけで OK。
 */
public final class MekanismCompat {

    private MekanismCompat() {}

    /** {@code Capability<IStrictEnergyHandler>} の取得は Mekanism 未導入時に失敗するので
     *  reflective に一度だけ取って cache する。 取得不可なら null。 */
    private static Object cachedCap = null; // 実体は Capability<IStrictEnergyHandler>
    private static boolean capLookupAttempted = false;

    private static final ResourceLocation STRICT_ENERGY_KEY =
        new ResourceLocation(BackpackArsenalMod.MODID, "mekanism_strict_energy");

    /** Mekanism mod がロードされているか。 */
    public static boolean isMekanismLoaded() {
        return ModList.get().isLoaded("mekanism");
    }

    /**
     * Mekanism がロードされていれば、 BE に Mekanism の STRICT_ENERGY capability を attach する。
     * これにより Mekanism cube / cable は backpack を **native の発電機** として認識する。
     */
    public static void tryAttachStrictEnergy(AttachCapabilitiesEvent<BlockEntity> event,
                                             BackpackFeProvider provider) {
        if (!isMekanismLoaded()) return;
        try {
            attachStrictEnergyCap(event, provider);
        } catch (Throwable t) {
            BackpackArsenalMod.LOGGER.warn(
                "[backpack_arsenal] failed to attach Mekanism STRICT_ENERGY cap: {}", t.toString());
        }
    }

    /** Mekanism API を実際に触る部分。 ここから先のスタックが Mekanism class load を起こす。 */
    private static void attachStrictEnergyCap(AttachCapabilitiesEvent<BlockEntity> event,
                                              BackpackFeProvider provider) {
        @SuppressWarnings("unchecked")
        Capability<mekanism.api.energy.IStrictEnergyHandler> cap =
            (Capability<mekanism.api.energy.IStrictEnergyHandler>) resolveStrictEnergyCap();
        if (cap == null) return;

        MekanismEnergyAdapter adapter = new MekanismEnergyAdapter(provider.storage);
        LazyOptional<mekanism.api.energy.IStrictEnergyHandler> opt = LazyOptional.of(() -> adapter);
        ICapabilityProvider mekProvider = new ICapabilityProvider() {
            @Override
            public <T> LazyOptional<T> getCapability(Capability<T> queriedCap,
                                                    net.minecraft.core.Direction side) {
                return queriedCap == cap ? opt.cast() : LazyOptional.empty();
            }
        };
        event.addCapability(STRICT_ENERGY_KEY, mekProvider);
        event.addListener(opt::invalidate);
        BackpackArsenalMod.LOGGER.info(
            "[backpack_arsenal] attached Mekanism STRICT_ENERGY cap to BE @ {}",
            event.getObject().getBlockPos());
    }

    /** {@code mekanism.common.capabilities.Capabilities.STRICT_ENERGY} を解決。
     *  Mekanism class が無い環境ではここで NoClassDefFoundError → 呼び出し側で catch される。 */
    private static Object resolveStrictEnergyCap() {
        if (capLookupAttempted) return cachedCap;
        capLookupAttempted = true;
        try {
            cachedCap = mekanism.common.capabilities.Capabilities.STRICT_ENERGY;
        } catch (Throwable t) {
            BackpackArsenalMod.LOGGER.warn(
                "[backpack_arsenal] Mekanism.Capabilities.STRICT_ENERGY lookup failed: {}", t.toString());
            cachedCap = null;
        }
        return cachedCap;
    }
}
