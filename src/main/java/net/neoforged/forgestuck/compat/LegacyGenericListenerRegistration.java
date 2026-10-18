package net.neoforged.forgestuck.compat;

import java.util.function.Consumer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Recreates Forge's generic payload filter around NeoForge's typed event listeners. */
public final class LegacyGenericListenerRegistration {
    private LegacyGenericListenerRegistration() {}

    public static void addGenericListener(IEventBus bus, Class<?> genericClassFilter, Consumer<?> listener,
            Class<?> eventType) {
        addGenericListener(bus, genericClassFilter, null, listener, eventType);
    }

    public static void addGenericListener(IEventBus bus, Class<?> genericClassFilter, EventPriority priority,
            Consumer<?> listener, Class<?> eventType) {
        java.util.Objects.requireNonNull(bus, "event bus");
        java.util.Objects.requireNonNull(genericClassFilter, "generic class filter");
        java.util.Objects.requireNonNull(listener, "listener");
        Class<? extends Event> typedEvent = eventType.asSubclass(Event.class);
        register(bus, genericClassFilter, priority, listener, typedEvent);
    }

    public static void addGenericListener(IEventBus bus, Class<?> genericClassFilter, Consumer<?> listener) {
        throw new UnsupportedOperationException("Cannot determine the Forge generic event class for listener "
                + listener.getClass().getName() + " filtered by " + genericClassFilter.getName()
                + "; use a directly declared event lambda so Forge Stuck can translate its event type");
    }

    private static <T extends Event> void register(IEventBus bus, Class<?> genericClassFilter, EventPriority priority,
            Consumer<?> listener, Class<T> eventType) {
        @SuppressWarnings("unchecked")
        Consumer<T> callback = (Consumer<T>) listener;
        Consumer<T> filtered = event -> {
            if (matches(genericClassFilter, event)) {
                callback.accept(event);
            }
        };
        if (priority == null) {
            bus.addListener(eventType, filtered);
        } else {
            bus.addListener(priority, eventType, filtered);
        }
    }

    private static boolean matches(Class<?> filter, Event event) {
        if (event instanceof LegacyIGenericEvent<?> generic
                && generic.getGenericType() instanceof Class<?> payloadType) {
            return filter.isAssignableFrom(payloadType);
        }
        if (event instanceof LevelEvent levelEvent) {
            return filter.isInstance(levelEvent.getLevel());
        }
        if (event instanceof EntityEvent entityEvent) {
            return filter.isInstance(entityEvent.getEntity());
        }
        if (event instanceof LegacyAttachCapabilitiesEvent<?> attachEvent) {
            return filter.isInstance(attachEvent.getObject());
        }
        return filter.isInstance(event);
    }
}
