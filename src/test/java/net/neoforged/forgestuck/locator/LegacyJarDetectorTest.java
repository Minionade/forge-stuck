package net.neoforged.forgestuck.locator;

import static org.junit.jupiter.api.Assertions.*;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyJarDetectorTest {
    private static Path jar(Path dir, String name, String... entries) throws Exception {
        Path p = dir.resolve(name);
        try (OutputStream os = Files.newOutputStream(p); JarOutputStream out = new JarOutputStream(os)) {
            for (String e : entries) {
                out.putNextEntry(new JarEntry(e));
                out.write("x = 1".getBytes());
                out.closeEntry();
            }
        }
        return p;
    }

    @Test
    void modsTomlOnlyIsLegacy(@TempDir Path dir) throws Exception {
        assertTrue(LegacyJarDetector.isLegacyCandidate(jar(dir, "a.jar", "META-INF/mods.toml")));
    }

    @Test
    void bothFilesIsNotLegacy(@TempDir Path dir) throws Exception {
        assertFalse(LegacyJarDetector.isLegacyCandidate(jar(dir, "b.jar", "META-INF/mods.toml", "META-INF/neoforge.mods.toml")));
    }

    @Test
    void neitherIsNotLegacy(@TempDir Path dir) throws Exception {
        assertFalse(LegacyJarDetector.isLegacyCandidate(jar(dir, "c.jar", "foo.txt")));
    }

    @Test
    void readsModsToml(@TempDir Path dir) throws Exception {
        assertEquals("x = 1", LegacyJarDetector.readModsToml(jar(dir, "d.jar", "META-INF/mods.toml")));
        assertNull(LegacyJarDetector.readModsToml(jar(dir, "e.jar", "foo.txt")));
    }
}
