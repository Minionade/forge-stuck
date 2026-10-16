package net.neoforged.forgestuck.locator;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.moddiscovery.ModFileDiscoveryAttributes;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.forgestuck.transformer.jar.JarTransformer;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Connector-style candidate locator: scans run/mods for legacy Forge 1.20.1 jars, transforms them into
 * loader-compatible jars cached in run/.forgestuck/cache/&lt;sha256+version&gt;.jar and hands the transformed
 * jar to the loader's discovery pipeline, which builds the real ModFile. Full remapping is out of scope.
 */
public class ForgeStuckCandidateLocator implements IModFileCandidateLocator {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final JarTransformer transformer = new JarTransformer();

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        Path modsDir = FMLPaths.MODSDIR.get();
        for (Path cached : scanMods(modsDir, modsDir.resolveSibling(".forgestuck").resolve("cache"))) {
            try {
                pipeline.addPath(cached, ModFileDiscoveryAttributes.DEFAULT.withLocator(this), IncompatibleFileReporting.WARN_ALWAYS);
                LOGGER.info("[Forge Stuck!!] Registered transformed jar {}", cached.getFileName());
            } catch (Exception e) {
                LOGGER.error("[Forge Stuck!!] Failed to register {}: {}", cached.getFileName(), e.toString());
            }
        }
    }

    @Override
    public int getPriority() {
        return LOWEST_SYSTEM_PRIORITY;
    }

    /** Transforms every legacy jar in {@code modsDir} into {@code cacheDir}; returns the cached jar paths. */
    public List<Path> scanMods(Path modsDir, Path cacheDir) {
        List<Path> result = new ArrayList<>();
        if (!Files.isDirectory(modsDir)) return result;
        try {
            Files.createDirectories(cacheDir);
        } catch (IOException e) {
            LOGGER.error("[Forge Stuck!!] Cannot create cache dir {}: {}", cacheDir, e.toString());
            return result;
        }
        try (Stream<Path> files = Files.list(modsDir)) {
            for (Path jar : (Iterable<Path>) files.sorted()::iterator) {
                try {
                    if (!jar.getFileName().toString().toLowerCase().endsWith(".jar")
                        || jar.getFileName().toString().startsWith("forgestuck")
                        || !isLegacyForgeJar(jar)) continue;
                    LOGGER.info("[Forge Stuck!!] Candidate found: {}", jar.getFileName());
                    Path cached = cacheDir.resolve(cacheKey(jar) + ".jar");
                    if (Files.isRegularFile(cached)) {
                        LOGGER.info("[Forge Stuck!!] Cache hit: {}", cached.getFileName());
                    } else {
                        LOGGER.info("[Forge Stuck!!] Cache miss for {}, transforming", jar.getFileName());
                        transformer.transformTo(jar, cached);
                        LOGGER.info("[Forge Stuck!!] Transformed to {}", cached);
                    }
                    result.add(cached);
                } catch (Exception e) {
                    LOGGER.error("[Forge Stuck!!] Skipping {}: {}", jar.getFileName(), e.toString());
                }
            }
        } catch (IOException e) {
            LOGGER.error("[Forge Stuck!!] Failed scanning {}: {}", modsDir, e.toString());
        }
        return result;
    }

    /** True if the jar has META-INF/mods.toml and no META-INF/neoforge.mods.toml. */
    public static boolean isLegacyForgeJar(Path jar) throws IOException {
        try (JarFile jf = new JarFile(jar.toFile())) {
            return jf.getEntry("META-INF/mods.toml") != null && jf.getEntry("META-INF/neoforge.mods.toml") == null;
        }
    }

    /** sha256(file bytes) + transformer version. */
    public static String cacheKey(Path jar) throws IOException {
        try (InputStream in = Files.newInputStream(jar)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest()) + "-v" + JarTransformer.TRANSFORMER_VERSION;
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String toString() {
        return "ForgeStuck legacy Forge 1.20.1 candidate locator";
    }
}
