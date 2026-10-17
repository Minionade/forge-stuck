package net.neoforged.forgestuck.locator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.neoforged.forgestuck.transformer.jar.JarTransformer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CacheLocationTest {
    @Test
    void cacheLivesUnderModsAndIsNotScanned(@TempDir Path game) throws Exception {
        Path mods = Files.createDirectories(game.resolve("mods"));
        Path cache = ForgeStuckCandidateLocator.cacheDir(game);
        assertEquals(mods.resolve(".forgestuck"), cache);

        Path top = Files.write(mods.resolve("top.jar"), new byte[] {1});
        Path src = Files.write(mods.resolve("src.jar"), new byte[] {1});
        Path cached = Files.write(Files.createDirectories(cache).resolve("abc-t12.jar"), new byte[] {1});

        try (Stream<Path> files = Files.list(mods)) {
            List<Path> scanned = files.filter(ForgeStuckCandidateLocator::isScannable).toList();
            assertTrue(scanned.contains(top) && scanned.contains(src));
            assertFalse(scanned.contains(cache));
            assertFalse(scanned.contains(cached));
        }
    }

    @Test
    void tempFilesAreCreatedInsideCacheDir(@TempDir Path game) throws Exception {
        Path jar = game.resolve("a.jar");
        try (var out = new java.util.jar.JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new java.util.zip.ZipEntry("META-INF/mods.toml"));
            out.write("modLoader=\"javafml\"\n[[mods]]\nmodId=\"cache_mod\"\n".getBytes());
            out.closeEntry();
        }
        Path cache = ForgeStuckCandidateLocator.cacheDir(game);
        Path out = new JarTransformer(List.of()).transformCached(jar, cache).output();
        assertEquals(cache, out.getParent());
        try (Stream<Path> files = Files.list(cache)) {
            assertEquals(List.of(out), files.toList(), "no leftover temp files");
        }
    }
}
