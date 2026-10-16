package net.neoforged.forgestuck.compat;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reflection-safe detection helper for legacy Forge 1.20.1 mod JARs.
 */
public final class LegacyModDiscovery {
    private static final Pattern MOD_ID_PATTERN = Pattern.compile("(?m)^modId\\s*=\\s*\"?([A-Za-z0-9_.-]+)\"?");
    private static final Pattern DISPLAY_NAME_PATTERN = Pattern.compile("(?m)^displayName\\s*=\\s*\"?([^\\n\\r\"]+)\"?");

    private LegacyModDiscovery() {
    }

    public static LegacyModDescriptor detectLegacyMod(Object jarContents) {
        if (jarContents == null) {
            return null;
        }

        try {
            Object modsToml = invokeGetEntry(jarContents, "META-INF/mods.toml");
            if (modsToml == null) {
                return null;
            }

            Object neoforgeToml = invokeGetEntry(jarContents, "META-INF/neoforge.mods.toml");
            if (neoforgeToml != null) {
                return null;
            }

            String content = String.valueOf(modsToml);
            String modId = matchFirst(MOD_ID_PATTERN, content, "unknown");
            String display = matchFirst(DISPLAY_NAME_PATTERN, content, modId);
            return LegacyModDescriptor.builder()
                .modId(modId)
                .displayName(display)
                .entryName("META-INF/mods.toml")
                .fileName("legacy-forge-mod")
                .build();
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object invokeGetEntry(Object jarContents, String entryName) {
        try {
            Method method = jarContents.getClass().getMethod("getEntry", String.class);
            return method.invoke(jarContents, entryName);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String matchFirst(Pattern pattern, String value, String fallback) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1) : fallback;
    }

    // Helper wrappers used by other code paths
    public static String matchModIdFromToml(String toml) {
        return matchFirst(MOD_ID_PATTERN, toml == null ? "" : toml, "unknown");
    }

    public static String matchDisplayNameFromToml(String toml, String fallback) {
        return matchFirst(DISPLAY_NAME_PATTERN, toml == null ? "" : toml, fallback);
    }
}
