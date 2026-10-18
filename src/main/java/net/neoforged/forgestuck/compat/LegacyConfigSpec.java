package net.neoforged.forgestuck.compat;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.FileConfig;
import java.util.Objects;
import net.neoforged.fml.config.IConfigSpec.ILoadedConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Adapts Forge's direct config assignment to NeoForge's loaded-config lifecycle. */
public final class LegacyConfigSpec {
    private LegacyConfigSpec() {}

    public static void setConfig(ModConfigSpec spec, CommentedConfig config) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(config, "config");
        spec.acceptConfig(new ILoadedConfig() {
            @Override
            public CommentedConfig config() {
                return config;
            }

            @Override
            public void save() {
                if (config instanceof FileConfig fileConfig) {
                    fileConfig.save();
                }
            }
        });
    }
}
