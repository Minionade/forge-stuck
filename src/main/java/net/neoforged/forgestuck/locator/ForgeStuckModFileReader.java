package net.neoforged.forgestuck.locator;

import cpw.mods.jarhandling.JarContents;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileReader;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conservative legacy-demod detector.
 *
 * The goal is simple and safe: if a JAR looks like a Forge 1.20.1 mod (META-INF/mods.toml exists,
 * neoforge.mods.toml is absent), we log it and return null. We intentionally avoid claiming the JAR and
 * therefore avoid crashing NeoForge with unsupported discovery internals.
 */
public class ForgeStuckModFileReader implements IModFileReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(ForgeStuckModFileReader.class);
    private static final Pattern MOD_ID_PATTERN = Pattern.compile("(?m)^modId\s*=\s*\"?([A-Za-z0-9_.-]+)\"?");
    private static final Pattern DISPLAY_NAME_PATTERN = Pattern.compile("(?m)^displayName\s*=\s*\"?([^\n\r\"]+)\"?");

    @Override
    public IModFile read(JarContents contents, ModFileDiscoveryAttributes attrs) {
        try {
            Object entry = readEntry(contents, "META-INF/mods.toml");
            if (entry == null) {
                return null;
            }

            Object neoforgeEntry = readEntry(contents, "META-INF/neoforge.mods.toml");
            if (neoforgeEntry != null) {
                return null;
            }

            String content = entry.toString();
            Matcher modId = MOD_ID_PATTERN.matcher(content);
            Matcher displayName = DISPLAY_NAME_PATTERN.matcher(content);
            String modIdValue = modId.find() ? modId.group(1) : "unknown";
            String display = displayName.find() ? displayName.group(1) : modIdValue;
            LOGGER.info("[Forge Stuck!!] Detected legacy Forge 1.20.1 JAR candidate: {} ({})", display, modIdValue);
        } catch (Throwable t) {
            LOGGER.debug("[Forge Stuck!!] Legacy mod detection failed safely", t);
        }

        return null;
    }

    private static Object readEntry(JarContents contents, String path) {
        try {
            Method method = JarContents.class.getMethod("getEntry", String.class);
            return method.invoke(contents, path);
        } catch (Throwable t) {
            return null;
        }
    }
}
