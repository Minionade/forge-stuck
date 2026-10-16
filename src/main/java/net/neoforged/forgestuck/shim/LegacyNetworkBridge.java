package net.neoforged.forgestuck.shim;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal network bridge stub. This exists so registration methods are typed correctly without forcing a
 * complex, non-portable packet rewrite layer.
 */
public final class LegacyNetworkBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyNetworkBridge.class);

    private LegacyNetworkBridge() {
    }

    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        LOGGER.info("[Forge Stuck!!] Legacy payload bridge initialized (safe no-op)");
    }
}
