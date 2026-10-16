package net.neoforged.forgestuck.shim;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Network bridge: handles payload registration in a passive manner until more specific legacy packet
 * compatibility is required.
 */
public final class LegacyNetworkBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyNetworkBridge.class);

    private LegacyNetworkBridge() {
    }

    public static void registerPayloadHandlers(Object event) {
        LOGGER.info("[Forge Stuck!!] Legacy payload bridge ready (passive hook)");
    }
}
