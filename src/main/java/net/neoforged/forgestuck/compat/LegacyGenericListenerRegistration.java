package net.neoforged.forgestuck.compat;

import java.util.function.Consumer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;

/** Adapts Forge generic listeners to the event-bus typed listener API. */
public final class LegacyGenericListenerRegistration {
    private LegacyGenericListenerRegistration() {}

    public static <T extends Event> void addGenericListener(IEventBus bus, Class<T> eventType,
            Consumer<T> listener) {
        bus.addListener(eventType, listener);
    }
}
