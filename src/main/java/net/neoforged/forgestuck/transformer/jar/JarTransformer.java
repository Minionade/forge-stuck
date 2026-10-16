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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;
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
    public static final String TRANSFORMER_VERSION = "5";
    public static final String DEFAULT_VERSION = "0.0.0-forgestuck";
    /** Targeted Thermal 1.20.1 -> NeoForge 1.21.1 compatibility shim: Thermal modules depend on id "thermal". */
    public static final String THERMAL_SOURCE_ID = "thermal_foundation";
    public static final String THERMAL_TARGET_ID = "thermal";
    public static final String TARGET_MINECRAFT_RANGE = "[1.21.1,)";

    private static final Logger LOGGER = LoggerFactory.getLogger("forgestuck");
    private static final String TAG = "[Forge Stuck!!]";
    private static final Pattern SIGNATURE = Pattern.compile("META-INF/[^/]+\\.(SF|RSA|DSA)", Pattern.CASE_INSENSITIVE);

    /**
     * Default for {@link #removeUnknownLoaders}. When enabled, model JSONs naming a custom (non-vanilla,
     * non-NeoForge) loader that has no known mapping get their {@code loader} property removed so they parse as
     * plain vanilla models. This avoids startup failures but loses the custom model's fidelity/functionality.
     */
    public static final boolean REMOVE_UNKNOWN_MODEL_LOADERS = true;

    /** Direct Forge 1.20.1 -> NeoForge 1.21.1 model loader id mappings. */
    public static final Map<String, String> MODEL_LOADER_MAPPINGS = Map.of(
            "forge:bucket", "neoforge:fluid_container",
            "forge:obj", "neoforge:obj",
            "forge:composite", "neoforge:composite",
            "forge:separate_transforms", "neoforge:separate_transforms",
            "forge:empty", "neoforge:empty");

    private static final Pattern MODEL_JSON = Pattern.compile("assets/[^/]+/models/.+\\.json");

    /** Result of a cached transformation. */
    public record Result(Path output, boolean cacheHit) {}

    private final List<ClassTransformStep> steps;
    private final boolean removeUnknownLoaders;
    private final LegacyNameMappings mappings;

    public JarTransformer(List<ClassTransformStep> steps) {
        this(steps, REMOVE_UNKNOWN_MODEL_LOADERS);
    }

    public JarTransformer(List<ClassTransformStep> steps, boolean removeUnknownLoaders) {
        this(steps, removeUnknownLoaders, LegacyNameMappings.defaults());
    }

    public JarTransformer(List<ClassTransformStep> steps, boolean removeUnknownLoaders, LegacyNameMappings mappings) {
        this.steps = List.copyOf(steps);
        this.removeUnknownLoaders = removeUnknownLoaders;
        this.mappings = mappings;
    }

    /** Transformer with the default step list. */
    public static JarTransformer withDefaultSteps() {
        return new JarTransformer(List.of(new ForgeClassTransformStep(), new ResourceLocationClassTransformStep(),
                new SrgMemberRemapStep()));
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
            String jarName = source.getFileName().toString();
            Map<String, String> rewritten = new LinkedHashMap<>();
            TreeSet<String> removed = new TreeSet<>();
            MixinResourceRewriter mixins = new MixinResourceRewriter(mappings, jarName);
            Map<String, JarEntry> pendingMixinEntries = new LinkedHashMap<>();
            Map<String, byte[]> pendingMixinData = new LinkedHashMap<>();
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
                    mixins.inspectClass(name.substring(0, name.length() - ".class".length()).replace('/', '.'), data);
                } else if (MixinResourceRewriter.isCandidate(name)) {
                    pendingMixinEntries.put(name, entry);
                    pendingMixinData.put(name, data);
                    continue;
                } else if (MODEL_JSON.matcher(name).matches()) {
                    data = rewriteModelLoader(name, data, rewritten, removed);
                }
                putEntry(out, entry, name, data);
            }
            if (modsToml == null) {
                throw new IOException("No META-INF/mods.toml in " + source.getFileName());
            }
            for (Map.Entry<String, JarEntry> pending : pendingMixinEntries.entrySet()) {
                String name = pending.getKey();
                putEntry(out, pending.getValue(), name, mixins.rewrite(name, pendingMixinData.get(name)));
            }
            mixins.logSummary();
            rewritten.forEach((from, to) -> LOGGER.info("{} {} model loader {} -> {}", TAG, jarName, from, to));
            for (String id : removed) {
                LOGGER.warn("{} {} removed unknown model loader '{}' from model JSON(s); models may render or "
                        + "behave incorrectly", TAG, source, id);
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

    /**
     * Rewrites the root {@code loader} property of a model JSON: known Forge ids are mapped to NeoForge ids; other
     * ids outside the {@code minecraft}/{@code neoforge} namespaces are removed when
     * {@link #removeUnknownLoaders} is set. Returns the original bytes when nothing changes or the JSON is invalid.
     */
    private byte[] rewriteModelLoader(String name, byte[] data, Map<String, String> rewritten, TreeSet<String> removed) {
        try {
            JsonElement root = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) return data;
            JsonObject obj = root.getAsJsonObject();
            JsonElement loaderEl = obj.get("loader");
            if (loaderEl == null || !loaderEl.isJsonPrimitive() || !loaderEl.getAsJsonPrimitive().isString()) return data;
            String loader = loaderEl.getAsString();
            String mapped = MODEL_LOADER_MAPPINGS.get(loader);
            if (mapped != null) {
                obj.addProperty("loader", mapped);
                rewritten.merge(loader, mapped, (a, b) -> a);
            } else if (removeUnknownLoaders && !loader.startsWith("minecraft:") && !loader.startsWith("neoforge:")) {
                obj.remove("loader");
                removed.add(loader);
            } else {
                return data;
            }
            return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(obj)
                    .getBytes(StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            LOGGER.debug("{} could not parse model JSON {}, leaving it unchanged: {}", TAG, name, e.toString());
            return data;
        }
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
        boolean hasThermalTarget = false;
        for (UnmodifiableConfig sm : sourceMods) {
            if (THERMAL_TARGET_ID.equals(sm.get("modId"))) hasThermalTarget = true;
        }
        for (UnmodifiableConfig sm : sourceMods) {
            String sourceId = sm.get("modId");
            if (sourceId == null) continue;
            String modId = sourceId;
            if (THERMAL_SOURCE_ID.equals(sourceId)) {
                if (hasThermalTarget) {
                    LOGGER.warn("{} {} declares both '{}' and '{}'; keeping '{}' and dropping '{}'", TAG, jarName,
                            THERMAL_TARGET_ID, THERMAL_SOURCE_ID, THERMAL_TARGET_ID, THERMAL_SOURCE_ID);
                    continue;
                }
                modId = THERMAL_TARGET_ID;
                LOGGER.info("{} remapped mod id {} -> {} in {}", TAG, THERMAL_SOURCE_ID, THERMAL_TARGET_ID, jarName);
            }
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

            List<UnmodifiableConfig> sourceDeps = dependenciesOf(source, sourceId);
            List<Config> converted = new ArrayList<>();
            TreeSet<String> seenDeps = new TreeSet<>();
            for (UnmodifiableConfig dep : sourceDeps) {
                Config c = convertDependency(dep);
                if (c == null) continue;
                String depId = c.get("modId");
                if (THERMAL_SOURCE_ID.equals(depId)) {
                    c.set("modId", THERMAL_TARGET_ID);
                    depId = THERMAL_TARGET_ID;
                    LOGGER.info("{} remapped dependency {} -> {} for mod {} in {}", TAG, THERMAL_SOURCE_ID,
                            THERMAL_TARGET_ID, modId, jarName);
                }
                if (depId.equals(modId) || !seenDeps.add(depId)) {
                    LOGGER.warn("{} dropped self/duplicate dependency {} for mod {} in {}", TAG, depId, modId, jarName);
                    continue;
                }
                converted.add(c);
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
        if (depId.equalsIgnoreCase("minecraft")) range = TARGET_MINECRAFT_RANGE;
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
        if ("kotori_scala".equals(modLoader)) {
            LOGGER.warn("{} {} uses modLoader=\"kotori_scala\"; compatibility fallback: rewriting it to \"javafml\", "
                    + "which may not preserve full scala-language semantics", TAG, jarName);
            return;
        }
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
