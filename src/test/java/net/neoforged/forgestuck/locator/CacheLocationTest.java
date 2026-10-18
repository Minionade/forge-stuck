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

    @Test
    void staleGeneratedCacheFilesArePrunedWithoutRemovingActiveOrUnknownFiles(@TempDir Path game) throws Exception {
        Path cache = Files.createDirectories(ForgeStuckCandidateLocator.cacheDir(game));
        Path active = Files.write(cache.resolve("a".repeat(64) + "-t0-123456789abc.jar"), new byte[] {1});
        Path stale = Files.write(cache.resolve("b".repeat(64) + "-t33-abcdefabcdef.jar"), new byte[] {1});
        Path legacyStale = Files.write(cache.resolve("c".repeat(64) + "-t32.jar"), new byte[] {1});
        Path staleCompat = Files.write(cache.resolve("forgestuck-compat-1234567890abcdef.jar"), new byte[] {1});
        Path unrelated = Files.write(cache.resolve("user-notes.txt"), new byte[] {1});
        Path temp = Files.write(cache.resolve("forgestuck-abandoned.tmp"), new byte[] {1});

        ForgeStuckCandidateLocator.pruneStaleCacheFiles(cache, java.util.Set.of(active));

        assertTrue(Files.exists(active));
        assertFalse(Files.exists(stale));
        assertFalse(Files.exists(legacyStale));
        assertFalse(Files.exists(staleCompat));
        assertFalse(Files.exists(temp));
        assertTrue(Files.exists(unrelated));
    }
}
