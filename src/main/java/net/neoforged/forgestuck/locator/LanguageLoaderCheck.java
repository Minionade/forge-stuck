package net.neoforged.forgestuck.locator;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;

/**
 * Detects legacy jars declaring a non-javafml language loader that no installed FML language loader satisfies. The
 * converted metadata keeps modLoader/loaderVersion unchanged, so FML itself will also refuse the mod; this check
 * only gives an early, actionable message. It never skips or alters the mod.
 */
public final class LanguageLoaderCheck {
    private LanguageLoaderCheck() {}

    public record Problem(String jarName, List<String> modIds, String loader, String range, boolean loaderMissing,
            String installedVersion) {
        public String message() {
            String what = loaderMissing ? "no language loader with that id is installed"
                    : "installed loader version " + installedVersion + " does not satisfy the range";
            return "Legacy jar " + jarName + " (mod(s) " + modIds + ") requires language loader '" + loader
                    + "' version " + range + ", but " + what + ". Forge Stuck will not fall back to javafml or skip the"
                    + " mod. " + hint(loader);
        }

        private static String hint(String loader) {
            if (loader.equals("kotori_scala")) {
                return "Install the NeoForge 1.21.1 build of ScalableCatsForce (the 'with-library' jar) in mods/.";
            }
            return "Install a NeoForge 1.21.1 build of the '" + loader + "' language provider in mods/.";
        }
    }

    /** @param available installed language loader name -> version */
    public static Problem check(Path jar, Map<String, String> available) throws IOException {
        String toml = readModsToml(jar);
        if (toml == null) return null;
        Config source = new TomlParser().parse(toml);
        String loader = source.getOrElse("modLoader", "javafml");
        if ("javafml".equals(loader)) return null;
        String range = source.getOrElse("loaderVersion", "[1,)");
        List<String> ids = new ArrayList<>();
        for (UnmodifiableConfig m : source.getOrElse("mods", List.<UnmodifiableConfig>of())) {
            Object id = m.get("modId");
            if (id != null) ids.add(id.toString());
        }
        String name = jar.getFileName().toString();
        String installed = available.get(loader);
        if (installed == null) return new Problem(name, ids, loader, range, true, null);
        try {
            ArtifactVersion v = new DefaultArtifactVersion(installed);
            if (!VersionRange.createFromVersionSpec(range).containsVersion(v)) {
                return new Problem(name, ids, loader, range, false, installed);
            }
        } catch (InvalidVersionSpecificationException e) {
            return new Problem(name, ids, loader, range, false, installed);
        }
        return null;
    }

    private static String readModsToml(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry e = zip.getEntry("META-INF/mods.toml");
            if (e == null) return null;
            try (InputStream in = zip.getInputStream(e)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }
}