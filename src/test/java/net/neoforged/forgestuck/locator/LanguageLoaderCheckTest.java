package net.neoforged.forgestuck.locator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LanguageLoaderCheckTest {
    @TempDir
    Path dir;

    private Path jar(String loader, String range) throws Exception {
        Path p = dir.resolve("scalamod.jar");
        try (OutputStream o = Files.newOutputStream(p); ZipOutputStream z = new ZipOutputStream(o)) {
            z.putNextEntry(new ZipEntry("META-INF/mods.toml"));
            z.write(("modLoader=\"" + loader + "\"\nloaderVersion=\"" + range + "\"\nlicense=\"x\"\n"
                    + "[[mods]]\nmodId=\"bdlib\"\n").getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        }
        return p;
    }

    @Test
    void missingLoaderIsReportedWithInstallHint() throws Exception {
        var p = LanguageLoaderCheck.check(jar("kotori_scala", "[2,)"), Map.of("javafml", "4"));
        assertNotNull(p);
        assertTrue(p.loaderMissing());
        assertTrue(p.message().contains("bdlib") && p.message().contains("kotori_scala")
                && p.message().contains("[2,)") && p.message().contains("ScalableCatsForce"));
    }

    @Test
    void versionMismatchAndSatisfied() throws Exception {
        var bad = LanguageLoaderCheck.check(jar("kotori_scala", "[3,)"), Map.of("kotori_scala", "2.1"));
        assertNotNull(bad);
        assertEquals("2.1", bad.installedVersion());
        assertNull(LanguageLoaderCheck.check(jar("kotori_scala", "[2,)"), Map.of("kotori_scala", "2.1")));
        assertNull(LanguageLoaderCheck.check(jar("javafml", "[47,)"), Map.of()));
    }
}