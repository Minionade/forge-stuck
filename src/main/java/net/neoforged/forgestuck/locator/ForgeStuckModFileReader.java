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
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Legacy-intercepting IModFileReader — supports both the JarContents-based SPI and the Path-based SPI.
 *
 * This implementation reads the legacy Forge mods.toml via the actual JarContents API shape used by NeoForge:
 * methods return Optional<T>, not the raw value directly. We unwrap the Optional before trying detection.
 */
public class ForgeStuckModFileReader implements IModFileReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(ForgeStuckModFileReader.class);

    @Override
    public IModFile read(JarContents contents, ModFileDiscoveryAttributes attrs) {
        try {
            String legacyToml = readJarText(contents, "META-INF/mods.toml");
            if (legacyToml == null || legacyToml.isBlank()) {
                LOGGER.debug("[Forge Stuck!!] JarContents-based reader: no META-INF/mods.toml found");
                return null;
            }

            boolean hasNeoToml = hasJarEntry(contents, "META-INF/neoforge.mods.toml");
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

    private static String readJarText(Object jarContents, String entryName) {
        try {
            Object result = invokeJarMethod(jarContents, "getInputStream", entryName);
            if (result == null) {
                return null;
            }

            if (result instanceof Optional<?> optional) {
                if (optional.isEmpty()) {
                    return null;
                }
                Object streamObj = optional.get();
                if (streamObj instanceof InputStream stream) {
                    return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                }
                return null;
            }

            if (result instanceof InputStream stream) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Throwable ignored) {
            // fall through to the legacy path below
        }

        try {
            Object entryResult = invokeJarMethod(jarContents, "getEntry", entryName);
            if (entryResult instanceof Optional<?> entryOptional && entryOptional.isPresent()) {
                Object entry = entryOptional.get();
                Object streamResult = invokeJarMethod(jarContents, "getInputStream", entry);
                if (streamResult instanceof Optional<?> streamOptional && streamOptional.isPresent()) {
                    Object streamObj = streamOptional.get();
                    if (streamObj instanceof InputStream stream) {
                        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }
            }
        } catch (Throwable ignored) {
            // ignore and return null below
        }

        // Final fallback: use the ModFileDiscoveryAttributes path if available
        try {
            if (jarContents instanceof JarContents) {
                return null;
            }
        } catch (Throwable ignored) {
            // ignore
        }
        return null;
    }

    private static boolean hasJarEntry(Object jarContents, String entryName) {
        try {
            Object result = invokeJarMethod(jarContents, "getEntry", entryName);
            if (result instanceof Optional<?> optional) {
                return optional.isPresent();
            }
            return result != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object invokeJarMethod(Object jarContents, String methodName, Object arg) {
        for (Method m : jarContents.getClass().getMethods()) {
            if (!m.getName().equals(methodName)) {
                continue;
            }
            Class<?>[] params = m.getParameterTypes();
            if (params.length != 1) {
                continue;
            }
            Class<?> target = params[0];
            if (arg instanceof String && String.class.equals(target)) {
                try {
                    return m.invoke(jarContents, arg);
                } catch (Throwable ignored) {
                    // continue checking other overloads
                }
            }
            if (arg != null && target.isAssignableFrom(arg.getClass())) {
                try {
                    return m.invoke(jarContents, arg);
                } catch (Throwable ignored) {
                    // continue checking other overloads
                }
            }
        }
        return null;
    }

    // Fallback entry point for SPI variants that pass a Path and locator
    public IModFile read(Path path, Object locator) {
        try {
            String filename = path.getFileName().toString();

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

                String legacyToml;
                try (InputStream is = jar.getInputStream(forgeToml)) {
                    legacyToml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }

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
     * Minimal synthesized IModFile proxy for legacy Forge JARs. The exact method surface is not fully known,
     * but its purpose is to satisfy NeoForge enough to accept the JAR as discovered and proceed with loading.
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
