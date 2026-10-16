package net.neoforged.forgestuck.locator;

import net.neoforged.forgestuck.transformer.jar.JarTransformer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ForgeStuckCandidateLocatorTest {
    static void jar(Path p, String... entries) throws IOException {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(p))) {
            for (String e : entries) {
                out.putNextEntry(new JarEntry(e));
                out.write("modLoader=\"javafml\"\n[[mods]]\nmodId=\"m\"\n".getBytes());
                out.closeEntry();
            }
        }
    }

    @Test
    void predicate(@TempDir Path dir) throws Exception {
        jar(dir.resolve("legacy.jar"), "META-INF/mods.toml");
        jar(dir.resolve("neo.jar"), "META-INF/neoforge.mods.toml");
        jar(dir.resolve("both.jar"), "META-INF/mods.toml", "META-INF/neoforge.mods.toml");
        assertTrue(ForgeStuckCandidateLocator.isLegacyForgeJar(dir.resolve("legacy.jar")));
        assertFalse(ForgeStuckCandidateLocator.isLegacyForgeJar(dir.resolve("neo.jar")));
        assertFalse(ForgeStuckCandidateLocator.isLegacyForgeJar(dir.resolve("both.jar")));
    }

    @Test
    void scanCreatesAndReusesCache(@TempDir Path dir) throws Exception {
        Path mods = dir.resolve("mods");
        Path cache = dir.resolve(".forgestuck/cache");
        Files.createDirectories(mods);
        jar(mods.resolve("legacy.jar"), "META-INF/mods.toml");
        jar(mods.resolve("neo.jar"), "META-INF/neoforge.mods.toml");
        jar(mods.resolve("broken.jar"), "META-INF/mods.toml");
        Files.writeString(mods.resolve("broken.jar"), "not a zip");

        ForgeStuckCandidateLocator locator = new ForgeStuckCandidateLocator();
        List<Path> first = locator.scanMods(mods, cache);
        assertEquals(1, first.size());
        Path cached = first.get(0);
        assertTrue(Files.isRegularFile(cached));
        assertEquals(cache, cached.getParent());
        assertTrue(cached.getFileName().toString().endsWith("-v" + JarTransformer.TRANSFORMER_VERSION + ".jar"));

        long modified = Files.getLastModifiedTime(cached).toMillis();
        Thread.sleep(20);
        assertEquals(first, locator.scanMods(mods, cache));
        assertEquals(modified, Files.getLastModifiedTime(cached).toMillis());
    }
}
