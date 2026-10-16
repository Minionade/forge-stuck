package net.neoforged.forgestuck.transformer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A tiny runtime hook for future ASM-based remapping.
 *
 * For now, this is intentionally conservative: we only log and do no modification. That avoids breaking
 * already-modern NeoForge classes while still providing a stable integration point for future patches.
 */
public final class ForgeClassTransformer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ForgeClassTransformer.class);

    private ForgeClassTransformer() {
    }

    public static void init() {
        LOGGER.info("[Forge Stuck!!] Legacy class transformer initialized (safe no-op bootstrap) ");
    }
}
