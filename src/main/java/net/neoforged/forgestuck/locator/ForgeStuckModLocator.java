package net.neoforged.forgestuck.locator;

import net.neoforged.fml.loading.moddiscovery.AbstractJarFileModLocator;
import net.neoforged.fml.loading.moddiscovery.ModFile;

import java.util.Collections;
import java.util.List;

/**
 * Conservative locator for legacy Forge 1.20.1 JARs.
 *
 * This is intentionally safe and non-claiming for now. Once the compatibility pipeline is stable,
 * it can parse legacy mods.toml entries and synthesize a compatible ModFile in-memory.
 */
public class ForgeStuckModLocator extends AbstractJarFileModLocator {
    @Override
    public String name() {
        return "forgestuck_legacy_locator";
    }

    @Override
    public List<ModFile> scanMods() {
        return Collections.emptyList();
    }

    @Override
    public String toString() {
        return "Forge Stuck!! Legacy Locator";
    }
}
