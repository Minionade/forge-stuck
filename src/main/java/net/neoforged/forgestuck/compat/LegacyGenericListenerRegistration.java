package net.neoforged.forgestuck.compat;

import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Forge generic listeners filter GenericEvent instances by payload type, which NeoForge no longer supports. */
public final class LegacyGenericListenerRegistration {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgestuck");

    private LegacyGenericListenerRegistration() {}

    public static void addGenericListener(Object bus, Class<?> genericClassFilter, Consumer<?> listener) {
        java.util.Objects.requireNonNull(bus, "event bus");
        java.util.Objects.requireNonNull(genericClassFilter, "generic class filter");
        java.util.Objects.requireNonNull(listener, "listener");
        LOGGER.debug("[Forge Stuck!!] Skipping unsupported Forge generic listener for {}",
                genericClassFilter.getName());
    }
}
