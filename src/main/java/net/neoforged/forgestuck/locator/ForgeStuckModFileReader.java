package net.neoforged.forgestuck.locator;

import cpw.mods.jarhandling.JarContents;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileReader;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;

/**
 * Minimal, safe IModFileReader implementation used by Forge Stuck!!
 *
 * This implementation intentionally does not claim any JARs yet and returns null from read().
 * It exists so the installed JAR does not crash NeoForge with AbstractMethodError during discovery.
 *
 * Later: expand read() to detect legacy Forge 1.20.1 JARs (META-INF/mods.toml without neoforge.mods.toml)
 * and synthesize a proper IModFile to hand to NeoForge.
 */
public class ForgeStuckModFileReader implements IModFileReader {

    @Override
    public IModFile read(JarContents contents, ModFileDiscoveryAttributes attrs) {
        // Safe default: do not claim the JAR. Return null to allow NeoForge's normal discovery.
        try {
            if (contents.getEntry("META-INF/mods.toml") == null) {
                return null;
            }
        } catch (Throwable t) {
            // Swallow exceptions to avoid breaking discovery.
            return null;
        }
        return null;
    }
}
