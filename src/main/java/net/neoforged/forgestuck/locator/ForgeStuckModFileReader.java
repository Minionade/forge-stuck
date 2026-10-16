package net.neoforged.forgestuck.locator;

import cpw.mods.jarhandling.JarContents;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileReader;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;

/**
 * Conservative IModFileReader: intentionally returns null for now so the discovery process is unmodified.
 * This avoids compile-time dependencies on internal NeoForge/FML locator helpers and keeps the reader
 * safe until we add a proper in-memory ModFile synthesis implementation.
 */
public class ForgeStuckModFileReader implements IModFileReader {

    @Override
    public IModFile read(JarContents contents, ModFileDiscoveryAttributes attrs) {
        // Very conservative: do not claim any JARs yet. We'll implement detection + synthesis later.
        // Avoid calling any methods on JarContents to remain compatible with differing NeoForge/FML internals.
        return null;
    }
}
