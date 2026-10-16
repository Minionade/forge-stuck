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
import java.util.Locale;

/**
 * Legacy-intercepting IModFileReader.
 *
 * If a JAR appears to be a 1.20.1 Forge mod (has META-INF/mods.toml but lacks
 * META-INF/neoforge.mods.toml), this reader synthesizes a minimal in-memory
 * IModFile and returns it so NeoForge will accept and load the JAR without
 * requiring the user to modify files on disk.
 *
 * The synthesized IModFile is implemented via a dynamic proxy to avoid a hard
 * compile-time dependency on NeoForge internal implementation classes. The
 * proxy implements the IModFile SPI and returns conservative metadata values
 * derived from the legacy mods.toml.
 */
public class ForgeStuckModFileReader implements IModFileReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(ForgeStuckModFileReader.class);

    @Override
    public IModFile read(JarContents contents, ModFileDiscoveryAttributes attrs) {
        try {
            LegacyModDescriptor descriptor = LegacyModDiscovery.detectLegacyMod(contents);
            if (descriptor == null) {
                return null;
            }

            LOGGER.info("[Forge Stuck!!] Intercepting legacy Forge mod: {} ({})", descriptor.displayName(), descriptor.modId());

            // Synthesize a minimal neoforge TOML content for this legacy mod
            String synthesizedToml = synthesizeNeoForgeToml(descriptor);

            // Create a dynamic proxy that implements IModFile and returns conservative values.
            ClassLoader cl = IModFile.class.getClassLoader();
            InvocationHandler handler = new LegacyModFileInvocationHandler(descriptor, synthesizedToml);
            Object proxy = Proxy.newProxyInstance(cl, new Class[]{IModFile.class}, handler);
            return (IModFile) proxy;
        } catch (Throwable t) {
            LOGGER.debug("[Forge Stuck!!] Failed to synthesize IModFile for legacy JAR (falling back)", t);
            return null;
        }
    }

    private static String synthesizeNeoForgeToml(LegacyModDescriptor desc) {
        // Minimal neoforge mods toml that provides an id and display name. Keep conservative.
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

    /**
     * InvocationHandler for the synthesized IModFile proxy. Returns conservative values
     * for commonly-accessed methods. Any unrecognized method returns null or a
     * reasonable default to avoid breaking NeoForge.
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

            // Basic Object methods
            if ("toString".equals(name)) {
                return "LegacyIModFile[synthesized:" + desc.modId() + "]";
            }
            if ("hashcode".equals(name)) {
                return desc.modId().hashCode();
            }
            if ("equals".equals(name)) {
                return proxy == args[0];
            }

            // Common IModFile-like accessors (best-effort; unknown exact SPI names).
            // We attempt to support a few plausible method names used by different NeoForge versions.
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
                    // Return an InputStream if the caller expects to read synthesized toml content
                    return new ByteArrayInputStream(toml.getBytes(StandardCharsets.UTF_8));
                case "getinputstream":
                case "openstream":
                    // Some SPI variants ask to open the mods toml stream; return the synthesized toml
                    return new ByteArrayInputStream(toml.getBytes(StandardCharsets.UTF_8));
                case "getlocation":
                case "location":
                case "getpath":
                    // Unknown: return null so NeoForge falls back to other heuristics if needed
                    return null;
                default:
                    // If the method has a primitive return type, return a safe default
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
