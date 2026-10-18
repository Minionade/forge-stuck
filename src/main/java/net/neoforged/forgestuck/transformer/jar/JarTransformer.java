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
import java.util.Set;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;
import net.neoforged.forgestuck.locator.LegacyJarDetector;
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
    public static final String TRANSFORMER_VERSION = "33";
    public static final String DEFAULT_VERSION = "0.0.0-forgestuck";
    public static final String TARGET_MINECRAFT_RANGE = "[1.21.1,)";

    private static final Logger LOGGER = LoggerFactory.getLogger("forgestuck");
    private static final Pattern JIJ_JAR = Pattern.compile("META-INF/jarjar/[^/]+\\.jar");
    private static final String TAG = "[Forge Stuck!!]";
    private static final Pattern SIGNATURE = Pattern.compile("META-INF/[^/]+\\.(SF|RSA|DSA)", Pattern.CASE_INSENSITIVE);

    /**
     * Default for {@link #removeUnknownLoaders}. Unknown custom loaders are preserved (and reported) by default so no
     * model content is degraded; the opt-in {@code true} mode strips the loader property, which loses fidelity.
     */
    public static final boolean REMOVE_UNKNOWN_MODEL_LOADERS = false;

    /** Direct Forge 1.20.1 -> NeoForge 1.21.1 model loader id mappings. */
    public static final Map<String, String> MODEL_LOADER_MAPPINGS = Map.of(
            "forge:bucket", "neoforge:fluid_container",
            "forge:obj", "neoforge:obj",
            "forge:composite", "neoforge:composite",
            "forge:separate_transforms", "neoforge:separate_transforms",
            "forge:empty", "neoforge:empty");

    private static final Pattern MODEL_JSON = Pattern.compile("assets/[^/]+/models/.+\\.json");

    /** Result of a cached transformation; a native bypass returns the source with {@code cacheHit=false}. */
    public record Result(Path output, boolean cacheHit) {}

    private final List<ClassTransformStep> steps;
    private final boolean removeUnknownLoaders;
    private final LegacyNameMappings mappings;
    private TransformProgress progress = TransformProgress.NONE;
    private final ThreadLocal<String> currentJar = ThreadLocal.withInitial(() -> "");

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
        return new JarTransformer(List.of(new LegacySpawnPlacementStep(), new LegacyHurtAndShearingStep(), new LegacyAttackAndTreeStep(), new LegacyInstanceSubscriberStep(), new LegacyRenderLivingListenerStep(), new LegacyConditionCodecStep(), new LegacyResourceLocationSubclassStep(), new LegacyEnumCreateRewriteStep(), new LegacyLootTypeRewriteStep(), new LegacyEnchantmentSubclassStep(), new LegacyBufferBuilderRewriteStep(), new ForgeClassTransformStep(), new ForgeOwnerRemapStep(),
                new ResourceLocationClassTransformStep(), new SrgMemberRemapStep(), new GameFieldRepairStep(), new LegacyGameTestGuardStep()));
    }

    /**
     * Default steps plus {@link CompatRelocationStep}: the transformer used at runtime, so transformed mods link to the
     * game-layer copy of the compat classes (see {@link CompatRelocation}).
     */
    public static JarTransformer forRuntime() {
        List<ClassTransformStep> all = new java.util.ArrayList<>(withDefaultSteps().steps);
        all.add(new CompatRelocationStep());
        return new JarTransformer(all);
    }

    private static volatile String codeFingerprint;

    /**
     * Cache file name for a source hash: {@code <sha>-t<VERSION>-<fp>.jar}. {@code fp} hashes Forge Stuck's own
     * code/resources and this transformer's step list, so changed transformer code never reuses stale cache entries
     * even while {@link #TRANSFORMER_VERSION} stays fixed.
     */
    public String cacheFileName(String sourceSha) {
        var d = newDigest();
        d.update(codeFingerprint().getBytes(StandardCharsets.UTF_8));
        for (ClassTransformStep s : steps) d.update(s.getClass().getName().getBytes(StandardCharsets.UTF_8));
        d.update((byte) (removeUnknownLoaders ? 1 : 0));
        return sourceSha + "-t" + TRANSFORMER_VERSION + "-" + java.util.HexFormat.of().formatHex(d.digest(), 0, 6)
                + ".jar";
    }

    private static java.security.MessageDigest newDigest() {
        try {
            return java.security.MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String codeFingerprint() {
        String fp = codeFingerprint;
        if (fp != null) return fp;
        var d = newDigest();
        try {
            Path root = Path.of(JarTransformer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(root)) {
                d.update(Files.readAllBytes(root));
            } else {
                try (var walk = Files.walk(root)) {
                    for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile).sorted()::iterator) {
                        d.update(root.relativize(p).toString().getBytes(StandardCharsets.UTF_8));
                        d.update(Files.readAllBytes(p));
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("{} could not fingerprint transformer code ({}); cache keyed by version only", TAG, e.toString());
        }
        return codeFingerprint = java.util.HexFormat.of().formatHex(d.digest());
    }

    /** Returns the cached output for this source jar, creating it when missing. */
    public Result transformCached(Path source, Path cacheDir) throws IOException {
        if (bypassNative(source)) return new Result(source, false);
        String jarName = source.getFileName().toString();
        Files.createDirectories(cacheDir);
        Path target = cacheDir.resolve(cacheFileName(sha256(source)));
        if (Files.isRegularFile(target) && Files.size(target) > 0) {
            LOGGER.info("{} cache hit for {} -> {}", TAG, source.getFileName(), target);
            progress.cached(jarName);
            return new Result(target, true);
        }
        LOGGER.info("{} cache miss for {}", TAG, source.getFileName());
        progress.transforming(jarName);
        long start = System.nanoTime();
        Path tmp = Files.createTempFile(cacheDir, "forgestuck-", ".tmp");
        String previous = currentJar.get();
        currentJar.set(jarName);
        try {
            transform(source, tmp);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            currentJar.set(previous);
            Files.deleteIfExists(tmp);
        }
        long millis = (System.nanoTime() - start) / 1_000_000;
        LOGGER.info("{} transformed jar created: {} ({} ms)", TAG, target, millis);
        progress.transformed(jarName, millis);
        return new Result(target, false);
    }

    /** Reports progress of {@link #transformCached}; returns this transformer. */
    public JarTransformer withProgress(TransformProgress listener) {
        this.progress = listener == null ? TransformProgress.NONE : listener;
        return this;
    }

    /** Writes the transformed jar to {@code output}; {@code source} is only read. */
    public void transform(Path source, Path output) throws IOException {
        if (source.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize())) {
            throw new IOException("Refusing to overwrite the source jar " + source);
        }
        if (bypassNative(source)) {
            Files.copy(source, output, StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        try (JarFile in = new JarFile(source.toFile());
                OutputStream fos = Files.newOutputStream(output);
                JarOutputStream out = new JarOutputStream(fos)) {
            String modsToml = null;
            String jarName = source.getFileName().toString();
            Map<String, String> rewritten = new LinkedHashMap<>();
            TreeSet<String> removed = new TreeSet<>();
            MixinResourceRewriter mixins = new MixinResourceRewriter(mappings, jarName);
            OwnerRemapReport ownerReport = new OwnerRemapReport();
            List<String> enumCandidates = prescanClasses(in, ownerReport);
            ResourceRewriteReport resourceReport = new ResourceRewriteReport();
            java.util.Set<String> allNames = new java.util.HashSet<>();
            in.stream().forEach(je -> allNames.add(je.getName()));
            ResourceDataRewriteStep resources = new ResourceDataRewriteStep(allNames, resourceReport);
            boolean needsRlAccess = false;
            byte[] existingAt = null;
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
                if (name.equals(LegacyResourceLocationSubclassStep.AT_PATH)) {
                    existingAt = data;
                    continue;
                }
                if (JIJ_JAR.matcher(name).matches()) {
                    data = transformNested(name, data);
                    putEntry(out, entry, name, data);
                    continue;
                }
                if (name.equals(JarFile.MANIFEST_NAME)) {
                    data = stripManifestDigests(data);
                } else if (name.endsWith(".class")) {
                    needsRlAccess |= LegacyResourceLocationSubclassStep.extendsResourceLocation(data);
                    data = applySteps(name, data, ownerReport);
                    mixins.inspectClass(name.substring(0, name.length() - ".class".length()).replace('/', '.'), data);
                } else if (MixinResourceRewriter.isCandidate(name)) {
                    pendingMixinEntries.put(name, entry);
                    pendingMixinData.put(name, data);
                    continue;
                } else if (MODEL_JSON.matcher(name).matches()) {
                    data = rewriteModelLoader(name, data, rewritten, removed);
                } else if (name.startsWith("data/") && name.endsWith(".json")) {
                    data = resources.content(name, data);
                }
                putEntry(out, entry, resources.entryName(name), data);
            }
            if (modsToml == null) {
                throw new IOException("No META-INF/mods.toml in " + source.getFileName());
            }
            for (Map.Entry<String, JarEntry> pending : pendingMixinEntries.entrySet()) {
                String name = pending.getKey();
                putEntry(out, pending.getValue(), name, mixins.rewrite(name, pendingMixinData.get(name)));
            }
            ownerReport.log(LOGGER, TAG, jarName);
            resourceReport.log(LOGGER, TAG, jarName);
            mixins.logSummary();
            LOGGER.info("{} {} summary: {} owner reference(s) remapped in {} class(es), {} method "
                    + "descriptor(s) adapted, {} mixin class(es) dropped",
                    TAG, jarName, ownerReport.total(), ownerReport.classesRewritten(),
                    ownerReport.descriptorsAdapted(), mixins.droppedMixins().size());
            rewritten.forEach((from, to) -> LOGGER.info("{} {} model loader {} -> {}", TAG, jarName, from, to));
            for (String id : removed) {
                if (removeUnknownLoaders) {
                    LOGGER.warn("{} {} removed unknown model loader '{}' from model JSON(s); models may render or "
                            + "behave incorrectly", TAG, source, id);
                } else {
                    LOGGER.warn("{} {} preserved unknown model loader '{}' in model JSON(s); it works only if the "
                            + "loader is registered on this runtime", TAG, source, id);
                }
            }
            if (needsRlAccess) {
                LOGGER.info("{} {} has ResourceLocation subclass(es); emitting access transformer to open the final class and its constructor", TAG, jarName);
            }
            if (needsRlAccess || existingAt != null) {
                JarEntry at = new JarEntry(LegacyResourceLocationSubclassStep.AT_PATH);
                out.putNextEntry(at);
                if (existingAt != null) out.write(existingAt);
                if (needsRlAccess) {
                    out.write(LegacyResourceLocationSubclassStep.AT_LINES.getBytes(StandardCharsets.UTF_8));
                }
                out.closeEntry();
            }
            String version = implementationVersion(in);
            Map<String, String> enumFiles = writeEnumExtensions(in, enumCandidates, modsToml, jarName, out);
            String converted = convertModsToml(modsToml, version, source.getFileName().toString(), enumFiles);
            validateEntrypoints(in, converted, jarName);
            JarEntry generated = new JarEntry("META-INF/neoforge.mods.toml");
            out.putNextEntry(generated);
            out.write(converted.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
    }

    /** Collects the {@code @Mod} values declared by classes in the source jar. */
    static Set<String> modAnnotationValues(JarFile in) throws IOException {
        Set<String> ids = new TreeSet<>();
        Enumeration<JarEntry> entries = in.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;
            try (InputStream is = in.getInputStream(entry)) {
                ids.addAll(modAnnotationValues(is.readAllBytes()));
            } catch (RuntimeException e) {
                LOGGER.warn("{} could not scan {} for @Mod: {}", TAG, entry.getName(), e.toString());
            }
        }
        return ids;
    }

    static Set<String> modAnnotationValues(byte[] classBytes) {
        Set<String> ids = new TreeSet<>();
        new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (!"Lnet/minecraftforge/fml/common/Mod;".equals(descriptor)
                        && !"Lnet/neoforged/fml/common/Mod;".equals(descriptor)) {
                    return null;
                }
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public void visit(String name, Object value) {
                        if ("value".equals(name) && value instanceof String s) ids.add(s);
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return ids;
    }

    /** Logs an error for every {@code @Mod} value that has no matching modId in the generated metadata. */
    static Set<String> validateEntrypoints(JarFile in, String convertedToml, String jarName) throws IOException {
        Set<String> declared = new TreeSet<>();
        List<UnmodifiableConfig> mods = new TomlParser().parse(convertedToml).getOrElse("mods", List.of());
        for (UnmodifiableConfig mod : mods) declared.add(mod.get("modId"));
        Set<String> dangling = new TreeSet<>();
        for (String id : modAnnotationValues(in)) {
            if (!declared.contains(id)) {
                dangling.add(id);
                LOGGER.error("{} {} has @Mod(\"{}\") but generated metadata declares only {}; FML will report a "
                        + "dangling entrypoint", TAG, jarName, id, declared);
            }
        }
        return dangling;
    }
    /**
     * Legacy Forge jar-in-jar entries run through the same pipeline so the nested mod (and the ids it provides) is
     * discoverable by NeoForge's JarInJar locator; nested jars with any other shape are kept byte-for-byte.
     */
    private byte[] transformNested(String entryName, byte[] data) throws IOException {
        Path dir = Files.createTempDirectory("forgestuck-nested-");
        try {
            Path in = dir.resolve(entryName.substring(entryName.lastIndexOf('/') + 1));
            Files.write(in, data);
            if (!LegacyJarDetector.isLegacyCandidate(in)) return data;
            Path out = dir.resolve("transformed.jar");
            LOGGER.info("{} transforming nested legacy jar {}", TAG, entryName);
            progress.nested(currentJar.get(), entryName);
            transform(in, out);
            return Files.readAllBytes(out);
        } finally {
            try (var walk = Files.walk(dir)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }
    private List<String> prescanClasses(JarFile in, OwnerRemapReport report) throws IOException {
        List<String> enumCandidates = new ArrayList<>();
        Enumeration<JarEntry> entries = in.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (entry.isDirectory() || !name.endsWith(".class")) continue;
            byte[] data;
            try (InputStream is = in.getInputStream(entry)) {
                data = is.readAllBytes();
            }
            String className = name.substring(0, name.length() - ".class".length()).replace('/', '.');
            if (LegacyEnumPrescan.mentionsLegacyExtensibleEnum(data)) {
                enumCandidates.add(name.substring(0, name.length() - ".class".length()));
            }
            for (ClassTransformStep step : steps) {
                try {
                    step.scan(className, data, report);
                } catch (Exception | LinkageError e) {
                    LOGGER.warn("{} class pre-scan {} failed on {}: {}", TAG, step.getClass().getSimpleName(),
                            className, e.toString());
                }
            }
        }
        return enumCandidates;
    }

    /**
     * Generates NeoForge enum-extension JSON (one file per mod) for statically evaluable legacy enum factories and
     * returns the metadata path to register for each mod id.
     */
    private Map<String, String> writeEnumExtensions(JarFile in, List<String> candidates, String modsToml,
            String jarName, JarOutputStream out) throws IOException {
        if (candidates.isEmpty()) return Map.of();
        LegacyEnumExtensionPlanner.Plan plan = LegacyEnumExtensionPlanner.plan(candidates, name -> {
            JarEntry entry = in.getJarEntry(name + ".class");
            if (entry == null) return null;
            try (InputStream is = in.getInputStream(entry)) {
                return is.readAllBytes();
            } catch (IOException e) {
                return null;
            }
        });
        if (plan.entries().isEmpty() && plan.placementEntries().isEmpty() && plan.unresolvedSites().isEmpty()
                && plan.unresolvedPlacementSites().isEmpty()) return Map.of();
        Set<String> modIds = new TreeSet<>();
        List<UnmodifiableConfig> sourceMods = new TomlParser().parse(modsToml).getOrElse("mods", List.of());
        for (UnmodifiableConfig mod : sourceMods) {
            String id = mod.get("modId");
            if (id != null) modIds.add(id);
        }
        Map<String, String> files = new LinkedHashMap<>();
        boolean includeUnprefixedPlacements = modIds.size() == 1;
        for (String modId : modIds) {
            String json = EnumExtensionJson.render(modId, plan.entries(), plan.placementEntries(),
                    includeUnprefixedPlacements);
            if (json == null) continue;
            String path = EnumExtensionJson.path(modId);
            out.putNextEntry(new JarEntry(path));
            out.write(json.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            files.put(modId, path);
        }
        for (LegacyEnumExtensionPlanner.Entry entry : plan.entries()) {
            if (!modIds.contains(entry.namespace())) {
                LOGGER.warn("{} {} ItemDisplayContext.create id {} has a namespace that is not a mod id of this jar; "
                        + "NeoForge requires the id to be prefixed with the owning mod id, so no enum constant is "
                        + "generated and that call fails when executed", TAG, jarName, entry.id());
            }
        }
        LOGGER.info("{} {} generated {} ItemDisplayContext enum extension(s) for mod(s) {}", TAG, jarName,
                plan.entries().size(), files.keySet());
        LOGGER.info("{} {} generated {} SpawnPlacements.Type enum extension(s)", TAG, jarName,
                plan.placementEntries().size());
        for (String site : plan.unresolvedSites()) {
            LOGGER.warn("{} {} ItemDisplayContext.create in {} has arguments that cannot be evaluated statically; "
                    + "that call fails precisely when executed (no enum extension generated)", TAG, jarName, site);
        }
        for (String site : plan.unresolvedPlacementSites()) {
            LOGGER.warn("{} {} SpawnPlacements.Type.create in {} has arguments that cannot be evaluated statically; "
                    + "that call fails precisely when executed (no enum extension generated)", TAG, jarName, site);
        }
        for (LegacyEnumExtensionPlanner.PlacementEntry entry : plan.placementEntries()) {
            if (modIds.size() > 1 && modIds.stream().noneMatch(id ->
                    entry.name().toLowerCase(Locale.ROOT).startsWith(id.toLowerCase(Locale.ROOT)))) {
                LOGGER.warn("{} {} SpawnPlacements.Type.create name {} does not identify a mod id in this "
                        + "multi-mod jar; no enum constant is generated for it", TAG, jarName, entry.name());
            }
        }
        return files;
    }

    private static boolean bypassNative(Path source) throws IOException {
        if (!LegacyJarDetector.isNativeNeoForge(source)) return false;
        LOGGER.info("{} bypass transform (native NeoForge descriptor; runtime validation left to NeoForge): {}",
                TAG, source.getFileName());
        return true;
    }

    private byte[] applySteps(String entryName, byte[] data, OwnerRemapReport report) {
        String className = entryName.substring(0, entryName.length() - ".class".length()).replace('/', '.');
        byte[] current = data;
        for (ClassTransformStep step : steps) {
            try {
                byte[] result = step.apply(className, current, report);
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
            } else if (!loader.startsWith("minecraft:") && !loader.startsWith("neoforge:")) {
                removed.add(loader);
                if (!removeUnknownLoaders) return data;
                obj.remove("loader");
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
        return convertModsToml(forgeToml, jarVersion, jarName, Map.of());
    }

    /** @param enumExtensions metadata path of the generated enum-extension JSON per mod id */
    String convertModsToml(String forgeToml, String jarVersion, String jarName, Map<String, String> enumExtensions) {
        Config source = new TomlParser().parse(forgeToml);
        String loader = source.getOrElse("modLoader", "javafml");
        if (!"javafml".equals(loader)) {
            onUnsupportedModLoader(loader, jarName);
        }

        Config out = Config.inMemory();
        // Only the Forge Java provider is replaced by FML's own javafml; any other language provider (e.g. a
        // NeoForge-native Scala provider) keeps its loader id and version range so FML routes construction to it.
        boolean javaLoader = "javafml".equals(loader);
        out.set("modLoader", loader);
        out.set("loaderVersion", javaLoader ? "[1,)" : source.getOrElse("loaderVersion", "[1,)"));
        out.set("license", source.getOrElse("license", "All Rights Reserved"));
        copyIfPresent(source, out, "issueTrackerURL");

        List<Config> mods = new ArrayList<>();
        Config deps = Config.inMemory();
        List<UnmodifiableConfig> sourceMods = source.getOrElse("mods", List.<UnmodifiableConfig>of());
        for (UnmodifiableConfig sm : sourceMods) {
            String sourceId = sm.get("modId");
            if (sourceId == null) continue;
            String modId = sourceId;
            Config mod = Config.inMemory();
            mod.set("modId", modId);
            String version = sm.getOrElse("version", jarVersion);
            mod.set("version", version.replace("${file.jarVersion}", jarVersion));
            mod.set("displayName", sm.getOrElse("displayName", modId));
            mod.set("description", sm.getOrElse("description", "Legacy Forge mod adapted by Forge Stuck!!"));
            String authors = sm.get("authors");
            if (authors == null) authors = source.get("authors");
            if (authors != null) mod.set("authors", authors);
            if (enumExtensions.containsKey(modId)) mod.set("enumExtensions", enumExtensions.get(modId));
            mods.add(mod);

            List<UnmodifiableConfig> sourceDeps = dependenciesOf(source, sourceId);
            List<Config> converted = new ArrayList<>();
            TreeSet<String> seenDeps = new TreeSet<>();
            for (UnmodifiableConfig dep : sourceDeps) {
                Config c = convertDependency(dep);
                if (c == null) continue;
                String depId = c.get("modId");
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
     * Extension point for mods that use a non-javafml language provider. The loader id is preserved in the output
     * metadata; FML reports a missing provider explicitly if no matching provider jar is installed.
     */
    protected void onUnsupportedModLoader(String modLoader, String jarName) {
        LOGGER.info("{} {} uses modLoader=\"{}\"; preserving it. Construction is delegated to that language "
                + "provider, which must be installed as a NeoForge-compatible jar (not verified here)",
                TAG, jarName, modLoader);
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
