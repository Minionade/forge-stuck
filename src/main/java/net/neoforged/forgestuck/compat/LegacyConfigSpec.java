package net.neoforged.forgestuck.compat;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Adapts Forge's direct config assignment to NeoForge's loaded-config lifecycle. */
public final class LegacyConfigSpec {
    private LegacyConfigSpec() {}

    public static boolean isLoaded(ModConfigSpec spec) {
        return spec != null && spec.isLoaded();
    }

    public static void setConfig(ModConfigSpec spec, CommentedConfig config) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(config, "config");
        ModConfig modConfig = ModLoadingContext.registeredConfig(spec);
        if (modConfig == null) {
            throw new IllegalStateException("ForgeConfigSpec.setConfig called for an unregistered config spec");
        }
        IConfigSpec.ILoadedConfig loadedConfig = modConfig.getLoadedConfig();
        if (loadedConfig == null) {
            CommentedConfig corrected = CommentedConfig.inMemory();
            corrected.addAll(config);
            corrected.putAllComments(config);
            spec.correct(corrected);
            StringWriter writer = new StringWriter();
            new TomlWriter().write(corrected, writer);
            ConfigTracker.acceptSyncedConfig(modConfig, writer.toString().getBytes(StandardCharsets.UTF_8));
            return;
        }

        CommentedConfig target = loadedConfig.config();
        if (target != config) {
            target.clear();
            target.addAll(config);
            target.putAllComments(config);
        }
        if (!spec.isCorrect(target)) {
            spec.correct(target);
        }
        spec.acceptConfig(loadedConfig);
    }
}
