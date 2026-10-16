package net.neoforged.forgestuck.locator;

import cpw.mods.jarhandling.JarContents;
import net.neoforged.fml.loading.moddiscovery.AbstractJarFileModLocator;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileReader;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;

import java.util.Collections;
import java.util.List;

/**
 * Safe reader implementation used by Forge Stuck!!.
 *
 * It intentionally performs a non-claiming read by default to avoid crashing NeoForge if a JAR is not a
 * valid legacy Forge mod candidate. The real compatibility layer will later inspect the JAR and synthesize
 * a mod file in-memory when appropriate.
 */
public class ForgeStuckModFileReader implements IModFileReader {
    @Override
    public IModFile read(JarContents contents, ModFileDiscoveryAttributes attrs) {
        try {
            String legacyToml = contents.getEntry("META-INF/mods.toml") != null ? "mods.toml" : null;
            if (legacyToml == null) {
                return null;
            }
        } catch (Throwable ignored) {
            return null;
        }

        // Safe default: do not claim the JAR. We only accept a JAR once we have a validated 1.20.1 mod payload.
        return null;
    }
}
