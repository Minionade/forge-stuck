package net.neoforged.forgestuck.transformer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runtime bootstrap for the compatibility transformer.
 *
 * For now, the class remains safe and no-op unless the ASM runtime is present. The hook is intentionally
 * conservative so it can be expanded later without destabilizing the modern NeoForge code path.
 */
public final class ForgeClassTransformer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ForgeClassTransformer.class);

    private ForgeClassTransformer() {
    }

    public static void init() {
        try {
            Class.forName("org.objectweb.asm.ClassReader");
            Class.forName("org.objectweb.asm.ClassWriter");
            LOGGER.info("[Forge Stuck!!] ASM transformer runtime detected and bootstrapped");
        } catch (Throwable t) {
            LOGGER.info("[Forge Stuck!!] ASM transformer runtime unavailable; running in passive compatibility mode");
        }
    }

    public static byte[] rewriteIfNeeded(byte[] classBytes, String className) {
        if (classBytes == null || classBytes.length == 0) {
            return classBytes;
        }

        try {
            Class.forName("org.objectweb.asm.ClassReader");
            Class.forName("org.objectweb.asm.ClassWriter");
            LOGGER.debug("[Forge Stuck!!] Transformer hook invoked for {}", className);
        } catch (Throwable ignored) {
            // Passive mode: keep the class untouched until ASM is present.
        }

        return classBytes;
    }
}
