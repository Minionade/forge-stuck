package net.neoforged.forgestuck.compat;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

/** Installed by transformed mod entrypoints, including mods with only automatic subscribers. */
public final class LegacyRuntimeBootstrap {
    private LegacyRuntimeBootstrap() {}
    public static void install() {
        LegacyDamageBridge.install();
        LegacyHurtBridge.install();
        LegacyStrictNBTIngredient.register();
        // The existing installer guards duplicate registration and loads client hooks only on Dist.CLIENT.
        LegacyTickBridge.install();
        LegacyPotionColorBridge.install();
        LegacyBucketFluidInteraction.install();
        var modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        LegacyToolStackItemPredicateAdapter.install(modEventBus);
        LegacyLootFunctionRegistration.install(modEventBus);
        LegacyBlockEnergyCapabilityCompat.install(modEventBus);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            LegacyGendustryFluidClientCompat.install(modEventBus);
        }
    }
}
