package net.neoforged.forgestuck.locator;

import cpw.mods.jarhandling.JarContents;
import net.neoforged.forgestuck.compat.LegacyModDescriptor;
import net.neoforged.forgestuck.compat.LegacyModDiscovery;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileReader;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Legacy-intercepting IModFileReader — supports both the JarContents-based SPI and the Path-based SPI.
 *
 * This version improves detection for the JarContents variant by attempting multiple reflection-based
 * strategies to obtain the mods.toml contents: directly via JarContents input-stream helpers or by
 * falling back to opening the file path reported in ModFileDiscoveryAttributes.
 */
public class ForgeStuckModFileReader implements IModFileReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(ForgeStuckModFileReader.class);

    // Called by SPI variants that pass a JarContents object
    @Override
    public IModFile read(JarContents contents, ModFileDiscoveryAttributes attrs) {
        try {
            // Try several strategies to obtain the mods.toml content and to detect neoforge.mods.toml
            String legacyToml = null;
            boolean hasNeoToml = false;

            // Strategy A: JarContents has an input stream accessor taking a String
            try {
                Method getInput = contents.getClass().getMethod("getInputStream", String.class);
                try (InputStream is = (InputStream) getInput.invoke(contents, "META-INF/mods.toml")) {
                    if (is != null) {
                        legacyToml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    }
                } catch (Throwable ignored) {}

                try {
                    Method getInput2 = contents.getClass().getMethod("getInputStream", String.class);
                    try (InputStream is = (InputStream) getInput2.invoke(contents, "META-INF/neoforge.mods.toml")) {
                        hasNeoToml = is != null;
                    } catch (Throwable ignored) {}
                } catch (Throwable ignored) {}
            } catch (NoSuchMethodException ignored) {
                // not available, continue
            }

            // Strategy B: JarContents.getEntry(String) -> JarEntry-like, then JarContents.getInputStream(entry)
            if (legacyToml == null) {
                try {
                    Method getEntry = contents.getClass().getMethod("getEntry", String.class);
                    Object entry = getEntry.invoke(contents, "META-INF/mods.toml");
                    if (entry != null) {
                        // try getInputStream(JarEntry)
                        try {
                            Method getInputByEntry = contents.getClass().getMethod("getInputStream", entry.getClass());
                            try (InputStream is = (InputStream) getInputByEntry.invoke(contents, entry)) {
                                if (is != null) {
                                    legacyToml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                                }
                            } catch (Throwable ignored) {}
                        } catch (NoSuchMethodException ignored) {
                            // fallback below
                        }
                    }

                    // check neo toml presence similarly
                    try {
                        Object neoEntry = getEntry.invoke(contents, "META-INF/neoforge.mods.toml");
                        hasNeoToml = neoEntry != null;
                    } catch (Throwable ignored) {}
                } catch (NoSuchMethodException ignored) {
                    // not available, continue
                }
            }

            // Strategy C: fall back to using the attrs to find an on-disk path and open the JarFile directly
            if (legacyToml == null && attrs != null) {
                try {
                    Path path = discoverPathFromAttributes(attrs);
                    if (path != null) {
                        try (JarFile jar = new JarFile(path.toFile())) {
                            JarEntry forgeToml = jar.getJarEntry("META-INF/mods.toml");
                            JarEntry neoToml = jar.getJarEntry("META-INF/neoforge.mods.toml");
                            if (forgeToml != null) {
                                try (InputStream is = jar.getInputStream(forgeToml)) {
                                    legacyToml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                                }
                            }
                            hasNeoToml = (neoToml != null);
                        }
                    }
                } catch (Throwable ignored) {
                    // ignore and continue
                }
            }

            if (legacyToml == null) {
                LOGGER.debug("[Forge Stuck!!] JarContents-based reader: not a legacy candidate (or detection failed)");
                return null;
            }

            if (hasNeoToml) {
                LOGGER.debug("[Forge Stuck!!] JarContents-based reader: neoforge.mods.toml present, skipping");
                return null;
            }

            String modId = LegacyModDiscovery.matchModIdFromToml(legacyToml);
            String display = LegacyModDiscovery.matchDisplayNameFromToml(legacyToml, modId);
            LegacyModDescriptor descriptor = LegacyModDescriptor.builder()
                .modId(modId)
                .displayName(display)
                .entryName("META-INF/mods.toml")
                .fileName(modId)
                .build();

            LOGGER.info("[Forge Stuck!!] Intercepting legacy Forge mod (JarContents): {} ({})", descriptor.displayName(), descriptor.modId());
            return synthesizeProxy(descriptor);
        } catch (Throwable t) {
            LOGGER.debug("[Forge Stuck!!] JarContents-based detection failed safely", t);
            return null;
        }
    }

    private Path discoverPathFromAttributes(ModFileDiscoveryAttributes attrs) {
        try {
            // Try common method names that return Path/File/String
            for (Method m : attrs.getClass().getMethods()) {
                String n = m.getName().toLowerCase(Locale.ROOT);
                if (n.contains("path") || n.contains("file") || n.contains("location") || n.contains("source")) {
                    Class<?> ret = m.getReturnType();
                    if (Path.class.isAssignableFrom(ret)) {
                        Object o = m.invoke(attrs);
                        if (o != null) return (Path) o;
                    }
                    if (java.io.File.class.isAssignableFrom(ret)) {
                        Object o = m.invoke(attrs);
                        if (o != null) return ((java.io.File) o).toPath();
                    }
                    if (String.class.isAssignableFrom(ret)) {
                        Object o = m.invoke(attrs);
                        if (o != null) return Path.of((String) o);
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    // Fallback entry point for SPI variants that pass a Path and locator
    public IModFile read(Path path, Object locator) {
        try {
            String filename = path.getFileName().toString();

            // Skip non-JAR files and our own jar filename variants
            if (!filename.toLowerCase(Locale.ROOT).endsWith(".jar") || filename.toLowerCase(Locale.ROOT).startsWith("forgestuck")) {
                LOGGER.debug("[Forge Stuck!!] Path-based reader: skipping non-jar or self jar: {}", filename);
                return null;
            }

            try (JarFile jar = new JarFile(path.toFile())) {
                JarEntry neoToml = jar.getJarEntry("META-INF/neoforge.mods.toml");
                JarEntry forgeToml = jar.getJarEntry("META-INF/mods.toml");

                if (forgeToml == null) {
                    LOGGER.debug("[Forge Stuck!!] Path-based reader: no META-INF/mods.toml in {}", filename);
                    return null;
                }

                if (neoToml != null) {
                    LOGGER.debug("[Forge Stuck!!] Path-based reader: neoforge.mods.toml present, not a legacy jar: {}", filename);
                    return null;
                }

                // Read mods.toml content
                String legacyToml;
                try (InputStream is = jar.getInputStream(forgeToml)) {
                    legacyToml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }

                // Basic extraction heuristics (fallback to filename-based id)
                String modId = extractModIdFromToml(legacyToml, filename);
                String display = extractDisplayNameFromToml(legacyToml, modId);

                LegacyModDescriptor descriptor = LegacyModDescriptor.builder()
                    .modId(modId)
                    .displayName(display)
                    .fileName(filename)
                    .entryName("META-INF/mods.toml")
                    .build();

                LOGGER.info("[Forge Stuck!!] Intercepting legacy Forge mod (Path): {} ({})", descriptor.displayName(), descriptor.modId());
                return synthesizeProxy(descriptor);
            }
        } catch (Throwable t) {
            LOGGER.debug("[Forge Stuck!!] Path-based detection failed safely", t);
            return null;
        }
    }

    private IModFile synthesizeProxy(LegacyModDescriptor descriptor) {
        try {
            String synthesizedToml = synthesizeNeoForgeToml(descriptor);
            ClassLoader cl = IModFile.class.getClassLoader();
            InvocationHandler handler = new LegacyModFileInvocationHandler(descriptor, synthesizedToml);
            Object proxy = Proxy.newProxyInstance(cl, new Class[]{IModFile.class}, handler);
            return (IModFile) proxy;
        } catch (Throwable t) {
            LOGGER.debug("[Forge Stuck!!] Failed to create synthesized IModFile proxy", t);
            return null;
        }
    }

    private static String synthesizeNeoForgeToml(LegacyModDescriptor desc) {
        String id = safeTomlString(desc.modId());
        String name = safeTomlString(desc.displayName());
        StringBuilder sb = new StringBuilder();
        sb.append("modLoader = \"neoforge\"\n");
        sb.append("loaderVersionRange = \"[4,)\"\n");
        sb.append("\n");
        sb.append("[[mods]]\n");
        sb.append("modId = \"").append(id).append("\"\n");
        sb.append("displayName = \"").append(name).append("\"\n");
        sb.append("version = \"0.0.0-forgestuck\"\n");
        return sb.toString();
    }

    private static String safeTomlString(String s) {
        if (s == null) return "unknown";
        return s.replace("\"", "'").replace("\n", " ").trim();
    }

    private static String extractModIdFromToml(String toml, String filename) {
        if (toml == null) return filename.replaceAll("\\.jar$", "");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^modId\\s*=\\s*\"?([A-Za-z0-9_.-]+)\"?").matcher(toml);
        if (m.find()) return m.group(1);
        return filename.replaceAll("\\.jar$", "");
    }

    private static String extractDisplayNameFromToml(String toml, String fallback) {
        if (toml == null) return fallback;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^displayName\\s*=\\s*\"?([^\\n\\r\"]+)\"?").matcher(toml);
        if (m.find()) return m.group(1);
        return fallback;
    }

    /**
     * InvocationHandler and proxy generation copied from earlier implementation
     */
    private static final class LegacyModFileInvocationHandler implements InvocationHandler {
        private final LegacyModDescriptor desc;
        private final String toml;

        LegacyModFileInvocationHandler(LegacyModDescriptor desc, String toml) {
            this.desc = desc;
            this.toml = toml;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName().toLowerCase(Locale.ROOT);

            if ("toString".equals(name)) {
                return "LegacyIModFile[synthesized:" + desc.modId() + "]";
            }
            if ("hashcode".equals(name)) {
                return desc.modId().hashCode();
            }
            if ("equals".equals(name)) {
                return proxy == args[0];
            }

            switch (name) {
                case "getid":
                case "id":
                case "getmodid":
                    return desc.modId();
                case "getname":
                case "getdisplayname":
                case "displayname":
                    return desc.displayName();
                case "getmetadata":
                case "metadata":
                case "gettoml":
                case "toml":
                    return new ByteArrayInputStream(toml.getBytes(StandardCharsets.UTF_8));
                case "getinputstream":
                case "openstream":
                    return new ByteArrayInputStream(toml.getBytes(StandardCharsets.UTF_8));
                case "getlocation":
                case "location":
                case "getpath":
                    return null;
                default:
                    Class<?> ret = method.getReturnType();
                    if (ret == boolean.class) return false;
                    if (ret == int.class) return 0;
                    if (ret == long.class) return 0L;
                    if (ret == float.class) return 0f;
                    if (ret == double.class) return 0d;
                    return null;
            }
        }
    }
}
