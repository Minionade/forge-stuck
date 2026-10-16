package net.neoforged.forgestuck.locator;

import cpw.mods.jarhandling.JarContents;
import net.neoforged.forgestuck.compat.LegacyModDescriptor;
import net.neoforged.forgestuck.compat.LegacyModDiscovery;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileReader;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Safe legacy detection hook: if a JAR looks like a Forge 1.20.1 mod, log it and return null.
 * We intentionally do not claim the JAR so we do not break NeoForge discovery.
 */
public class ForgeStuckModFileReader implements IModFileReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(ForgeStuckModFileReader.class);

    @Override
    public IModFile read(JarContents contents, ModFileDiscoveryAttributes attrs) {
        try {
            LegacyModDescriptor descriptor = LegacyModDiscovery.detectLegacyMod(contents);
            if (descriptor == null) {
                return null;
            }

            LOGGER.info("[Forge Stuck!!] Detected legacy Forge 1.20.1 mod candidate: {} ({}) from {}",
                descriptor.displayName(), descriptor.modId(), descriptor.entryName());
        } catch (Throwable t) {
            LOGGER.debug("[Forge Stuck!!] Legacy mod detection failed safely", t);
        }

        return null;
    }
}
