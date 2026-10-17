package net.neoforged.forgestuck.locator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Detects legacy Forge jars by reading the real on-disk jar (no JarContents reflection).
 */
public final class LegacyJarDetector {
    public static final String FORGE_MODS_TOML = "META-INF/mods.toml";
    public static final String NEOFORGE_MODS_TOML = "META-INF/neoforge.mods.toml";

    private LegacyJarDetector() {}

    /**
     * NeoForge's own descriptor is authoritative, including in dual-loader jars. Leave runtime/version
     * validation to NeoForge rather than rewriting an incompatible native mod into a legacy candidate.
     */
    public static boolean isNativeNeoForge(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            return zip.getEntry(NEOFORGE_MODS_TOML) != null;
        }
    }

    /** A legacy candidate has a {@code mods.toml} and no {@code neoforge.mods.toml}. */
    public static boolean isLegacyCandidate(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            return zip.getEntry(FORGE_MODS_TOML) != null && zip.getEntry(NEOFORGE_MODS_TOML) == null;
        }
    }

    /** @return the content of {@code META-INF/mods.toml} or null when absent. */
    public static String readModsToml(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry(FORGE_MODS_TOML);
            if (entry == null) return null;
            try (InputStream in = zip.getInputStream(entry)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }
}
