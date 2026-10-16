export interface JavaSourceFile {
  path: string;
  filename: string;
  category: 'core' | 'shim' | 'adapter' | 'config' | 'locator';
  description: string;
  code: string;
}

export const JAVA_PROJECT_FILES: JavaSourceFile[] = [
  {
    path: 'src/main/java/net/neoforged/forgestuck/ForgeStuckMod.java',
    filename: 'ForgeStuckMod.java',
    category: 'core',
    description: 'Main NeoForge 1.21.1 mod entrypoint for "Forge Stuck!!", lifecycle events, and subsystem initialization',
    code: `package net.neoforged.forgestuck;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.forgestuck.adapter.AdvancedGeneratorsMultiblockAdapter;
import net.neoforged.forgestuck.adapter.ForestryGeneticsAdapter;
import net.neoforged.forgestuck.adapter.ThermalSeriesAdapter;
import net.neoforged.forgestuck.shim.LegacyCapabilityBridge;
import net.neoforged.forgestuck.shim.LegacyItemStackShim;
import net.neoforged.forgestuck.shim.LegacyNetworkBridge;
import org.slf4j.Logger;

/**
 * Forge Stuck!!: NeoForge 1.21.1 Runtime Compatibility Bridge for Forge 1.20.1 mods.
 */
@Mod(ForgeStuckMod.MODID)
public class ForgeStuckMod {
    public static final String MODID = "forgestuck";
    public static final String VERSION = "1.21.1-1.1.0";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ForgeStuckMod(IEventBus modEventBus) {
        LOGGER.info("[Forge Stuck!!] Unsticking 1.20.1 mods for NeoForge 1.21.1 v{}", VERSION);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::onLoadComplete);

        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(LegacyItemStackShim.class);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("[Forge Stuck!!] Bootstrapping Legacy Forge API Shim Layer...");
        ThermalSeriesAdapter.init();
        ForestryGeneticsAdapter.init();
        AdvancedGeneratorsMultiblockAdapter.init();
        LOGGER.info("[Forge Stuck!!] Adapters unstuck: Thermal Series, Gendustry CE, Advanced Generators");
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        LOGGER.info("[Forge Stuck!!] Registering NeoForge 1.21.1 Capability Bridges...");
        LegacyCapabilityBridge.register(event);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        LOGGER.info("[Forge Stuck!!] Registering Legacy SimpleChannel Network Forwarders...");
        LegacyNetworkBridge.registerPayloadHandlers(event);
    }

    private void onLoadComplete(FMLLoadCompleteEvent event) {
        LOGGER.info("[Forge Stuck!!] Successfully un-stuck Forge 1.20.1 mods! All shims active.");
    }
}
`,
  },
  {
    path: 'src/main/java/net/neoforged/forgestuck/locator/ForgeStuckModLocator.java',
    filename: 'ForgeStuckModLocator.java',
    category: 'locator',
    description: 'IModLocator that scans /mods/ directly for 1.20.1 Forge JARs and presents them to NeoForge without any subfolders',
    code: `package net.neoforged.forgestuck.locator;

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
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("modId\\s*=\\s*[\"']([^\"']+)[\"']").matcher(toml);
        if (m.find()) return m.group(1);
        return filename.replaceAll("\\.jar$", "").replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
    }

    static String convertForgeTomlToNeoForge(String forgeToml, String modId) {
        String neoToml = forgeToml
            .replaceAll("loaderVersion=\"\\[47,\\)\"", "loaderVersion=\"[2,)\"")
            .replaceAll("modLoader=\"javafml\"", "modLoader=\"javafml\"\nloaderVersion=\"[2,)\"");

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
`,
  },
  // New: ForgeStuckModFileReader skeleton - safe initial implementation
  {
    path: 'src/main/java/net/neoforged/forgestuck/locator/ForgeStuckModFileReader.java',
    filename: 'ForgeStuckModFileReader.java',
    category: 'locator',
    description: 'IModFileReader skeleton that safely returns null (non-claiming) for unsupported JARs. Implement proper synthesis later.',
    code: `package net.neoforged.forgestuck.locator;

import cpw.mods.jarhandling.JarContents;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileReader;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;

/**
 * Minimal, safe IModFileReader implementation used by Forge Stuck!!
 *
 * This implementation intentionally does not claim any JARs yet and returns null from read().
 * It exists so the generated JAR does not crash NeoForge with AbstractMethodError during discovery.
 *
 * Later: expand read() to detect legacy Forge 1.20.1 JARs (META-INF/mods.toml without neoforge.mods.toml)
 * and synthesize a proper IModFile to hand to NeoForge.
 */
public class ForgeStuckModFileReader implements IModFileReader {

    @Override
    public IModFile read(JarContents contents, ModFileDiscoveryAttributes attrs) {
        // Safe default: do not claim the JAR. Return null to allow NeoForge's normal discovery.
        try {
            // Example detection (non-claiming): if someone wants to expand, inspect contents here.
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
`,
  }
  // rest of JAVA_PROJECT_FILES entries are unchanged and will be added by existing code
];
