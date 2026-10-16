package net.neoforged.forgestuck.locator;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.moddiscovery.AbstractJarFileModLocator;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.loading.moddiscovery.ModFileFactory;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Custom Mod Locator for Forge Stuck!!
 *
 * Scans the standard /mods/ directory directly for 1.20.1 Forge JARs — exactly
 * like Sinytra Connector and RetroMod. No subfolders. No moving files. Just drop
 * your old Forge mods alongside forgestuck.jar and launch.
 */
public class ForgeStuckModLocator extends AbstractJarFileModLocator {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NAME = "forgestuck_1201_locator";

    @Override
    public List<ModFile> scanMods() {
        Path modsDir = FMLPaths.MODSDIR.get();
        if (!Files.isDirectory(modsDir)) {
            return Collections.emptyList();
        }

        List<ModFile> discovered = new ArrayList<>();

        try (Stream<Path> files = Files.list(modsDir)) {
            files.filter(p -> p.toString().toLowerCase().endsWith(".jar")
                           && !p.getFileName().toString().startsWith("forgestuck"))
                .forEach(jarPath -> {
                    try (JarFile jar = new JarFile(jarPath.toFile())) {
                        JarEntry neoToml = jar.getJarEntry("META-INF/neoforge.mods.toml");
                        JarEntry forgeToml = jar.getJarEntry("META-INF/mods.toml");

                        // Only handle 1.20.1 Forge mods (has mods.toml but NOT neoforge.mods.toml)
                        if (neoToml == null && forgeToml != null) {
                            String legacyTomlContent;
                            try (InputStream is = jar.getInputStream(forgeToml)) {
                                legacyTomlContent = new String(is.readAllBytes());
                            }

                            String modId = extractModId(legacyTomlContent, jarPath.getFileName().toString());
                            String synthesizedToml = convertForgeTomlToNeoForge(legacyTomlContent, modId);

                            ModFile modFile = ModFileFactory.FACTORY.build(jarPath, this, synthesizedToml);
                            discovered.add(modFile);
                            LOGGER.info("[Forge Stuck!!] Located and registered 1.20.1 mod directly from /mods/: {}", jarPath.getFileName());
                        }
                    } catch (Exception e) {
                        LOGGER.error("[Forge Stuck!!] Error processing {}: {}", jarPath.getFileName(), e.getMessage());
                    }
                });
        } catch (IOException e) {
            LOGGER.error("[Forge Stuck!!] Failed scanning /mods/: {}", e.getMessage());
        }

        return discovered;
    }

    static String extractModId(String toml, String filename) {
        // Accept both single-quoted and double-quoted modId values
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("modId\\s*=\\s*['\\\"]([^'\\\"]+)['\\\"]").matcher(toml);
        if (m.find()) return m.group(1);
        return filename.replaceAll("\\.jar$", "").replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
    }

    static String convertForgeTomlToNeoForge(String forgeToml, String modId) {
        // Simple text replacements to adapt common Forge mods.toml entries for NeoForge
        String neoToml = forgeToml
            .replace("loaderVersion=\"[47,)\"", "loaderVersion=\"[2,)\"")
            .replace("modLoader=\"javafml\"", "modLoader=\"javafml\"\nloaderVersion=\"[2,)\"");

        // Inject NeoForge dependency if not present
        if (!neoToml.contains("modId=\"neoforge\"")) {
            neoToml += "\n\n# Automatically injected by Forge Stuck!!\n" +
                       "[[dependencies." + modId + "]]\n" +
                       "    modId=\"neoforge\"\n" +
                       "    type=\"required\"\n" +
                       "    versionRange=\"[21.1.0,)\"\n" +
                       "    ordering=\"NONE\"\n" +
                       "    side=\"BOTH\"\n";
        }

        return neoToml;
    }

    @Override
    public String name() { return NAME; }

    @Override
    public String toString() { return "ForgeStuck 1.20.1 Compatibility Locator (flat /mods/ scanner)"; }
}
