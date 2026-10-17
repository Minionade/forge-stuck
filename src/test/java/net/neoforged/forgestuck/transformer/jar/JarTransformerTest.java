package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.*;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JarTransformerTest {
    static final String MODS_TOML = """
            modLoader = "kotori_scala"
            loaderVersion = "[1,)"
            license = "MIT"

            [[mods]]
            modId = "advgenerators"
            version = "${file.jarVersion}"
            displayName = "Advanced Generators"
            description = "Generators"
            authors = "Someone"

            [[dependencies.advgenerators]]
            modId = "forge"
            mandatory = true
            versionRange = "[47,)"
            ordering = "NONE"
            side = "BOTH"

            [[dependencies.advgenerators]]
            modId = "minecraft"
            mandatory = true
            versionRange = "[1.20.1,1.21)"
            ordering = "NONE"
            side = "BOTH"

            [[dependencies.advgenerators]]
            modId = "bdlib"
            mandatory = true
            versionRange = "[2,)"
            ordering = "AFTER"
            side = "BOTH"
            """;

    static final byte[] CLASS_BYTES = {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 1, 2, 3, 4};

    static Path buildJar(Path dir, String name, String modsToml, String implVersion) throws IOException {
        Path jar = dir.resolve(name);
        try (OutputStream os = Files.newOutputStream(jar); JarOutputStream out = new JarOutputStream(os)) {
            put(out, "META-INF/MANIFEST.MF", ("Manifest-Version: 1.0\n"
                    + (implVersion == null ? "" : "Implementation-Version: " + implVersion + "\n")
                    + "\nName: com/example/Foo.class\nSHA-256-Digest: abc\n\n").getBytes(StandardCharsets.UTF_8));
            put(out, "META-INF/mods.toml", modsToml.getBytes(StandardCharsets.UTF_8));
            put(out, "META-INF/FAKE.SF", "sig".getBytes(StandardCharsets.UTF_8));
            put(out, "META-INF/FAKE.RSA", "sig".getBytes(StandardCharsets.UTF_8));
            put(out, "com/example/Foo.class", CLASS_BYTES);
            put(out, "assets/readme.txt", "hello".getBytes(StandardCharsets.UTF_8));
        }
        return jar;
    }

    private static void put(JarOutputStream out, String name, byte[] data) throws IOException {
        out.putNextEntry(new JarEntry(name));
        out.write(data);
        out.closeEntry();
    }

    private static byte[] read(Path jar, String entry) throws IOException {
        try (JarFile f = new JarFile(jar.toFile())) {
            JarEntry e = f.getJarEntry(entry);
            if (e == null) return null;
            return f.getInputStream(e).readAllBytes();
        }
    }

    private static JarTransformer plain() {
        return new JarTransformer(List.of());
    }

    @Test
    void nativeAndDualLoaderJarsBypassAllRewritesAndCache(@TempDir Path dir) throws Exception {
        for (boolean dual : new boolean[] {false, true}) {
            Path source = dir.resolve("native-" + dual + ".jar");
            try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(source))) {
                put(out, "META-INF/neoforge.mods.toml", """
                        modLoader = "javafml"
                        loaderVersion = "[4,)"
                        [[mods]]
                        modId = "native_example"
                        version = "1"
                        [[dependencies.native_example]]
                        modId = "minecraft"
                        versionRange = "[1.21.1]"
                        [[dependencies.native_example]]
                        modId = "neoforge"
                        versionRange = "[21.1,)"
                        """.getBytes(StandardCharsets.UTF_8));
                if (dual) put(out, "META-INF/mods.toml", MODS_TOML.getBytes(StandardCharsets.UTF_8));
                put(out, "META-INF/EXAMPLE.SF", new byte[] {1, 2, 3});
                put(out, "example/Hook.class", LegacyGameTestGuardStepTest.hook(
                        "net/neoforged/neoforge/event/RegisterGameTestsEvent", true, "example.gametest.Missing"));
                put(out, "assets/x/models/test.json", "{\"loader\":\"forge:bucket\"}".getBytes(StandardCharsets.UTF_8));
            }
            byte[] original = Files.readAllBytes(source);
            Path cache = dir.resolve("cache");
            JarTransformer transformer = JarTransformer.withDefaultSteps();
            JarTransformer.Result result = transformer.transformCached(source, cache);
            assertEquals(source, result.output());
            assertFalse(result.cacheHit());
            assertFalse(Files.exists(cache));
            Path output = dir.resolve("out-" + dual + ".jar");
            transformer.transform(source, output);
            assertArrayEquals(original, Files.readAllBytes(output));
            assertArrayEquals(original, Files.readAllBytes(source));
        }
    }

    @Test
    void defaultLegacyPipelineAddsGuardAndStillConvertsMetadata(@TempDir Path dir) throws Exception {
        Path source = dir.resolve("legacy.jar");
        byte[] hook = LegacyGameTestGuardStepTest.hook("net/minecraftforge/event/RegisterGameTestsEvent",
                true, "example.gametest.Missing");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(source))) {
            put(out, "META-INF/mods.toml", MODS_TOML.getBytes(StandardCharsets.UTF_8));
            put(out, "example/Hook.class", hook);
        }
        Path output = JarTransformer.withDefaultSteps().transformCached(source, dir.resolve("cache")).output();
        assertNotEquals(source, output);
        assertNotNull(read(output, "META-INF/neoforge.mods.toml"));
        assertNull(read(output, "META-INF/mods.toml"));
        var node = new org.objectweb.asm.tree.ClassNode();
        new org.objectweb.asm.ClassReader(read(output, "example/Hook.class")).accept(node, 0);
        assertTrue(node.methods.stream().anyMatch(method -> {
            for (var insn : method.instructions) {
                if (insn instanceof org.objectweb.asm.tree.MethodInsnNode call
                        && call.owner.equals("net/neoforged/forgestuck/compat/LegacyGameTestSupport")
                        && call.name.equals("shouldSkip")) return true;
            }
            return false;
        }));
        assertArrayEquals(hook, read(source, "example/Hook.class"));
    }

    @Test
    void transformsMetadataAndLeavesSourceUntouched(@TempDir Path dir) throws Exception {
        Path src = buildJar(dir, "adv.jar", MODS_TOML, "1.6.0.6");
        String before = JarTransformer.sha256(src);
        Path out = dir.resolve("out.jar");

        plain().transform(src, out);

        assertEquals(before, JarTransformer.sha256(src));

        assertNull(read(out, "META-INF/FAKE.SF"));
        assertNull(read(out, "META-INF/FAKE.RSA"));
        assertNull(read(out, "META-INF/mods.toml"));
        assertArrayEquals(CLASS_BYTES, read(out, "com/example/Foo.class"));
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), read(out, "assets/readme.txt"));

        Config toml = new TomlParser().parse(new String(read(out, "META-INF/neoforge.mods.toml"), StandardCharsets.UTF_8));
        assertEquals("kotori_scala", toml.get("modLoader"));
        assertEquals("[1,)", toml.get("loaderVersion"));
        assertEquals("MIT", toml.get("license"));
        List<UnmodifiableConfig> mods = toml.get("mods");
        assertEquals(1, mods.size());
        assertEquals("advgenerators", mods.get(0).get("modId"));
        assertEquals("1.6.0.6", mods.get(0).get("version"));
        assertEquals("Advanced Generators", mods.get(0).get("displayName"));
        assertEquals("Someone", mods.get(0).get("authors"));

        List<UnmodifiableConfig> deps = toml.get(List.of("dependencies", "advgenerators"));
        assertEquals(2, deps.size());
        assertTrue(deps.stream().noneMatch(d -> "forge".equals(d.get("modId"))));
        UnmodifiableConfig mc = deps.stream().filter(d -> "minecraft".equals(d.get("modId"))).findFirst().orElseThrow();
        assertEquals(JarTransformer.TARGET_MINECRAFT_RANGE, mc.get("versionRange"));
        assertEquals("required", mc.get("type"));
        UnmodifiableConfig bd = deps.stream().filter(d -> "bdlib".equals(d.get("modId"))).findFirst().orElseThrow();
        assertEquals("[2,)", bd.get("versionRange"));
        assertEquals("AFTER", bd.get("ordering"));

        String manifest = new String(read(out, "META-INF/MANIFEST.MF"), StandardCharsets.UTF_8);
        assertFalse(manifest.contains("SHA-256-Digest"));
    }

    @Test
    void usesFallbackVersionWithoutManifestVersion(@TempDir Path dir) throws Exception {
        Path src = buildJar(dir, "adv.jar", MODS_TOML, null);
        Path out = dir.resolve("out.jar");
        plain().transform(src, out);
        Config toml = new TomlParser().parse(new String(read(out, "META-INF/neoforge.mods.toml"), StandardCharsets.UTF_8));
        List<UnmodifiableConfig> mods = toml.get("mods");
        assertEquals(JarTransformer.DEFAULT_VERSION, mods.get(0).get("version"));
    }

    @Test
    void classStepsAreAppliedAndUnchangedClassesCopiedVerbatim(@TempDir Path dir) throws Exception {
        Path src = buildJar(dir, "adv.jar", MODS_TOML, "1");
        Path out = dir.resolve("out.jar");
        byte[] replaced = {9, 9, 9};
        new JarTransformer(List.of((name, bytes) -> name.equals("com.example.Foo") ? replaced : bytes)).transform(src, out);
        assertArrayEquals(replaced, read(out, "com/example/Foo.class"));
    }

    @Test
    void warnsOnNonJavafmlLoader(@TempDir Path dir) throws Exception {
        Path src = buildJar(dir, "adv.jar", MODS_TOML, "1");
        String[] seen = new String[1];
        new JarTransformer(List.of()) {
            @Override
            protected void onUnsupportedModLoader(String modLoader, String jarName) {
                seen[0] = modLoader;
            }
        }.transform(src, dir.resolve("out.jar"));
        assertEquals("kotori_scala", seen[0]);
    }

    @Test
    void refusesToOverwriteSource(@TempDir Path dir) throws Exception {
        Path src = buildJar(dir, "adv.jar", MODS_TOML, "1");
        assertThrows(IOException.class, () -> plain().transform(src, src));
    }

    @Test
    void cacheIsReusedAndInvalidatedBySourceChange(@TempDir Path dir) throws Exception {
        Path src = buildJar(dir, "adv.jar", MODS_TOML, "1");
        Path cache = dir.resolve("cache");
        JarTransformer t = plain();

        JarTransformer.Result first = t.transformCached(src, cache);
        assertFalse(first.cacheHit());
        assertTrue(Files.isRegularFile(first.output()));
        assertTrue(first.output().getFileName().toString().contains("-t" + JarTransformer.TRANSFORMER_VERSION + "-"));

        JarTransformer.Result second = t.transformCached(src, cache);
        assertTrue(second.cacheHit());
        assertEquals(first.output(), second.output());

        Path changed = buildJar(Files.createDirectory(dir.resolve("other")), "adv.jar", MODS_TOML, "2");
        JarTransformer.Result third = t.transformCached(changed, cache);
        assertFalse(third.cacheHit());
        assertNotEquals(first.output(), third.output());
    }

    private Path jarWith(Path dir, String entry, String json) throws IOException {
        Path jar = dir.resolve("m.jar");
        try (OutputStream os = Files.newOutputStream(jar); JarOutputStream out = new JarOutputStream(os)) {
            put(out, "META-INF/mods.toml", MODS_TOML.getBytes(StandardCharsets.UTF_8));
            put(out, entry, json.getBytes(StandardCharsets.UTF_8));
        }
        return jar;
    }

    private String transformedEntry(Path dir, String entry, String json, boolean fallback) throws IOException {
        Path out = dir.resolve("out.jar");
        new JarTransformer(List.of(), fallback).transform(jarWith(dir, entry, json), out);
        return new String(read(out, entry), StandardCharsets.UTF_8);
    }

    @Test
    void rewritesBucketLoader(@TempDir Path dir) throws Exception {
        String r = transformedEntry(dir, "assets/x/models/item/b.json", "{\"loader\":\"forge:bucket\",\"fluid\":\"minecraft:water\"}", true);
        assertTrue(r.contains("neoforge:fluid_container"));
        assertFalse(r.contains("forge:bucket"));
        assertTrue(r.contains("minecraft:water"));
    }

    @Test
    void rewritesObjLoader(@TempDir Path dir) throws Exception {
        String r = transformedEntry(dir, "assets/x/models/block/o.json", "{\"loader\":\"forge:obj\",\"model\":\"x:models/o.obj\"}", true);
        assertTrue(r.contains("\"neoforge:obj\""));
    }

    @Test
    void removesUnknownLoaderOnlyWhenFallbackEnabled(@TempDir Path dir) throws Exception {
        String json = "{\"loader\":\"advgenerators:multiblock_model\",\"parent\":\"block/cube\"}";
        String r = transformedEntry(dir, "assets/x/models/block/m.json", json, true);
        assertFalse(r.contains("loader"));
        assertTrue(r.contains("block/cube"));
        assertEquals(json, transformedEntry(dir, "assets/x/models/block/m.json", json, false));
    }

    @Test
    void nonModelJsonAndModelsWithoutLoaderAreUntouched(@TempDir Path dir) throws Exception {
        String json = "{\"loader\":\"forge:bucket\"}";
        assertEquals(json, transformedEntry(dir, "assets/x/lang/en_us.json", json, true));
        String plain = "{ \"parent\" : \"block/cube\" }";
        assertEquals(plain, transformedEntry(dir, "assets/x/models/block/p.json", plain, true));
    }

    @Test
    void cacheFileNameContainsTransformerVersion(@TempDir Path dir) throws Exception {
        Path jar = buildJar(dir, "a.jar", MODS_TOML, "1.0");
        Path out = new JarTransformer(List.of()).transformCached(jar, dir.resolve("cache")).output();
        assertTrue(out.getFileName().toString().contains("-t" + JarTransformer.TRANSFORMER_VERSION + "-"));
        assertTrue(out.getFileName().toString().matches("[0-9a-f]{64}-t0-[0-9a-f]{12}\\.jar"));
    }

    @Test
    void cacheKeyDependsOnTransformerStepsAndCode(@TempDir Path dir) throws Exception {
        String sha = "a".repeat(64);
        String none = new JarTransformer(List.of()).cacheFileName(sha);
        assertEquals(none, new JarTransformer(List.of()).cacheFileName(sha));
        assertNotEquals(none, JarTransformer.forRuntime().cacheFileName(sha));
        assertNotEquals(JarTransformer.withDefaultSteps().cacheFileName(sha), JarTransformer.forRuntime().cacheFileName(sha));
    }

    @Test
    void nonJavafmlLoaderVersionRangeIsKept() {
        String toml = new JarTransformer(List.of()).convertModsToml(
                "modLoader=\"kotori_scala\"\nloaderVersion=\"[3.3.0,)\"\nlicense=\"x\"\n[[mods]]\nmodId=\"sc\"\n", "1", "sc.jar");
        var parsed = new TomlParser().parse(toml);
        assertEquals("kotori_scala", parsed.get("modLoader"));
        assertEquals("[3.3.0,)", parsed.get("loaderVersion"));
        String java = new JarTransformer(List.of()).convertModsToml(
                "modLoader=\"javafml\"\nloaderVersion=\"[47,)\"\nlicense=\"x\"\n[[mods]]\nmodId=\"j\"\n", "1", "j.jar");
        assertEquals("[1,)", new TomlParser().parse(java).get("loaderVersion"));
    }
    @Test
    void nonJavafmlLoaderIsPreserved(@TempDir Path dir) throws Exception {
        Path src = buildJar(dir, "adv.jar", MODS_TOML, "1");
        Path out = dir.resolve("out.jar");
        new JarTransformer(List.of()).transform(src, out);
        String toml = new String(read(out, "META-INF/neoforge.mods.toml"), StandardCharsets.UTF_8);
        assertEquals("kotori_scala", new TomlParser().parse(toml).get("modLoader"));
        assertEquals("[1,)", new TomlParser().parse(toml).get("loaderVersion"));
    }

    @Test
    void modIdsAreNeverRenamed() {
        String toml = """
                modLoader = "javafml"
                [[mods]]
                modId = "alias_style_id"
                version = "1.0"
                [[dependencies.alias_style_id]]
                modId = "shared_core"
                mandatory = true
                versionRange = "[1,)"
                """;
        Config out = new TomlParser().parse(new JarTransformer(List.of()).convertModsToml(toml, "1.0", "a.jar"));
        List<UnmodifiableConfig> mods = out.get("mods");
        assertEquals("alias_style_id", mods.get(0).get("modId"));
        List<UnmodifiableConfig> deps = out.get(List.of("dependencies", "alias_style_id"));
        assertEquals("shared_core", deps.get(0).get("modId"));
    }

    @Test
    void thermalStyleIdsAndDependenciesAreKeptAsDeclared() {
        String toml = """
                modLoader = "javafml"
                [[mods]]
                modId = "thermal_foundation"
                version = "1.0"
                [[dependencies.thermal_foundation]]
                modId = "thermal"
                mandatory = true
                versionRange = "[11.0.6,11.1.0)"
                """;
        Config out = new TomlParser().parse(new JarTransformer(List.of()).convertModsToml(toml, "1.0", "tf.jar"));
        List<UnmodifiableConfig> mods = out.get("mods");
        assertEquals("thermal_foundation", mods.get(0).get("modId"));
        List<UnmodifiableConfig> deps = out.get(List.of("dependencies", "thermal_foundation"));
        assertEquals("thermal", deps.get(0).get("modId"));
    }

    @Test
    void transformedMetadataIdsMatchModAnnotations(@org.junit.jupiter.api.io.TempDir Path dir) throws IOException {
        org.objectweb.asm.ClassWriter cw = new org.objectweb.asm.ClassWriter(0);
        cw.visit(org.objectweb.asm.Opcodes.V21, org.objectweb.asm.Opcodes.ACC_PUBLIC, "unrelated/ns/Entry", null,
                "java/lang/Object", null);
        cw.visitAnnotation("Lnet/minecraftforge/fml/common/Mod;", true).visit("value", "thermal_foundation");
        cw.visitEnd();
        String toml = """
                modLoader = "javafml"
                [[mods]]
                modId = "thermal_foundation"
                version = "1.0"
                [[dependencies.thermal_foundation]]
                modId = "thermal"
                mandatory = true
                versionRange = "[1,)"
                """;
        Path src = dir.resolve("src.jar");
        try (OutputStream os = Files.newOutputStream(src); JarOutputStream jos = new JarOutputStream(os)) {
            SrgCompatTest.put(jos, "META-INF/mods.toml", SrgCompatTest.b(toml));
            SrgCompatTest.put(jos, "unrelated/ns/Entry.class", cw.toByteArray());
        }
        Path out = dir.resolve("out.jar");
        new JarTransformer(List.of()).transform(src, out);

        List<UnmodifiableConfig> mods = new TomlParser()
                .parse(new String(read(out, "META-INF/neoforge.mods.toml"), StandardCharsets.UTF_8)).get("mods");
        java.util.Set<String> ids = new java.util.TreeSet<>();
        mods.forEach(m -> ids.add(m.get("modId")));
        try (JarFile jf = new JarFile(out.toFile())) {
            java.util.Set<String> annotated = JarTransformer.modAnnotationValues(jf);
            assertEquals(java.util.Set.of("thermal_foundation"), annotated);
            assertTrue(ids.containsAll(annotated), "dangling @Mod entrypoint: " + annotated + " vs " + ids);
        }
        try (JarFile jf = new JarFile(src.toFile())) {
            assertTrue(JarTransformer.validateEntrypoints(jf, new JarTransformer(List.of())
                    .convertModsToml(toml, "1.0", "src.jar"), "src.jar").isEmpty());
            assertEquals(java.util.Set.of("thermal_foundation"), JarTransformer.validateEntrypoints(jf,
                    "[[mods]]\nmodId = \"thermal\"\n", "src.jar"));
        }
    }
    @Test
    void nonThermalModsAreUnchanged() {
        String toml = """
                modLoader = "javafml"
                [[mods]]
                modId = "cofh_core"
                version = "1.0"
                [[dependencies.cofh_core]]
                modId = "bdlib"
                mandatory = true
                versionRange = "[2,)"
                """;
        Config out = new TomlParser().parse(new JarTransformer(List.of()).convertModsToml(toml, "1.0", "c.jar"));
        List<UnmodifiableConfig> mods = out.get("mods");
        assertEquals("cofh_core", mods.get(0).get("modId"));
        List<UnmodifiableConfig> deps = out.get(List.of("dependencies", "cofh_core"));
        assertEquals("bdlib", deps.get(0).get("modId"));
    }
}
