package net.neoforged.forgestuck.compat;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;

/**
 * Stand-in for Forge 1.20.1's {@code ModLoadingContext}. NeoForge moved config registration onto the
 * {@link ModContainer}; this forwards Forge's {@code registerConfig} calls to the active container, which (as in
 * Forge) is only meaningful while the mod is being constructed or loaded.
 */
public final class ModLoadingContext {
    private static final ModLoadingContext INSTANCE = new ModLoadingContext();

    private ModLoadingContext() {}

    public static ModLoadingContext get() {
        return INSTANCE;
    }

    public ModContainer getActiveContainer() {
        return net.neoforged.fml.ModLoadingContext.get().getActiveContainer();
    }

    public String getActiveNamespace() {
        return net.neoforged.fml.ModLoadingContext.get().getActiveNamespace();
    }

    public void registerConfig(ModConfig.Type type, IConfigSpec spec) {
        getActiveContainer().registerConfig(type, spec);
    }

    public void registerConfig(ModConfig.Type type, IConfigSpec spec, String fileName) {
        getActiveContainer().registerConfig(type, spec, fileName);
    }
}
