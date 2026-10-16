package net.neoforged.forgestuck.transformer.jar;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rewrites a legacy Forge 1.20.1 jar into a jar NeoForge can discover: signatures are stripped, a valid
 * {@code META-INF/neoforge.mods.toml} is generated from {@code META-INF/mods.toml} and class entries are passed
 * through a list of {@link ClassTransformStep}s. The source jar is never modified.
 *
 * NOTE: this does not remap Forge 1.20.1 APIs to NeoForge 1.21.1 APIs.
 */
public class JarTransformer {
    /** Bump whenever the output of the transformer changes so cached jars are invalidated. */
    public static final String TRANSFORMER_VERSION = "1";
    public static final String DEFAULT_VERSION = "0.0.0-forgestuck";
    public static final String TARGET_MINECRAFT_RANGE = "[1.21.1,)";

    private static final Logger LOGGER = LoggerFactory.getLogger("forgestuck");
    private static final String TAG = "[Forge Stuck!!]";
    private static final Pattern SIGNATURE = Pattern.compile("META-INF/[^/]+\\.(SF|RSA|DSA)", Pattern.CASE_INSENSITIVE);

    /** Result of a cached transformation. */
    public record Result(Path output, boolean cacheHit) {}

    private final List<ClassTransformStep> steps;

    public JarTransformer(List<ClassTransformStep> steps) {
        this.steps = List.copyOf(steps);
    }

    /** Transformer with the default step list. */
    public static JarTransformer withDefaultSteps() {
        return new JarTransformer(List.of(new ForgeClassTransformStep()));
    }

    /** Returns the cached output for this source jar, creating it when missing. */
    public Result transformCached(Path source, Path cacheDir) throws IOException {
        Files.createDirectories(cacheDir);
        Path target = cacheDir.resolve(sha256(source) + "-t" + TRANSFORMER_VERSION + ".jar");
        if (Files.isRegularFile(target) && Files.size(target) > 0) {
            LOGGER.info("{} cache hit for {} -> {}", TAG, source.getFileName(), target);
            return new Result(target, true);
        }
        LOGGER.info("{} cache miss for {}", TAG, source.getFileName());
        Path tmp = Files.createTempFile(cacheDir, "forgestuck-", ".tmp");
        try {
            transform(source, tmp);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
        LOGGER.info("{} transformed jar created: {}", TAG, target);
        return new Result(target, false);
    }

    /** Writes the transformed jar to {@code output}; {@code source} is only read. */
    public void transform(Path source, Path output) throws IOException {
        if (source.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize())) {
            throw new IOException("Refusing to overwrite the source jar " + source);
        }
        try (JarFile in = new JarFile(source.toFile());
                OutputStream fos = Files.newOutputStream(output);
                JarOutputStream out = new JarOutputStream(fos)) {
            String modsToml = null;
            Enumeration<JarEntry> entries = in.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory()) {
                    putEntry(out, entry, name, new byte[0]);
                    continue;
                }
                if (SIGNATURE.matcher(name).matches()) {
                    continue;
                }
                byte[] data;
                try (InputStream is = in.getInputStream(entry)) {
                    data = is.readAllBytes();
                }
                if (name.equals("META-INF/mods.toml")) {
                    modsToml = new String(data, StandardCharsets.UTF_8);
                    continue;
                }
                if (name.equals("META-INF/neoforge.mods.toml")) {
                    continue;
                }
                if (name.equals(JarFile.MANIFEST_NAME)) {
                    data = stripManifestDigests(data);
                } else if (name.endsWith(".class")) {
                    data = applySteps(name, data);
                }
                putEntry(out, entry, name, data);
            }
            if (modsToml == null) {
                throw new IOException("No META-INF/mods.toml in " + source.getFileName());
            }
            String version = implementationVersion(in);
            String converted = convertModsToml(modsToml, version, source.getFileName().toString());
            JarEntry generated = new JarEntry("META-INF/neoforge.mods.toml");
            out.putNextEntry(generated);
            out.write(converted.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
    }

    private byte[] applySteps(String entryName, byte[] data) {
        String className = entryName.substring(0, entryName.length() - ".class".length()).replace('/', '.');
        byte[] current = data;
        for (ClassTransformStep step : steps) {
            try {
                byte[] result = step.apply(className, current);
                if (result != null) current = result;
            } catch (Exception | LinkageError e) {
                LOGGER.warn("{} class step {} failed on {}, leaving it unchanged: {}", TAG,
                        step.getClass().getSimpleName(), className, e.toString());
            }
        }
        return current;
    }

    private static void putEntry(JarOutputStream out, JarEntry original, String name, byte[] data) throws IOException {
        JarEntry copy = new JarEntry(name);
        if (original.getTime() != -1) copy.setTime(original.getTime());
        out.putNextEntry(copy);
        out.write(data);
        out.closeEntry();
    }

    private static byte[] stripManifestDigests(byte[] data) throws IOException {
        Manifest manifest = new Manifest(new java.io.ByteArrayInputStream(data));
        manifest.getEntries().clear();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        manifest.write(bos);
        return bos.toByteArray();
    }

    private static String implementationVersion(JarFile jar) throws IOException {
        Manifest manifest = jar.getManifest();
        if (manifest == null) return DEFAULT_VERSION;
        String v = manifest.getMainAttributes().getValue("Implementation-Version");
        return v == null || v.isBlank() ? DEFAULT_VERSION : v.trim();
    }

    /** Converts Forge {@code mods.toml} text into a valid {@code neoforge.mods.toml} text. */
    public String convertModsToml(String forgeToml, String jarVersion, String jarName) {
        Config source = new TomlParser().parse(forgeToml);
        String loader = source.getOrElse("modLoader", "javafml");
        if (!"javafml".equals(loader)) {
            onUnsupportedModLoader(loader, jarName);
        }

        Config out = Config.inMemory();
        out.set("modLoader", "javafml");
        out.set("loaderVersion", "[1,)");
        out.set("license", source.getOrElse("license", "All Rights Reserved"));
        copyIfPresent(source, out, "issueTrackerURL");

        List<Config> mods = new ArrayList<>();
        Config deps = Config.inMemory();
        List<UnmodifiableConfig> sourceMods = source.getOrElse("mods", List.<UnmodifiableConfig>of());
        for (UnmodifiableConfig sm : sourceMods) {
            String modId = sm.get("modId");
            if (modId == null) continue;
            Config mod = Config.inMemory();
            mod.set("modId", modId);
            String version = sm.getOrElse("version", jarVersion);
            mod.set("version", version.replace("${file.jarVersion}", jarVersion));
            mod.set("displayName", sm.getOrElse("displayName", modId));
            mod.set("description", sm.getOrElse("description", "Legacy Forge mod adapted by Forge Stuck!!"));
            String authors = sm.get("authors");
            if (authors == null) authors = source.get("authors");
            if (authors != null) mod.set("authors", authors);
            mods.add(mod);

            List<UnmodifiableConfig> sourceDeps = dependenciesOf(source, modId);
            List<Config> converted = new ArrayList<>();
            for (UnmodifiableConfig dep : sourceDeps) {
                Config c = convertDependency(dep);
                if (c != null) converted.add(c);
            }
            if (!converted.isEmpty()) deps.set(List.of(modId), converted);
        }
        out.set("mods", mods);
        if (!deps.isEmpty()) out.set("dependencies", deps);
        return TomlFormat.instance().createWriter().writeToString(out);
    }

    private static List<UnmodifiableConfig> dependenciesOf(Config source, String modId) {
        UnmodifiableConfig all = source.get("dependencies");
        if (all == null) return List.of();
        List<UnmodifiableConfig> list = all.get(List.of(modId));
        return list == null ? List.of() : list;
    }

    private static Config convertDependency(UnmodifiableConfig dep) {
        String depId = dep.get("modId");
        if (depId == null || depId.equalsIgnoreCase("forge")) return null;
        Config c = Config.inMemory();
        c.set("modId", depId);
        String type = dep.get("type");
        if (type == null) {
            Object mandatory = dep.get("mandatory");
            type = mandatory instanceof Boolean b && !b ? "optional" : "required";
        }
        c.set("type", type.toLowerCase(Locale.ROOT));
        String range = dep.getOrElse("versionRange", "");
        if (depId.equals("minecraft")) range = TARGET_MINECRAFT_RANGE;
        if (!range.isEmpty()) c.set("versionRange", range);
        c.set("ordering", dep.getOrElse("ordering", "NONE"));
        c.set("side", dep.getOrElse("side", "BOTH"));
        return c;
    }

    private static void copyIfPresent(Config from, Config to, String key) {
        Object v = from.get(key);
        if (v != null) to.set(key, v);
    }

    /**
     * Extension point for mods whose language provider is not available on this runtime (e.g. {@code kotori_scala}).
     * The jar is still produced so the failure is visible and diagnosable.
     */
    protected void onUnsupportedModLoader(String modLoader, String jarName) {
        LOGGER.warn("{} {} uses modLoader=\"{}\" whose language provider is unavailable on this runtime; "
                + "the mod will likely fail to load", TAG, jarName, modLoader);
    }

    public static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
