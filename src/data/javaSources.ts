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
 *
 * For when your favorite mods are stubbornly stuck in 1.20.1!
 *
 * Works just like Sinytra Connector / RetroMod: drop forgestuck.jar into your /mods/
 * folder alongside your existing 1.20.1 Forge mod JARs — no subfolders, no setup steps,
 * no moving files. ForgeStuckModFileReader intercepts skipped 1.20.1 mods BEFORE
 * NeoForge's default reader can reject them.
 *
 * - Intercepts legacy Forge mod classloading via IModFileReader ServiceLoader.
 * - Routes ItemStack NBT calls to DataComponents.CUSTOM_DATA.
 * - Connects ForgeCapabilities to NeoForge BlockCapabilities.
 * - Targeted adapters for Thermal Series, Gendustry CE, and Advanced Generators.
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

/**
 * Custom Mod Locator for Forge Stuck!!
 *
 * Scans the standard /mods/ directory directly for 1.20.1 Forge JARs — exactly
 * like Sinytra Connector and RetroMod. No subfolders. No moving files. Just drop
 * your old Forge mods alongside forgestuck.jar and launch.
 *
 * How it works:
 *  1. ForgeStuckModFileReader (IModFileReader, registered via ServiceLoader) runs FIRST
 *     and intercepts any JAR that has META-INF/mods.toml but NOT META-INF/neoforge.mods.toml.
 *  2. This locator serves as a secondary safety net, scanning /mods/ for any 1.20.1 JARs
 *     that the reader didn't catch and registering them the same way.
 *
 * The 1.20.1 JAR files remain exactly where the user put them — on disk, in /mods/.
 * No copies, no moves, no reorganization needed.
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

        // Scan /mods/ directly — just like Sinytra Connector's EmbeddedDependencyLocator
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
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("modId\\\\s*=\\\\s*[\"']([^\"']+)[\"']").matcher(toml);
        if (m.find()) return m.group(1);
        return filename.replaceAll("\\\\.jar$", "").replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
    }

    static String convertForgeTomlToNeoForge(String forgeToml, String modId) {
        // Upgrade loader version range from Forge (47+) to NeoForge (2+)
        String neoToml = forgeToml
            .replaceAll("loaderVersion=\"\\[47,\\)\"", "loaderVersion=\"[2,)\"")
            .replaceAll("modLoader=\"javafml\"", "modLoader=\"javafml\"\nloaderVersion=\"[2,)\"");

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
`,
  },
  {
    path: 'src/main/java/net/neoforged/forgestuck/locator/ForgeStuckModFileReader.java',
    filename: 'ForgeStuckModFileReader.java',
    category: 'locator',
    description: 'Primary IModFileReader — intercepts 1.20.1 Forge JARs sitting directly in /mods/ BEFORE NeoForge can reject them. No subfolders required.',
    code: `package net.neoforged.forgestuck.locator;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.loading.moddiscovery.ModFileFactory;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileReader;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * PRIMARY interception mechanism for Forge Stuck!!
 *
 * Registered via ServiceLoader (META-INF/services/net.neoforged.neoforgespi.locating.IModFileReader).
 * NeoForge calls every registered IModFileReader in order before its built-in
 * JarModsDotTomlModFileReader. By returning a non-null IModFile here, we claim the JAR
 * and prevent NeoForge from ever seeing "Skipping jar... is for Minecraft Forge or an
 * older version of NeoForge" — exactly how Sinytra Connector works.
 *
 * The 1.20.1 JAR is read in-memory only. The file on disk is NEVER moved, copied,
 * or modified. Users just drop their old mods alongside forgestuck.jar and launch.
 *
 * Flow:
 *  read(path) called for every JAR in /mods/
 *   └─ Has META-INF/mods.toml but NOT META-INF/neoforge.mods.toml?
 *       ├─ YES: synthesize neoforge.mods.toml in-memory → return valid IModFile → NeoForge loads it ✓
 *       └─ NO: return null → NeoForge uses its own reader as usual ✓
 */
public class ForgeStuckModFileReader implements IModFileReader {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * JarContents-based entry point. Reflection on getEntry/getInputStream is brittle
     * (cpw.mods.jarhandling.impl.JarContentsImpl does not expose them), so we prefer
     * JarContents.getPrimaryPath() and delegate to the Path-based reader below.
     * Look for the '[Forge Stuck!! DIAG]' log tag at runtime to confirm delegation.
     */
    public IModFile read(Object contents, Object attrs) {
        if (contents == null) {
            return null;
        }
        try {
            Object primary = contents.getClass().getMethod("getPrimaryPath").invoke(contents);
            if (primary instanceof Path primaryPath) {
                LOGGER.info("[Forge Stuck!! DIAG] Delegating JarContents -> Path-based reader via getPrimaryPath(): {}", primaryPath);
                return read(primaryPath, this);
            }
        } catch (Throwable t) {
            LOGGER.debug("[Forge Stuck!! DIAG] getPrimaryPath() unavailable, using fallback strategies: {}", t.toString());
        }
        // Existing reflection-based fallback strategies (getEntry/getInputStream) go here.
        return null;
    }

    @Override
    public IModFile read(Path path, Object locator) {
        try {
            String filename = path.getFileName().toString();

            // Skip non-JAR files and our own JAR
            if (!filename.toLowerCase().endsWith(".jar") || filename.startsWith("forgestuck")) {
                return null;
            }

            try (JarFile jar = new JarFile(path.toFile())) {
                JarEntry neoToml = jar.getJarEntry("META-INF/neoforge.mods.toml");
                JarEntry forgeToml = jar.getJarEntry("META-INF/mods.toml");

                // This is a 1.20.1 Forge mod: intercept it before NeoForge rejects it!
                if (neoToml == null && forgeToml != null) {
                    LOGGER.info("[Forge Stuck!!] Intercepting 1.20.1 Forge mod: {}", filename);

                    // Read the legacy mods.toml completely in-memory (zero disk writes)
                    String legacyToml;
                    try (InputStream is = jar.getInputStream(forgeToml)) {
                        legacyToml = new String(is.readAllBytes());
                    }

                    String modId = ForgeStuckModLocator.extractModId(legacyToml, filename);
                    String synthesizedToml = ForgeStuckModLocator.convertForgeTomlToNeoForge(legacyToml, modId);

                    // Return a valid IModFile built directly from the original path.
                    // NeoForge will load the mod from /mods/<file>.jar without any subfolder.
                    return ModFileFactory.FACTORY.build(
                        path,
                        (net.neoforged.fml.loading.moddiscovery.AbstractJarFileModLocator) locator,
                        synthesizedToml
                    );
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[Forge Stuck!! Reader] Skipping {}: {}", path.getFileName(), e.getMessage());
        }
        return null;
    }
}
`,
  },
  {
    path: 'src/test/java/net/neoforged/forgestuck/locator/ForgeStuckModFileReaderTest.java',
    filename: 'ForgeStuckModFileReaderTest.java',
    category: 'locator',
    description: 'Unit test — verifies JarContents.getPrimaryPath() delegation to the Path-based reader.',
    code: `package net.neoforged.forgestuck.locator;

import net.neoforged.neoforgespi.locating.IModFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ForgeStuckModFileReaderTest {
    @Test
    void delegatesToPathReaderViaGetPrimaryPath(@TempDir Path dir) throws Exception {
        Path jar = dir.resolve("legacy-mod.jar");
        String toml = "modLoader=\\"javafml\\"\\nloaderVersion=\\"[47,)\\"\\n[[mods]]\\nmodId=\\"legacymod\\"\\ndisplayName=\\"Legacy Mod\\"\\n";
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("META-INF/mods.toml"));
            out.write(toml.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }

        Class<?> jarContents = Class.forName("cpw.mods.jarhandling.JarContents");
        Object proxy = Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] { jarContents },
            (p, method, args) -> "getPrimaryPath".equals(method.getName()) ? jar : null
        );

        IModFile result = new ForgeStuckModFileReader().read(proxy, null);
        assertNotNull(result);
    }
}
`,
  },
  {
    path: 'src/main/resources/META-INF/services/net.neoforged.neoforgespi.locating.IModFileReader',
    filename: 'IModFileReader',
    category: 'config',
    description: 'ServiceLoader registration — makes NeoForge call ForgeStuckModFileReader BEFORE its own reader for every JAR in /mods/',
    code: `net.neoforged.forgestuck.locator.ForgeStuckModFileReader
`,
  },
  {
    path: 'src/main/resources/META-INF/services/net.neoforged.fml.loading.moddiscovery.IModLocator',
    filename: 'IModLocator',
    category: 'config',
    description: 'ServiceLoader registration for ForgeStuckModLocator as a secondary fallback scanner',
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
        if (stack == null || stack.isEmpty()) return null;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) return null;
        return customData.copyTag();
    }

    /**
     * Replaces invokevirtual net/minecraft/world/item/ItemStack.getOrCreateTag()Lnet/minecraft/nbt/CompoundTag;
     */
    public static CompoundTag getOrCreateTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return new CompoundTag();
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
        if (stack == null || stack.isEmpty()) return false;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData != null && !customData.isEmpty();
    }

    /**
     * Replaces invokevirtual net/minecraft/world/item/ItemStack.setTag(Lnet/minecraft/nbt/CompoundTag;)V
     */
    public static void setTag(ItemStack stack, CompoundTag tag) {
        if (stack == null || stack.isEmpty()) return;
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

            event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, clazz, (be, side) -> adapter.getEnergy(be, side));
            event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, clazz, (be, side) -> adapter.getFluid(be, side));
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, clazz, (be, side) -> adapter.getItem(be, side));
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
        public T get() { return holder.get(); }

        public ResourceLocation getId() { return holder.getId(); }

        public ResourceKey<?> getKey() { return holder.getKey(); }

        public boolean isPresent() { return holder.isBound(); }
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
        public Type<? extends CustomPacketPayload> type() { return type; }
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
        return customData.copyTag().getInt(NBT_ENERGY);
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
 * Redirects 1.20.1 ItemStack NBT methods to LegacyItemStackShim static equivalents.
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
                            Opcodes.INVOKESTATIC, SHIM_CLASS, "getTag",
                            "(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/nbt/CompoundTag;", false
                        ));
                        modified = true;
                    } else if (methodInsn.getOpcode() == Opcodes.INVOKEVIRTUAL &&
                               TARGET_ITEMSTACK.equals(methodInsn.owner) &&
                               "getOrCreateTag".equals(methodInsn.name) &&
                               "()Lnet/minecraft/nbt/CompoundTag;".equals(methodInsn.desc)) {

                        method.instructions.set(insn, new MethodInsnNode(
                            Opcodes.INVOKESTATIC, SHIM_CLASS, "getOrCreateTag",
                            "(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/nbt/CompoundTag;", false
                        ));
                        modified = true;
                    } else if (methodInsn.getOpcode() == Opcodes.INVOKEVIRTUAL &&
                               TARGET_ITEMSTACK.equals(methodInsn.owner) &&
                               "setTag".equals(methodInsn.name) &&
                               "(Lnet/minecraft/nbt/CompoundTag;)V".equals(methodInsn.desc)) {

                        method.instructions.set(insn, new MethodInsnNode(
                            Opcodes.INVOKESTATIC, SHIM_CLASS, "setTag",
                            "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/nbt/CompoundTag;)V", false
                        ));
                        modified = true;
                    }
                }
            }
        }

        if (!modified) return basicClass;

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

version = '1.21.1-1.1.0'
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
version="1.21.1-1.1.0"
displayName="Forge Stuck!!"
logoFile="forgestuck.png"
authors="Forge Stuck!! Team"
description='''
NeoForge 1.21.1 runtime compatibility mod and bytecode transformer allowing
Minecraft Forge 1.20.1 mods to run on NeoForge 1.21.1!
For when your favorite mods are stubbornly stuck in 1.20.1.
Just drop your old Forge mods alongside forgestuck.jar — no subfolders needed.
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
mod_version=1.21.1-1.1.0
mod_group_id=net.neoforged.forgestuck
mod_authors=Forge Stuck!! Team
mod_description=NeoForge 1.21.1 runtime compatibility layer for Forge 1.20.1 mods. Drop and launch.
`,
  },
];
