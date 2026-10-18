package net.neoforged.forgestuck.compat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;

/**
 * Stand-in for Forge 1.20.1's {@code ModLoadingContext}. NeoForge moved config registration onto the
 * {@link ModContainer}; this forwards Forge's {@code registerConfig} calls to the active container, which (as in
 * Forge) is only meaningful while the mod is being constructed or loaded.
 */
public final class ModLoadingContext {
    private static final ModLoadingContext INSTANCE = new ModLoadingContext();
    private static final Map<IConfigSpec, ModConfig> REGISTERED_CONFIGS = new ConcurrentHashMap<>();

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
        registerConfig(type, spec, null);
    }

    public void registerConfig(ModConfig.Type type, IConfigSpec spec, String fileName) {
        if (spec.isEmpty()) {
            return;
        }
        ModContainer container = getActiveContainer();
        ModConfig config = fileName == null
                ? ConfigTracker.INSTANCE.registerConfig(type, spec, container)
                : ConfigTracker.INSTANCE.registerConfig(type, spec, container, fileName);
        REGISTERED_CONFIGS.put(spec, config);
    }

    static ModConfig registeredConfig(IConfigSpec spec) {
        return REGISTERED_CONFIGS.get(spec);
    }
}
