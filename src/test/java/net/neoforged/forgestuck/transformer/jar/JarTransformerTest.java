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
        assertEquals("javafml", toml.get("modLoader"));
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
        assertTrue(first.output().getFileName().toString().contains("-t" + JarTransformer.TRANSFORMER_VERSION));

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
        assertTrue(out.getFileName().toString().endsWith("-t" + JarTransformer.TRANSFORMER_VERSION + ".jar"));
        assertNotEquals("1", JarTransformer.TRANSFORMER_VERSION);
    }

    @Test
    void kotoriScalaIsRewrittenToJavafml(@TempDir Path dir) throws Exception {
        Path src = buildJar(dir, "adv.jar", MODS_TOML, "1");
        Path out = dir.resolve("out.jar");
        new JarTransformer(List.of()).transform(src, out);
        String toml = new String(read(out, "META-INF/neoforge.mods.toml"), StandardCharsets.UTF_8);
        assertEquals("javafml", new TomlParser().parse(toml).get("modLoader"));
    }

    @Test
    void thermalFoundationIdIsRemappedToThermal() {
        String toml = """
                modLoader = "javafml"
                [[mods]]
                modId = "thermal_foundation"
                version = "1.0"
                [[dependencies.thermal_foundation]]
                modId = "cofh_core"
                mandatory = true
                versionRange = "[1,)"
                """;
        Config out = new TomlParser().parse(new JarTransformer(List.of()).convertModsToml(toml, "1.0", "tf.jar"));
        List<UnmodifiableConfig> mods = out.get("mods");
        assertEquals(1, mods.size());
        assertEquals("thermal", mods.get(0).get("modId"));
        List<UnmodifiableConfig> deps = out.get(List.of("dependencies", "thermal"));
        assertEquals("cofh_core", deps.get(0).get("modId"));
    }

    @Test
    void thermalDependencyTargetsAreNormalized() {
        String toml = """
                modLoader = "javafml"
                [[mods]]
                modId = "thermal_expansion"
                version = "1.0"
                [[dependencies.thermal_expansion]]
                modId = "thermal_foundation"
                mandatory = true
                versionRange = "[11.0.3,11.1.0)"
                """;
        Config out = new TomlParser().parse(new JarTransformer(List.of()).convertModsToml(toml, "1.0", "te.jar"));
        List<UnmodifiableConfig> deps = out.get(List.of("dependencies", "thermal_expansion"));
        assertEquals(1, deps.size());
        assertEquals("thermal", deps.get(0).get("modId"));
        assertEquals("[11.0.3,11.1.0)", deps.get(0).get("versionRange"));
    }

    @Test
    void thermalCoexistenceKeepsSingleThermal() {
        String toml = """
                modLoader = "javafml"
                [[mods]]
                modId = "thermal"
                version = "1.0"
                [[mods]]
                modId = "thermal_foundation"
                version = "1.0"
                """;
        Config out = new TomlParser().parse(new JarTransformer(List.of()).convertModsToml(toml, "1.0", "t.jar"));
        List<UnmodifiableConfig> mods = out.get("mods");
        assertEquals(1, mods.size());
        assertEquals("thermal", mods.get(0).get("modId"));
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
