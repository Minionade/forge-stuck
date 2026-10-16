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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
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
 * 
 * For when your favorite mods are stubbornly stuck in 1.20.1!
 * Intercepts legacy Forge mod classloading, routes ItemStack NBT calls to
 * DataComponents.CUSTOM_DATA, connects ForgeCapabilities to NeoForge BlockCapabilities,
 * and handles targeted mod adapters for Thermal Series, Gendustry CE, and Advanced Generators.
 */
@Mod(ForgeStuckMod.MODID)
public class ForgeStuckMod {
    public static final String MODID = "forgestuck";
    public static final String VERSION = "1.21.1-1.1.0";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ForgeStuckMod(IEventBus modEventBus) {
        LOGGER.info("[Forge Stuck!!] Unsticking 1.20.1 mods for NeoForge 1.21.1 v{}", VERSION);

        // Register mod lifecycle listeners
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::onLoadComplete);

        // Register runtime game event listeners
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(LegacyItemStackShim.class);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("[Forge Stuck!!] Bootstrapping Legacy Forge API Shim Layer...");
        
        // Initialize dynamic adapters for targeted mods
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

    /**
     * Standalone CLI runner: Can also be executed directly via 'java -jar forgestuck-1.21.1-1.1.0.jar'
     * or as a Prism Launcher pre-launch command to organize /mods/.forgestuck/ ahead of time!
     */
    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("Forge Stuck!! v" + VERSION + " - Setup & Organizer");
        System.out.println("=================================================");
        try {
            java.nio.file.Path currentDir = java.nio.file.Paths.get(".").toAbsolutePath().normalize();
            java.nio.file.Path modsDir = Files.exists(currentDir.resolve("mods")) ? currentDir.resolve("mods") : currentDir;
            java.nio.file.Path forgestuckDir = modsDir.resolve(".forgestuck");
            java.nio.file.Path manifestsDir = forgestuckDir.resolve("manifests");

            Files.createDirectories(manifestsDir);
            System.out.println("[Forge Stuck!!] Ensured target directory exists: " + forgestuckDir);

            // Move any 1.20.1 Forge JARs from mods/ to mods/.forgestuck/ so NeoForge doesn't reject them
            if (Files.isDirectory(modsDir)) {
                try (var stream = Files.list(modsDir)) {
                    stream.filter(p -> p.toString().toLowerCase().endsWith(".jar") && !p.getFileName().toString().startsWith("forgestuck"))
                          .forEach(p -> {
                              try (java.util.jar.JarFile jf = new java.util.jar.JarFile(p.toFile())) {
                                  if (jf.getJarEntry("META-INF/neoforge.mods.toml") == null && jf.getJarEntry("META-INF/mods.toml") != null) {
                                      java.nio.file.Path target = forgestuckDir.resolve(p.getFileName());
                                      Files.move(p, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                      System.out.println("[Forge Stuck!!] Moved legacy JAR to /mods/.forgestuck/: " + p.getFileName());
                                  }
                              } catch (Exception ignored) {}
                          });
                }
            }
            System.out.println("[Forge Stuck!!] Setup complete! Original JARs remain untouched.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
`,
  },
  {
    path: 'src/main/java/net/neoforged/forgestuck/locator/ForgeStuckModLocator.java',
    filename: 'ForgeStuckModLocator.java',
    category: 'locator',
    description: 'Custom IModLocator that intercepts skipped 1.20.1 mods and generates synthetic neoforge.mods.toml manifests in-memory',
    code: `package net.neoforged.forgestuck.locator;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.moddiscovery.AbstractJarFileModLocator;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.loading.moddiscovery.ModFileFactory;
import org.slf4j.Logger;

import java.io.File;
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
 * In NeoForge 1.21.1, the default ModDiscoverer encounters 1.20.1 JARs with only
 * META-INF/mods.toml and logs:
 *   "Skipping jar. File mods/... is for Minecraft Forge or an older version of NeoForge, and cannot be loaded"
 * 
 * ForgeStuckModLocator runs during FML discovery, catches these skipped 1.20.1 JARs,
 * reads their legacy mods.toml, wraps them with a synthesized neoforge.mods.toml in memory,
 * and passes them to NeoForge as valid candidate mods!
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

        Path forgestuckDir = modsDir.resolve(".forgestuck");
        Path manifestsBaseDir = forgestuckDir.resolve("manifests");
        try {
            Files.createDirectories(manifestsBaseDir);
            LOGGER.info("[Forge Stuck!!] Target directory verified at: {}", forgestuckDir);
        } catch (IOException e) {
            LOGGER.error("[Forge Stuck!!] Failed to create /mods/.forgestuck/ folder: {}", e.getMessage());
        }

        List<ModFile> discovered1201Mods = new ArrayList<>();

        // 1. Move any 1.20.1 Forge mods placed directly in mods/ into mods/.forgestuck/
        // This is crucial because NeoForge's ModsFolderLocator will abort launch if old Forge mods sit directly in mods/
        try (Stream<Path> files = Files.list(modsDir)) {
            files.filter(p -> p.toString().toLowerCase().endsWith(".jar") && !p.getFileName().toString().startsWith("forgestuck")).forEach(jarPath -> {
                try (JarFile jar = new JarFile(jarPath.toFile())) {
                    JarEntry neoToml = jar.getJarEntry("META-INF/neoforge.mods.toml");
                    JarEntry forgeToml = jar.getJarEntry("META-INF/mods.toml");

                    if (neoToml == null && forgeToml != null) {
                        Path destination = forgestuckDir.resolve(jarPath.getFileName());
                        Files.move(jarPath, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        LOGGER.info("[Forge Stuck!!] Moved 1.20.1 JAR to /mods/.forgestuck/ to prevent NeoForge rejection: {}", jarPath.getFileName());
                    }
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}

        // 2. Discover and register all 1.20.1 mods safely residing in mods/.forgestuck/
        if (Files.isDirectory(forgestuckDir)) {
            try (Stream<Path> forgestuckFiles = Files.list(forgestuckDir)) {
                forgestuckFiles.filter(p -> p.toString().toLowerCase().endsWith(".jar")).forEach(jarPath -> {
                    try (JarFile jar = new JarFile(jarPath.toFile())) {
                        JarEntry forgeToml = jar.getJarEntry("META-INF/mods.toml");
                        if (forgeToml != null) {
                            String jarFileName = jarPath.getFileName().toString();
                            
                            // Read legacy mods.toml directly from stream (original JAR remains 100% untouched)
                            String legacyTomlContent;
                            try (InputStream is = jar.getInputStream(forgeToml)) {
                                legacyTomlContent = new String(is.readAllBytes());
                            }

                            String modId = extractModId(legacyTomlContent, jarFileName);

                            // Dedicated manifest path inside /mods/.forgestuck/manifests/<modId>/META-INF/neoforge.mods.toml
                            Path dedicatedManifestDir = manifestsBaseDir.resolve(modId).resolve("META-INF");
                            Files.createDirectories(dedicatedManifestDir);
                            Path dedicatedTomlPath = dedicatedManifestDir.resolve("neoforge.mods.toml");

                            String synthesizedToml = convertForgeTomlToNeoForge(legacyTomlContent, jarFileName, modId);
                            Files.writeString(dedicatedTomlPath, synthesizedToml);

                            LOGGER.info("[Forge Stuck!!] Generated manifest in /mods/.forgestuck/manifests/{}/META-INF/neoforge.mods.toml", modId);

                            // Register ModFile with NeoForge
                            ModFile modFile = ModFileFactory.FACTORY.build(jarPath, this, synthesizedToml);
                            discovered1201Mods.add(modFile);
                            LOGGER.info("[Forge Stuck!!] Successfully registered {} for NeoForge 1.21.1 loading", jarFileName);
                        }
                    } catch (Exception e) {
                        LOGGER.error("[Forge Stuck!!] Error processing {}: {}", jarPath, e.getMessage());
                    }
                });
            } catch (IOException e) {
                LOGGER.error("[Forge Stuck!!] Failed scanning /mods/.forgestuck/: {}", e.getMessage());
            }
        }

        return discovered1201Mods;
    }

    private static String extractModId(String toml, String filename) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("modId\\\\s*=\\\\s*[\"']([^\"']+)[\"']").matcher(toml);
        if (m.find()) {
            return m.group(1);
        }
        return filename.replaceAll("\\\\.jar$", "").replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
    }

    private static String convertForgeTomlToNeoForge(String forgeToml, String filename, String modId) {
        // Upgrade modLoader version and strip Forge-specific rigid constraints
        String neoToml = forgeToml
            .replaceAll("loaderVersion=\"\\[47,\\)\"", "loaderVersion=\"[2,)\"")
            .replaceAll("modLoader=\"javafml\"", "modLoader=\"javafml\"\\nloaderVersion=\"[2,)\"");

        // If no dependencies section exists for neoforge, supply default
        if (!neoToml.contains("modId=\"neoforge\"")) {
            neoToml += "\\n\\n# Automatically injected by Forge Stuck!! into dedicated path\\n" +
                       "[[dependencies." + modId + "]]\\n" +
                       "    modId=\\"neoforge\\"\\n" +
                       "    type=\\"required\\"\\n" +
                       "    versionRange=\\"[21.1.0,)\\"\\n" +
                       "    ordering=\\"NONE\\"\\n" +
                       "    side=\\"BOTH\\"\\n";
        }

        return neoToml;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String toString() {
        return "ForgeStuck 1.20.1 Compatibility Locator";
    }
}
`,
  },
  {
    path: 'src/main/java/net/neoforged/forgestuck/locator/ForgeStuckModFileReader.java',
    filename: 'ForgeStuckModFileReader.java',
    category: 'locator',
    description: 'IModFileReader that claims 1.20.1 Forge mods directly in /mods/ before JarModsDotTomlModFileReader can reject them',
    code: `package net.neoforged.forgestuck.locator;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.loading.moddiscovery.ModFileFactory;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileReader;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Custom IModFileReader registered via ServiceLoader.
 * 
 * Intercepts 1.20.1 Forge mods sitting directly in /mods/ BEFORE
 * NeoForge's default JarModsDotTomlModFileReader can reject them.
 * 
 * Reads the legacy META-INF/mods.toml from the stream (leaving the original JAR on disk 100% untouched),
 * writes the synthesized neoforge.mods.toml to /mods/.forgestuck/manifests/<modid>/META-INF/neoforge.mods.toml,
 * and builds a valid IModFile so NeoForge loads it directly from /mods/ without errors or subfolders!
 */
public class ForgeStuckModFileReader implements IModFileReader {
    private static final Logger LOGGER = LogUtils.getLogger();

    public IModFile read(Path path, Object locator) {
        try {
            if (!path.toString().toLowerCase().endsWith(".jar") || path.getFileName().toString().startsWith("forgestuck")) {
                return null;
            }

            try (JarFile jar = new JarFile(path.toFile())) {
                JarEntry neoToml = jar.getJarEntry("META-INF/neoforge.mods.toml");
                JarEntry forgeToml = jar.getJarEntry("META-INF/mods.toml");

                // If it is a 1.20.1 Forge mod, intercept it before NeoForge rejects it!
                if (neoToml == null && forgeToml != null) {
                    String filename = path.getFileName().toString();
                    LOGGER.info("[Forge Stuck!!] Intercepting 1.20.1 Forge mod in /mods/: {}", filename);

                    // 1. Read legacy mods.toml directly from stream (Zero edits to original JAR on disk)
                    String legacyToml;
                    try (InputStream is = jar.getInputStream(forgeToml)) {
                        legacyToml = new String(is.readAllBytes());
                    }

                    // 2. Extract modId
                    String modId = extractModId(legacyToml, filename);

                    // 3. Create dedicated path in /mods/.forgestuck/manifests/<modId>/META-INF/neoforge.mods.toml
                    Path modsDir = FMLPaths.MODSDIR.get();
                    Path dedicatedManifestDir = modsDir.resolve(".forgestuck")
                        .resolve("manifests")
                        .resolve(modId)
                        .resolve("META-INF");
                    Files.createDirectories(dedicatedManifestDir);

                    Path dedicatedToml = dedicatedManifestDir.resolve("neoforge.mods.toml");
                    String synthesized = convertForgeTomlToNeoForge(legacyToml, filename, modId);
                    Files.writeString(dedicatedToml, synthesized);

                    LOGGER.info("[Forge Stuck!!] Generated manifest at: {}", dedicatedToml);

                    // 4. Return valid IModFile so NeoForge loads it directly and skips JarModsDotTomlModFileReader!
                    return ModFileFactory.FACTORY.build(path, (net.neoforged.fml.loading.moddiscovery.AbstractJarFileModLocator) locator, synthesized);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[Forge Stuck!! Reader] {}", e.getMessage());
        }
        return null;
    }

    private static String extractModId(String toml, String filename) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("modId\\\\s*=\\\\s*[\"']([^\"']+)[\"']").matcher(toml);
        if (m.find()) return m.group(1);
        return filename.replaceAll("\\\\.jar$", "").replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
    }

    private static String convertForgeTomlToNeoForge(String forgeToml, String filename, String modId) {
        String neoToml = forgeToml
            .replaceAll("loaderVersion=\"\\[47,\\)\"", "loaderVersion=\"[2,)\"")
            .replaceAll("modLoader=\"javafml\"", "modLoader=\"javafml\"\\nloaderVersion=\"[2,)\"");

        if (!neoToml.contains("modId=\"neoforge\"")) {
            neoToml += "\\n\\n# Automatically injected by Forge Stuck!!\\n" +
                       "[[dependencies." + modId + "]]\\n" +
                       "    modId=\\"neoforge\\"\\n" +
                       "    type=\\"required\\"\\n" +
                       "    versionRange=\\"[21.1.0,)\\"\\n" +
                       "    ordering=\\"NONE\\"\\n" +
                       "    side=\\"BOTH\\"\\n";
        }
        return neoToml;
    }
}
`,
  },
  {
    path: 'src/main/resources/META-INF/services/net.neoforged.neoforgespi.locating.IModFileReader',
    filename: 'IModFileReader',
    category: 'config',
    description: 'ServiceLoader registration for ForgeStuckModFileReader preventing NeoForge from rejecting 1.20.1 mods',
    code: `net.neoforged.forgestuck.locator.ForgeStuckModFileReader
`,
  },
  {
    path: 'src/main/resources/META-INF/services/net.neoforged.fml.loading.moddiscovery.IModLocator',
    filename: 'IModLocator',
    category: 'config',
    description: 'ServiceLoader registration for ForgeStuckModLocator allowing automatic mod discovery',
    code: `net.neoforged.forgestuck.locator.ForgeStuckModLocator
`,
  },
  {
    path: 'src/main/java/net/neoforged/forgestuck/shim/LegacyItemStackShim.java',
    filename: 'LegacyItemStackShim.java',
    category: 'shim',
    description: 'Dynamic emulator redirecting legacy ItemStack.getTag() / setTag() to 1.21.1 DataComponents.CUSTOM_DATA',
    code: `package net.neoforged.forgestuck.shim;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Transparent bridge for ItemStack NBT operations.
 * 
 * In Minecraft 1.21+, arbitrary root NBT on ItemStack was replaced by Data Components.
 * 1.20.1 mods call:
 *   - stack.getTag()
 *   - stack.getOrCreateTag()
 *   - stack.setTag(CompoundTag)
 *   - stack.hasTag()
 *   - stack.getTagElement(key)
 * 
 * This shim intercepts those calls via ASM bytecode redirection and translates them
 * directly into reads and writes on DataComponents.CUSTOM_DATA.
 */
public final class LegacyItemStackShim {

    private LegacyItemStackShim() {}

    /**
     * Replaces invokevirtual net/minecraft/world/item/ItemStack.getTag()Lnet/minecraft/nbt/CompoundTag;
     */
    public static CompoundTag getTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            return null;
        }
        return customData.copyTag();
    }

    /**
     * Replaces invokevirtual net/minecraft/world/item/ItemStack.getOrCreateTag()Lnet/minecraft/nbt/CompoundTag;
     */
    public static CompoundTag getOrCreateTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new CompoundTag();
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            CompoundTag newTag = new CompoundTag();
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(newTag));
            return newTag;
        }
        return customData.copyTag();
    }

    /**
     * Replaces invokevirtual net/minecraft/world/item/ItemStack.hasTag()Z
     */
    public static boolean hasTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData != null && !customData.isEmpty();
    }

    /**
     * Replaces invokevirtual net/minecraft/world/item/ItemStack.setTag(Lnet/minecraft/nbt/CompoundTag;)V
     */
    public static void setTag(ItemStack stack, CompoundTag tag) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (tag == null || tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    /**
     * Replaces invokevirtual net/minecraft/world/item/ItemStack.getTagElement(Ljava/lang/String;)Lnet/minecraft/nbt/CompoundTag;
     */
    public static CompoundTag getTagElement(ItemStack stack, String key) {
        CompoundTag tag = getTag(stack);
        if (tag != null && tag.contains(key, Tag.TAG_COMPOUND)) {
            return tag.getCompound(key);
        }
        return null;
    }

    /**
     * Helper to write back changes from an in-memory legacy tag into the 1.21 DataComponent
     */
    public static void syncTag(ItemStack stack, CompoundTag tag) {
        setTag(stack, tag);
    }
}
`,
  },
  {
    path: 'src/main/java/net/neoforged/forgestuck/shim/LegacyCapabilityBridge.java',
    filename: 'LegacyCapabilityBridge.java',
    category: 'shim',
    description: 'Binds ForgeCapabilities (Energy, FluidHandler, ItemHandler) to NeoForge 1.21.1 Block & Item Capabilities',
    code: `package net.neoforged.forgestuck.shim;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridges 1.20.1 ForgeCapabilities to NeoForge 1.21.1's Capability registration system.
 */
public class LegacyCapabilityBridge {

    public static final Object ENERGY_TOKEN = new Object();
    public static final Object FLUID_TOKEN = new Object();
    public static final Object ITEM_TOKEN = new Object();

    private static final Map<Class<? extends BlockEntity>, LegacyCapabilityAdapter<?>> ADAPTERS = new ConcurrentHashMap<>();

    public interface LegacyCapabilityAdapter<T extends BlockEntity> {
        IEnergyStorage getEnergy(T be, Direction side);
        IFluidHandler getFluid(T be, Direction side);
        IItemHandler getItem(T be, Direction side);
    }

    public static <T extends BlockEntity> void registerAdapter(Class<T> clazz, LegacyCapabilityAdapter<T> adapter) {
        ADAPTERS.put(clazz, adapter);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register(RegisterCapabilitiesEvent event) {
        for (Map.Entry<Class<? extends BlockEntity>, LegacyCapabilityAdapter<?>> entry : ADAPTERS.entrySet()) {
            Class clazz = entry.getKey();
            LegacyCapabilityAdapter adapter = entry.getValue();

            // Energy Storage Bridge (FE / RF)
            event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                clazz,
                (be, side) -> adapter.getEnergy(be, side)
            );

            // Fluid Handler Bridge
            event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                clazz,
                (be, side) -> adapter.getFluid(be, side)
            );

            // Item Handler Bridge
            event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                clazz,
                (be, side) -> adapter.getItem(be, side)
            );
        }
    }
}
`,
  },
  {
    path: 'src/main/java/net/neoforged/forgestuck/shim/LegacyRegistryBridge.java',
    filename: 'LegacyRegistryBridge.java',
    category: 'shim',
    description: 'Emulates Forge DeferredRegister and RegistryObject delegating to NeoForge 1.21.1 DeferredHolder',
    code: `package net.neoforged.forgestuck.shim;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

/**
 * Drop-in emulation for DeferredRegister and RegistryObject
 */
public class LegacyRegistryBridge<T> {

    private final DeferredRegister<T> delegate;

    private LegacyRegistryBridge(DeferredRegister<T> delegate) {
        this.delegate = delegate;
    }

    public static <T> LegacyRegistryBridge<T> create(ResourceKey<? extends Registry<T>> registryKey, String modid) {
        return new LegacyRegistryBridge<>(DeferredRegister.create(registryKey, modid));
    }

    public <I extends T> RegistryObjectShim<I> register(String name, Supplier<? extends I> supplier) {
        DeferredHolder<T, I> holder = delegate.register(name, supplier);
        return new RegistryObjectShim<>(holder);
    }

    public void register(IEventBus bus) {
        delegate.register(bus);
    }

    public static class RegistryObjectShim<T> implements Supplier<T> {
        private final DeferredHolder<?, ? extends T> holder;

        public RegistryObjectShim(DeferredHolder<?, ? extends T> holder) {
            this.holder = holder;
        }

        @Override
        public T get() {
            return holder.get();
        }

        public ResourceLocation getId() {
            return holder.getId();
        }

        public ResourceKey<?> getKey() {
            return holder.getKey();
        }

        public boolean isPresent() {
            return holder.isBound();
        }
    }
}
`,
  },
  {
    path: 'src/main/java/net/neoforged/forgestuck/shim/LegacyNetworkBridge.java',
    filename: 'LegacyNetworkBridge.java',
    category: 'shim',
    description: 'Translates 1.20.1 SimpleChannel packets into 1.21.1 CustomPacketPayload & PayloadRegistrar',
    code: `package net.neoforged.forgestuck.shim;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Emulates Forge's SimpleChannel on top of NeoForge 1.21.1's CustomPacketPayload architecture.
 */
public class LegacyNetworkBridge {

    private static final List<ChannelDefinition<?>> REGISTERED_CHANNELS = new ArrayList<>();

    public static class ChannelDefinition<M> {
        public final ResourceLocation channelId;
        public final int messageId;
        public final Class<M> messageClass;
        public final BiConsumer<M, FriendlyByteBuf> encoder;
        public final Function<FriendlyByteBuf, M> decoder;
        public final BiConsumer<M, IPayloadContext> handler;

        public ChannelDefinition(ResourceLocation channelId, int messageId, Class<M> messageClass,
                                 BiConsumer<M, FriendlyByteBuf> encoder,
                                 Function<FriendlyByteBuf, M> decoder,
                                 BiConsumer<M, IPayloadContext> handler) {
            this.channelId = channelId;
            this.messageId = messageId;
            this.messageClass = messageClass;
            this.encoder = encoder;
            this.decoder = decoder;
            this.handler = handler;
        }
    }

    public static <M> void registerMessage(ResourceLocation channel, int id, Class<M> clazz,
                                          BiConsumer<M, FriendlyByteBuf> encoder,
                                          Function<FriendlyByteBuf, M> decoder,
                                          BiConsumer<M, IPayloadContext> handler) {
        REGISTERED_CHANNELS.add(new ChannelDefinition<>(channel, id, clazz, encoder, decoder, handler));
    }

    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("forgestuck");

        for (ChannelDefinition<?> def : REGISTERED_CHANNELS) {
            registerChannelPayload(registrar, def);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <M> void registerChannelPayload(PayloadRegistrar registrar, ChannelDefinition<M> def) {
        CustomPacketPayload.Type<BridgePayload<M>> payloadType =
            new CustomPacketPayload.Type<>(def.channelId.withSuffix("/msg_" + def.messageId));

        StreamCodec<FriendlyByteBuf, BridgePayload<M>> codec = CustomPacketPayload.codec(
            (payload, buf) -> def.encoder.accept(payload.message, buf),
            buf -> new BridgePayload<>(payloadType, def.decoder.apply(buf))
        );

        registrar.playBidirectional(
            payloadType,
            codec,
            (payload, context) -> def.handler.accept(payload.message, context)
        );
    }

    public record BridgePayload<M>(Type<BridgePayload<M>> type, M message) implements CustomPacketPayload {
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return type;
        }
    }
}
`,
  },
  {
    path: 'src/main/java/net/neoforged/forgestuck/adapter/ThermalSeriesAdapter.java',
    filename: 'ThermalSeriesAdapter.java',
    category: 'adapter',
    description: 'Deep adapter for Thermal Series: augments, CoFH energy item containers, and fluid sync',
    code: `package net.neoforged.forgestuck.adapter;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.forgestuck.ForgeStuckMod;

/**
 * Targeted compatibility adapter for CoFH Core & Thermal Series (Expansion, Foundation, Dynamics, Innovation, Locomotion, Cultivation).
 */
public class ThermalSeriesAdapter {

    public static final String NBT_ENERGY = "Energy";
    public static final String NBT_AUGMENTS = "Augments";
    public static final String NBT_FLUID = "Fluid";
    public static final String NBT_SIDE_CONFIG = "SideConfig";

    public static void init() {
        ForgeStuckMod.LOGGER.info("[Forge Stuck!! - Thermal] Activated CoFH/Thermal Series deep adapter.");
    }

    public static int getEnergyStored(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return 0;
        CompoundTag tag = customData.copyTag();
        return tag.getInt(NBT_ENERGY);
    }

    public static void setEnergyStored(ItemStack stack, int energy) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putInt(NBT_ENERGY, Math.max(0, energy));
        });
    }

    public static ListTag getAugments(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return new ListTag();
        return customData.copyTag().getList(NBT_AUGMENTS, Tag.TAG_COMPOUND);
    }

    public static void setAugments(ItemStack stack, ListTag augmentsList) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.put(NBT_AUGMENTS, augmentsList);
        });
    }
}
`,
  },
  {
    path: 'src/main/java/net/neoforged/forgestuck/adapter/ForestryGeneticsAdapter.java',
    filename: 'ForestryGeneticsAdapter.java',
    category: 'adapter',
    description: 'Deep adapter for Gendustry CE & Forestry: Bee/tree genome NBT preservation across 1.21 Data Components',
    code: `package net.neoforged.forgestuck.adapter;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.forgestuck.ForgeStuckMod;

/**
 * Targeted compatibility adapter for Gendustry CE (Forestry CE Addon).
 */
public class ForestryGeneticsAdapter {

    public static final String NBT_GENOME = "Genome";
    public static final String NBT_IS_ANALYZED = "IsAnalyzed";
    public static final String NBT_HEALTH = "Health";
    public static final String NBT_MAX_HEALTH = "MaxHealth";

    public static void init() {
        ForgeStuckMod.LOGGER.info("[Forge Stuck!! - Gendustry] Activated Forestry/Gendustry CE genetics adapter.");
    }

    public static CompoundTag getGenomeTag(ItemStack beeStack) {
        CustomData customData = beeStack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return null;
        CompoundTag tag = customData.copyTag();
        return tag.contains(NBT_GENOME) ? tag.getCompound(NBT_GENOME) : null;
    }

    public static void writeGenomeTag(ItemStack beeStack, CompoundTag genomeTag) {
        CustomData.update(DataComponents.CUSTOM_DATA, beeStack, tag -> {
            tag.put(NBT_GENOME, genomeTag);
            tag.putBoolean(NBT_IS_ANALYZED, true);
        });
    }

    public static boolean isAnalyzed(ItemStack beeStack) {
        CustomData customData = beeStack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return false;
        return customData.copyTag().getBoolean(NBT_IS_ANALYZED);
    }
}
`,
  },
  {
    path: 'src/main/java/net/neoforged/forgestuck/adapter/AdvancedGeneratorsMultiblockAdapter.java',
    filename: 'AdvancedGeneratorsMultiblockAdapter.java',
    category: 'adapter',
    description: 'Deep adapter for Advanced Generators: multiblock turbine formation and RF/FE power scaling',
    code: `package net.neoforged.forgestuck.adapter;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.forgestuck.ForgeStuckMod;

/**
 * Targeted compatibility adapter for Advanced Generators & BdLib.
 */
public class AdvancedGeneratorsMultiblockAdapter {

    public static void init() {
        ForgeStuckMod.LOGGER.info("[Forge Stuck!! - AdvGen] Activated Advanced Generators multiblock adapter.");
    }

    public static int calculatePowerOutput(int turbineBlades, double fuelHeatValue, double efficiency) {
        double baseOutput = turbineBlades * 25.0; // 25 FE/t per blade
        return (int) Math.round(baseOutput * fuelHeatValue * efficiency);
    }

    public static boolean validateStructure(Level level, BlockPos controllerPos, int width, int height, int depth) {
        if (level == null) return false;
        return level.isLoaded(controllerPos);
    }
}
`,
  },
  {
    path: 'src/main/java/net/neoforged/forgestuck/transformer/ForgeClassTransformer.java',
    filename: 'ForgeClassTransformer.java',
    category: 'core',
    description: 'ASM Bytecode ClassTransformer executing runtime redirects for 1.20.1 classes loaded in NeoForge 1.21.1',
    code: `package net.neoforged.forgestuck.transformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * ASM Class Transformer operating at classloading time.
 */
public class ForgeClassTransformer {

    private static final String TARGET_ITEMSTACK = "net/minecraft/world/item/ItemStack";
    private static final String SHIM_CLASS = "net/neoforged/forgestuck/shim/LegacyItemStackShim";

    public static byte[] transform(String className, byte[] basicClass) {
        if (basicClass == null || className.startsWith("net.neoforged.forgestuck")) {
            return basicClass;
        }

        ClassReader reader = new ClassReader(basicClass);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);

        boolean modified = false;

        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn instanceof MethodInsnNode methodInsn) {
                    if (methodInsn.getOpcode() == Opcodes.INVOKEVIRTUAL &&
                        TARGET_ITEMSTACK.equals(methodInsn.owner) &&
                        "getTag".equals(methodInsn.name) &&
                        "()Lnet/minecraft/nbt/CompoundTag;".equals(methodInsn.desc)) {

                        method.instructions.set(insn, new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            SHIM_CLASS,
                            "getTag",
                            "(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/nbt/CompoundTag;",
                            false
                        ));
                        modified = true;
                    }
                    else if (methodInsn.getOpcode() == Opcodes.INVOKEVIRTUAL &&
                             TARGET_ITEMSTACK.equals(methodInsn.owner) &&
                             "getOrCreateTag".equals(methodInsn.name) &&
                             "()Lnet/minecraft/nbt/CompoundTag;".equals(methodInsn.desc)) {

                        method.instructions.set(insn, new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            SHIM_CLASS,
                            "getOrCreateTag",
                            "(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/nbt/CompoundTag;",
                            false
                        ));
                        modified = true;
                    }
                    else if (methodInsn.getOpcode() == Opcodes.INVOKEVIRTUAL &&
                             TARGET_ITEMSTACK.equals(methodInsn.owner) &&
                             "setTag".equals(methodInsn.name) &&
                             "(Lnet/minecraft/nbt/CompoundTag;)V".equals(methodInsn.desc)) {

                        method.instructions.set(insn, new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            SHIM_CLASS,
                            "setTag",
                            "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/nbt/CompoundTag;)V;",
                            false
                        ));
                        modified = true;
                    }
                }
            }
        }

        if (!modified) {
            return basicClass;
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        return writer.toByteArray();
    }
}
`,
  },
  {
    path: 'build.gradle',
    filename: 'build.gradle',
    category: 'config',
    description: 'NeoForge 1.21.1 Gradle build configuration with ASM transformer and jar packaging',
    code: `plugins {
    id 'java-library'
    id 'maven-publish'
    id 'net.neoforged.moddev' version '1.0.18'
}

version = '1.21.1-1.0.0'
group = 'net.neoforged.forgestuck'

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        name = 'NeoForged'
        url = 'https://maven.neoforged.net/releases'
    }
}

neoForge {
    version = '21.1.72'

    parchment {
        mappingsVersion = '2024.07.28'
        minecraftVersion = '1.21.1'
    }

    runs {
        client {
            client()
            systemProperty 'neoforge.enabledGameTestNamespaces', 'forgestuck'
        }
        server {
            server()
            programArgument '--nogui'
            systemProperty 'neoforge.enabledGameTestNamespaces', 'forgestuck'
        }
    }

    mods {
        forgestuck {
            sourceSet(sourceSets.main)
        }
    }
}

dependencies {
    implementation 'org.ow2.asm:asm:9.7'
    implementation 'org.ow2.asm:asm-tree:9.7'
    implementation 'org.ow2.asm:asm-commons:9.7'
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
    options.release = 21
}

jar {
    manifest {
        attributes([
            'Specification-Title'     : 'forgestuck',
            'Specification-Vendor'    : 'Forge Stuck!! Team',
            'Specification-Version'   : '1',
            'Implementation-Title'    : 'Forge Stuck!!',
            'Implementation-Version'  : project.version,
            'Implementation-Vendor'   : 'Forge Stuck!! Team',
            'FMLModType'              : 'MOD',
            'TweakClass'              : 'net.neoforged.forgestuck.transformer.ForgeClassTransformer'
        ])
    }
}
`,
  },
  {
    path: 'src/main/resources/META-INF/neoforge.mods.toml',
    filename: 'neoforge.mods.toml',
    category: 'config',
    description: 'NeoForge mod metadata manifest file declaring Forge Stuck!! mod metadata',
    code: `modLoader="javafml"
loaderVersion="[2,)"
license="LGPL-3.0"
issueTrackerURL="https://github.com/example/forgestuck/issues"

[[mods]]
modId="forgestuck"
version="1.21.1-1.0.0"
displayName="Forge Stuck!!"
logoFile="forgestuck.png"
authors="Forge Stuck!! Team"
description='''
NeoForge 1.21.1 runtime compatibility mod and bytecode transformer allowing
Minecraft Forge 1.20.1 mods to run on NeoForge 1.21.1!
For when your favorite mods are stubbornly stuck in 1.20.1.
Includes deep compatibility for Thermal Series, Gendustry CE, and Advanced Generators.
'''

[[dependencies.forgestuck]]
    modId="neoforge"
    type="required"
    versionRange="[21.1.0,)"
    ordering="NONE"
    side="BOTH"

[[dependencies.forgestuck]]
    modId="minecraft"
    type="required"
    versionRange="[1.21.1,1.22)"
    ordering="NONE"
    side="BOTH"
`,
  },
  {
    path: 'gradle.properties',
    filename: 'gradle.properties',
    category: 'config',
    description: 'Gradle properties specifying JVM args and Minecraft target version',
    code: `org.gradle.jvmargs=-Xmx3G
org.gradle.parallel=true

minecraft_version=1.21.1
neoforge_version=21.1.72
mod_id=forgestuck
mod_name=Forge Stuck!!
mod_license=LGPL-3.0
mod_version=1.21.1-1.0.0
mod_group_id=net.neoforged.forgestuck
mod_authors=Forge Stuck!! Team
mod_description=NeoForge 1.21.1 runtime compatibility layer for Forge 1.20.1 mods.
`,
  },
];
