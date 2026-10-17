package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.*;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NestedJarTest {
    static final String PATH = "META-INF/jarjar/lib-1.0.jar";

    static byte[] jar(Map<String, String> files) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (JarOutputStream out = new JarOutputStream(bos)) {
            for (var e : files.entrySet()) {
                out.putNextEntry(new JarEntry(e.getKey()));
                out.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    static String legacy(String id) {
        return "modLoader=\"javafml\"\nloaderVersion=\"[47,)\"\nlicense=\"x\"\n[[mods]]\nmodId=\"" + id
                + "\"\nversion=\"1.0\"\n";
    }

    static Path outer(Path dir, String name, Map<String, byte[]> extra) throws IOException {
        Path p = dir.resolve(name);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(p))) {
            String toml = legacy("outer_mod") + "[[dependencies.outer_mod]]\nmodId=\"provided_lib\"\n"
                    + "mandatory=true\nversionRange=\"[1,)\"\n";
            SrgCompatTest.put(out, "META-INF/mods.toml", SrgCompatTest.b(toml));
            SrgCompatTest.put(out, "META-INF/jarjar/metadata.json", SrgCompatTest.b("{\"jars\":[]}"));
            for (var e : extra.entrySet()) SrgCompatTest.put(out, e.getKey(), e.getValue());
        }
        return p;
    }

    static List<String> modIds(byte[] jarBytes, String tomlName, Path dir) throws IOException {
        Path p = Files.write(dir.resolve("probe-" + System.nanoTime() + ".jar"), jarBytes);
        try (JarFile jf = new JarFile(p.toFile())) {
            var e = jf.getJarEntry(tomlName);
            if (e == null) return null;
            String toml = new String(jf.getInputStream(e).readAllBytes(), StandardCharsets.UTF_8);
            List<String> ids = new ArrayList<>();
            List<UnmodifiableConfig> mods = new TomlParser().parse(toml).get("mods");
            mods.forEach(m -> ids.add(m.get("modId")));
            return ids;
        }
    }

    @Test
    void nestedLegacyJarIsTransformedAndProvidesDependency(@TempDir Path dir) throws Exception {
        byte[] nested = jar(new TreeMap<>(Map.of("META-INF/mods.toml", legacy("provided_lib"))));
        Path src = outer(dir, "outer.jar", Map.of(PATH, nested));
        Path out = dir.resolve("out.jar");
        new JarTransformer(List.of()).transform(src, out);

        List<String> outerIds = modIds(Files.readAllBytes(out), "META-INF/neoforge.mods.toml", dir);
        assertEquals(List.of("outer_mod"), outerIds);
        byte[] nestedOut = SrgCompatTest.read(out, PATH);
        assertEquals(List.of("provided_lib"), modIds(nestedOut, "META-INF/neoforge.mods.toml", dir));
        assertNull(modIds(nestedOut, "META-INF/mods.toml", dir));
        assertEquals("{\"jars\":[]}", new String(SrgCompatTest.read(out, "META-INF/jarjar/metadata.json"),
                StandardCharsets.UTF_8));
        assertFalse(outerIds.contains("provided_lib"), "no duplicate ids across outer and nested");
    }

    @Test
    void nestedNativeAndPlainJarsAreUntouched(@TempDir Path dir) throws Exception {
        byte[] nativeJar = jar(new TreeMap<>(Map.of("META-INF/neoforge.mods.toml", legacy("native_lib"),
                "META-INF/mods.toml", legacy("native_lib"))));
        byte[] plain = jar(new TreeMap<>(Map.of("a/B.txt", "hi")));
        Path src = outer(dir, "outer.jar", Map.of(PATH, nativeJar, "META-INF/jarjar/plain-1.jar", plain));
        Path out = dir.resolve("out.jar");
        new JarTransformer(List.of()).transform(src, out);
        assertArrayEquals(nativeJar, SrgCompatTest.read(out, PATH));
        assertArrayEquals(plain, SrgCompatTest.read(out, "META-INF/jarjar/plain-1.jar"));
    }

    @Test
    void outputIsIdempotentAndCacheKeyedByVersion(@TempDir Path dir) throws Exception {
        byte[] nested = jar(new TreeMap<>(Map.of("META-INF/mods.toml", legacy("provided_lib"))));
        Path src = outer(dir, "outer.jar", Map.of(PATH, nested));
        Path out1 = dir.resolve("o1.jar");
        Path out2 = dir.resolve("o2.jar");
        new JarTransformer(List.of()).transform(src, out1);
        new JarTransformer(List.of()).transform(out1, out2);
        assertArrayEquals(Files.readAllBytes(out1), Files.readAllBytes(out2));
        assertNotEquals("11", JarTransformer.TRANSFORMER_VERSION);
    }
}