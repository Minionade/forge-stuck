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
        Path cacheDir = gameDir.resolve(".forgestuck").resolve("cache");
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
                // For provider/platform jars: register the ORIGINAL jar, untransformed.
                if (shouldSkipTransformation(lower)) {
                    LOGGER.info("{} bypass transform (provider/platform jar): {}", TAG, name);

                    ModFileDiscoveryAttributes attributes = ModFileDiscoveryAttributes.DEFAULT.withLocator(this);
                    var modFile = pipeline.addPath(jar, attributes, IncompatibleFileReporting.ERROR);

                    if (modFile.isPresent()) {
                        context.addLocated(jar);
                        IModFile file = modFile.get();
                        LOGGER.info("{} registered UNTRANSFORMED mod id(s) {} from {}", TAG,
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
                    LOGGER.info("{} registered mod id(s) {} from {}", TAG,
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

    private static boolean isScannable(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".jar") && !n.startsWith("forgestuck") && Files.isRegularFile(p);
    }

    @Override
    public String toString() {
        return "{Forge Stuck!! legacy jar locator}";
    }
}