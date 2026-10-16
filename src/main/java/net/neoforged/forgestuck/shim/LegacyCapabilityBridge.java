package net.neoforged.forgestuck.shim;

import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * No-op capability bridge stub. This can be expanded to adapt Forge capability registrations to NeoForge
 * without breaking the modern registry model.
 */
public final class LegacyCapabilityBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyCapabilityBridge.class);

    private LegacyCapabilityBridge() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        LOGGER.info("[Forge Stuck!!] Legacy capability bridge registered (safe no-op)");
    }
}
