package net.neoforged.forgestuck.locator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import net.neoforged.forgestuck.transformer.jar.JarTransformer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ForgeStuckCandidateLocator implements IModFileCandidateLocator {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgestuck");
    private static final String TAG = "[Forge Stuck!!]";

    /**
     * Transformed jars live here, directly under {@code mods/}. NeoForge's mods-folder locator lists {@code mods/}
     * non-recursively and only accepts regular {@code *.jar} files, so jars in this subfolder are loaded only through
     * this locator.
     */
    public static final String CACHE_DIR_NAME = ".forgestuck";
    private static final String OLD_CACHE_DIR = ".forgestuck/cache";

    public static Path cacheDir(Path gameDir) {
        return gameDir.resolve("mods").resolve(CACHE_DIR_NAME);
    }

    private static void warnAboutLegacyCache(Path gameDir) {
        Path old = gameDir.resolve(".forgestuck").resolve("cache");
        if (Files.isDirectory(old)) {
            LOGGER.info("{} old transformed-jar cache {}/ ({}) is no longer used and is left in place; delete it manually "
                    + "if you want the disk space back", TAG, OLD_CACHE_DIR, old);
        }
    }

    // Provider/platform jars that should never be transformed by ForgeStuck.
    // Keep this list narrow and explicit.
    private static final Set<String> TRANSFORM_EXCLUDE_SUBSTRINGS = Set.of(
            "scalablecatsforce",
            "kotlinforforge",
            "forgifiedfabricapi",
            "connector",
            "sinytra"
    );

    @Override
    public int getPriority() {
        return HIGHEST_SYSTEM_PRIORITY;
    }

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        Path gameDir = FMLPaths.GAMEDIR.get();
        Path modsDir = gameDir.resolve("mods");
        Path cacheDir = cacheDir(gameDir);
        warnAboutLegacyCache(gameDir);
        if (!Files.isDirectory(modsDir)) return;

        List<Path> jars;
        try (Stream<Path> files = Files.list(modsDir)) {
            jars = files
                    .filter(ForgeStuckCandidateLocator::isScannable)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
        } catch (IOException e) {
            LOGGER.error("{} could not list {}", TAG, modsDir, e);
            return;
        }

        JarTransformer transformer = JarTransformer.withDefaultSteps();

        for (Path jar : jars) {
            String name = jar.getFileName().toString();
            String lower = name.toLowerCase(Locale.ROOT);

            try {
                if (LegacyJarDetector.isNativeNeoForge(jar)) {
                    LOGGER.info("{} bypass transform (native NeoForge descriptor; runtime validation left to NeoForge): {}",
                            TAG, name);
                    continue;
                }

                // For provider/platform jars: register the ORIGINAL jar, untransformed.
                if (shouldSkipTransformation(lower)) {
                    LOGGER.info("{} bypass transform (provider/platform jar): {}", TAG, name);

                    ModFileDiscoveryAttributes attributes = ModFileDiscoveryAttributes.DEFAULT.withLocator(this);
                    var modFile = pipeline.addPath(jar, attributes, IncompatibleFileReporting.ERROR);

                    if (modFile.isPresent()) {
                        context.addLocated(jar);
                        IModFile file = modFile.get();
                        LOGGER.info("{} discovered UNTRANSFORMED provider/platform mod id(s) {} from {} (discovery only; "
                                + "construction is decided later by FML)", TAG,
                                file.getModInfos().stream().map(i -> i.getModId()).toList(), name);
                    } else {
                        LOGGER.warn("{} could not register untransformed provider/platform jar {} (leaving for other locators)", TAG, name);
                    }
                    continue;
                }

                if (!LegacyJarDetector.isLegacyCandidate(jar)) {
                    continue;
                }

                LOGGER.info("{} candidate found: {}", TAG, name);

                JarTransformer.Result result = transformer.transformCached(jar, cacheDir);
                ModFileDiscoveryAttributes attributes = ModFileDiscoveryAttributes.DEFAULT.withLocator(this);
                var modFile = pipeline.addPath(result.output(), attributes, IncompatibleFileReporting.ERROR);

                if (modFile.isPresent()) {
                    context.addLocated(jar);
                    IModFile file = modFile.get();
                    LOGGER.info("{} discovered transformed mod id(s) {} from {} (discovery only; not evidence the mod is "
                            + "constructed or registers content)", TAG,
                            file.getModInfos().stream().map(i -> i.getModId()).toList(), name);
                } else {
                    LOGGER.info("{} skipped {}: loader did not accept {}", TAG, name, result.output());
                }
            } catch (Exception | LinkageError t) {
                LOGGER.error("{} skipped {}: {}", TAG, name, t.toString(), t);
            }
        }
    }

    private static boolean shouldSkipTransformation(String lowerJarName) {
        return TRANSFORM_EXCLUDE_SUBSTRINGS.stream().anyMatch(lowerJarName::contains);
    }

    static boolean isScannable(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".jar") && !n.startsWith("forgestuck") && Files.isRegularFile(p);
    }

    @Override
    public String toString() {
        return "{Forge Stuck!! legacy jar locator}";
    }
}