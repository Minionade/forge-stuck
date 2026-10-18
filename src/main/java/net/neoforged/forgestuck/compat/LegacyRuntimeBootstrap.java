package net.neoforged.forgestuck.compat;

/** Installed by transformed mod entrypoints, including mods with only automatic subscribers. */
public final class LegacyRuntimeBootstrap {
    private LegacyRuntimeBootstrap() {}
    public static void install() {
        LegacyHurtBridge.install();
        // The existing installer guards duplicate registration and loads client hooks only on Dist.CLIENT.
        LegacyTickBridge.install();
    }
}
