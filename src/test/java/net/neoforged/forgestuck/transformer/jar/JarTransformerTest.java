package net.neoforged.forgestuck.transformer.jar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class JarTransformerTest {
    static final String TOML = "modLoader=\"javafml\"\nloaderVersion=\"[47,)\"\nlicense=\"MIT\"\n"
        + "[[mods]]\nmodId=\"examplemod\"\nversion=\"1.2.3\"\ndisplayName=\"Example\"\n";

    static void writeJar(Path p, boolean withNeo) throws IOException {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(p))) {
            put(out, "META-INF/mods.toml", TOML.getBytes(StandardCharsets.UTF_8));
            if (withNeo) put(out, "META-INF/neoforge.mods.toml", "x".getBytes());
            put(out, "META-INF/SIGNER.SF", "sig".getBytes());
            put(out, "META-INF/SIGNER.RSA", "sig".getBytes());
            put(out, "META-INF/SIGNER.DSA", "sig".getBytes());
            put(out, "a/B.class", new byte[]{1, 2, 3});
        }
    }

    static void put(JarOutputStream out, String name, byte[] data) throws IOException {
        out.putNextEntry(new JarEntry(name));
        out.write(data);
        out.closeEntry();
    }

    @Test
    void synthesizesNeoTomlAndStripsSignatures(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("in.jar");
        Path out = dir.resolve("sub/out.jar");
        writeJar(src, false);
        new JarTransformer().transformTo(src, out);
        try (JarFile jf = new JarFile(out.toFile())) {
            assertNotNull(jf.getEntry("META-INF/neoforge.mods.toml"));
            assertNotNull(jf.getEntry("META-INF/mods.toml"));
            assertNull(jf.getEntry("META-INF/SIGNER.SF"));
            assertNull(jf.getEntry("META-INF/SIGNER.RSA"));
            assertNull(jf.getEntry("META-INF/SIGNER.DSA"));
            assertArrayEquals(new byte[]{1, 2, 3}, jf.getInputStream(jf.getEntry("a/B.class")).readAllBytes());
            String neo = new String(jf.getInputStream(jf.getEntry("META-INF/neoforge.mods.toml")).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(neo.contains("loaderVersion = \"[2,)\""));
            assertTrue(neo.contains("modId = \"examplemod\""));
            assertTrue(neo.contains("version = \"1.2.3\""));
            assertTrue(neo.contains("displayName = \"Example\""));
        }
    }

    @Test
    void keepsExistingNeoToml(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("in.jar");
        Path out = dir.resolve("out.jar");
        writeJar(src, true);
        new JarTransformer().transformTo(src, out);
        try (JarFile jf = new JarFile(out.toFile())) {
            assertEquals("x", new String(jf.getInputStream(jf.getEntry("META-INF/neoforge.mods.toml")).readAllBytes()));
        }
    }
}
