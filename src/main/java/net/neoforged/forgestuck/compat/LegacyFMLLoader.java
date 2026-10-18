package net.neoforged.forgestuck.compat;

import net.neoforged.fml.loading.FMLEnvironment;

public final class LegacyFMLLoader {
    private LegacyFMLLoader() {}

    public static boolean isProduction() {
        return FMLEnvironment.production;
    }
}
