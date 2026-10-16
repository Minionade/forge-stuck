package net.neoforged.forgestuck.shim;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Capability bridge: safe no-op bridge for the Forge capability registration model.
 * Expand as the runtime compatibility surface requires.
 */
public final class LegacyCapabilityBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyCapabilityBridge.class);

    private LegacyCapabilityBridge() {
    }

    public static void register(Object event) {
        LOGGER.info("[Forge Stuck!!] Legacy capability bridge ready (passive hook)");
    }
}
