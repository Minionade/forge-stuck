package net.neoforged.forgestuck.compat;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.FileConfig;
import java.nio.file.Path;
import java.util.Objects;
import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Adapts Forge's direct config assignment to NeoForge's loaded-config lifecycle. */
public final class LegacyConfigSpec {
    private LegacyConfigSpec() {}

    public static void setConfig(ModConfigSpec spec, CommentedConfig config) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(config, "config");
        ModConfig modConfig = ModLoadingContext.registeredConfig(spec);
        if (modConfig == null) {
            throw new IllegalStateException("ForgeConfigSpec.setConfig called for an unregistered config spec");
        }
        IConfigSpec.ILoadedConfig loadedConfig = modConfig.getLoadedConfig();
        if (loadedConfig == null && config instanceof FileConfig fileConfig) {
            Path path = fileConfig.getNioPath().toAbsolutePath();
            if (!path.getFileName().toString().equals(modConfig.getFileName())) {
                throw new IllegalStateException("ForgeConfigSpec.setConfig file " + path.getFileName()
                        + " does not match registered config " + modConfig.getFileName());
            }
            fileConfig.save();
            ConfigTracker.INSTANCE.loadConfigs(modConfig.getType(), path.getParent());
            loadedConfig = modConfig.getLoadedConfig();
        }
        if (loadedConfig == null) {
            throw new IllegalStateException("ForgeConfigSpec.setConfig called before NeoForge loaded "
                    + modConfig.getFileName());
        }

        CommentedConfig target = loadedConfig.config();
        if (target != config) {
            target.clear();
            target.addAll(config);
            target.putAllComments(config);
        }
        boolean alreadyCorrect = spec.isCorrect(target);
        spec.acceptConfig(loadedConfig);
        if (alreadyCorrect) {
            loadedConfig.save();
        }
    }
}
