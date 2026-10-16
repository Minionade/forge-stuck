package net.neoforged.forgestuck.transformer.jar;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

/**
 * Rewrites a legacy Forge 1.20.1 jar into a jar the NeoForge loader accepts.
 *
 * <ul>
 *   <li>copies all entries</li>
 *   <li>drops signature files (META-INF/*.SF, *.RSA, *.DSA) since the content is modified</li>
 *   <li>synthesizes META-INF/neoforge.mods.toml from META-INF/mods.toml</li>
 *   <li>passes every .class entry through {@link #transformClassIfNeeded(byte[], String)}</li>
 * </ul>
 *
 * Limitations: the generated neoforge.mods.toml is deliberately conservative (modLoader "javafml",
 * loaderVersion "[2,)", and one [[mods]] block per source mod with modId/displayName/version only).
 * Dependencies, mixins, access transformers and other sections are NOT converted, and no remapping of
 * net.minecraftforge.* / Minecraft 1.20.1 references is performed.
 */
public class JarTransformer {
    /** Bump to invalidate all cached transformed jars when the transformation logic changes. */
    public static final String TRANSFORMER_VERSION = "1";

    private static final String MODS_TOML = "META-INF/mods.toml";
    private static final String NEO_MODS_TOML = "META-INF/neoforge.mods.toml";
    private static final Pattern MODS_BLOCK = Pattern.compile("(?m)^\\s*\\[\\[\\s*mods\\s*\\]\\]\\s*$");
    private static final Pattern ANY_TABLE = Pattern.compile("(?m)^\\s*\\[");
    private static final Pattern STRING_VALUE = Pattern.compile("(?m)^\\s*%s\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')");

    public void transformTo(Path sourceJar, Path outJar) throws IOException {
        Path parent = outJar.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path tmp = Files.createTempFile(parent, "forgestuck-", ".tmp");
        try {
            try (JarFile jar = new JarFile(sourceJar.toFile());
                 JarOutputStream out = new JarOutputStream(Files.newOutputStream(tmp))) {
                boolean hasNeoToml = jar.getEntry(NEO_MODS_TOML) != null;
                JarEntry legacyToml = jar.getJarEntry(MODS_TOML);

                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (isSignatureFile(name)) continue;

                    ZipEntry copy = new ZipEntry(name);
                    out.putNextEntry(copy);
                    if (!entry.isDirectory()) {
                        try (InputStream in = jar.getInputStream(entry)) {
                            byte[] data = in.readAllBytes();
                            if (name.endsWith(".class")) data = transformClassIfNeeded(data, name);
                            out.write(data);
                        }
                    }
                    out.closeEntry();
                }

                if (!hasNeoToml && legacyToml != null) {
                    String legacy;
                    try (InputStream in = jar.getInputStream(legacyToml)) {
                        legacy = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    }
                    out.putNextEntry(new ZipEntry(NEO_MODS_TOML));
                    out.write(convertModsToml(legacy, sourceJar.getFileName().toString()).getBytes(StandardCharsets.UTF_8));
                    out.closeEntry();
                }
            }
            Files.move(tmp, outJar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** Hook for class-byte transformations (ASM). Currently a no-op. */
    protected byte[] transformClassIfNeeded(byte[] classBytes, String entryName) {
        return classBytes;
    }

    static boolean isSignatureFile(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        return upper.startsWith("META-INF/") && upper.indexOf('/', 9) < 0
            && (upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA"));
    }

    static String convertModsToml(String legacy, String jarName) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Synthesized by Forge Stuck!! from META-INF/mods.toml (conservative conversion)\n");
        sb.append("modLoader = \"javafml\"\n");
        sb.append("loaderVersion = \"[2,)\"\n");
        String license = value(legacy, "license");
        sb.append("license = \"").append(escape(license != null ? license : "UNKNOWN")).append("\"\n");

        Matcher m = MODS_BLOCK.matcher(legacy);
        boolean any = false;
        while (m.find()) {
            int start = m.end();
            Matcher next = ANY_TABLE.matcher(legacy);
            int end = next.find(start) ? next.start() : legacy.length();
            appendMod(sb, legacy.substring(start, end), jarName);
            any = true;
        }
        if (!any) appendMod(sb, "", jarName);
        return sb.toString();
    }

    private static void appendMod(StringBuilder sb, String block, String jarName) {
        String fallbackId = jarName.replaceAll("(?i)\\.jar$", "").replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase(Locale.ROOT);
        String modId = value(block, "modId");
        if (modId == null) modId = fallbackId;
        String displayName = value(block, "displayName");
        String version = value(block, "version");
        // "${file.jarVersion}" placeholders are resolved from the manifest by the loader; keep as-is.
        sb.append("\n[[mods]]\n");
        sb.append("modId = \"").append(escape(modId)).append("\"\n");
        sb.append("displayName = \"").append(escape(displayName != null ? displayName : modId)).append("\"\n");
        sb.append("version = \"").append(escape(version != null ? version : "1.0.0")).append("\"\n");
    }

    private static String value(String text, String key) {
        Matcher m = Pattern.compile(String.format(STRING_VALUE.pattern(), Pattern.quote(key))).matcher(text);
        if (!m.find()) return null;
        return m.group(1) != null ? m.group(1) : m.group(2);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
