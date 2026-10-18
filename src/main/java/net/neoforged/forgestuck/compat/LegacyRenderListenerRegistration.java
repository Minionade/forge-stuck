package net.neoforged.forgestuck.compat;

import java.util.function.Consumer;
import net.jodah.typetools.TypeResolver;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;

/** Explicit-class registrations from transformed mods; no eager client-class references on servers. */
public final class LegacyRenderListenerRegistration {
    private LegacyRenderListenerRegistration() {}
    public static <T extends Event> void addListener(IEventBus bus, Consumer<T> listener) {
        addListener(bus, EventPriority.NORMAL, false, listener);
    }
    public static <T extends Event> void addListener(IEventBus bus, EventPriority priority, Consumer<T> listener) {
        addListener(bus, priority, false, listener);
    }
    @SuppressWarnings("unchecked")
    public static <T extends Event> void addListener(IEventBus bus, EventPriority priority,
                                                    boolean receiveCanceled, Consumer<T> listener) {
        // Use the same type resolver as NeoForge Bus 8.0; preserve its behavior for all other types.
        Class<?> inferred = TypeResolver.resolveRawArgument(Consumer.class, listener.getClass());
        if (inferred.getName().equals("net.neoforged.neoforge.client.event.RenderLivingEvent")) {
            addListener(bus, priority, receiveCanceled, (Class<T>) inferred, listener);
        } else {
            bus.addListener(priority, receiveCanceled, listener);
        }
    }
    public static <T extends Event> void addListener(IEventBus bus, Class<T> type, Consumer<T> listener) {
        addListener(bus, EventPriority.NORMAL, false, type, listener);
    }
    public static <T extends Event> void addListener(IEventBus bus, EventPriority priority,
                                                    Class<T> type, Consumer<T> listener) {
        addListener(bus, priority, false, type, listener);
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T extends Event> void addListener(IEventBus bus, EventPriority priority, boolean receiveCanceled,
                                                    Class<T> type, Consumer<T> listener) {
        if (!type.getName().equals("net.neoforged.neoforge.client.event.RenderLivingEvent")) {
            bus.addListener(priority, receiveCanceled, type, listener);
            return;
        }
        try {
            Class<? extends Event> pre = Class.forName(type.getName() + "$Pre", false, type.getClassLoader()).asSubclass(Event.class);
            Class<? extends Event> post = Class.forName(type.getName() + "$Post", false, type.getClassLoader()).asSubclass(Event.class);
            bus.addListener(priority, receiveCanceled, (Class) pre, (Consumer) listener);
            bus.addListener(priority, receiveCanceled, (Class) post, (Consumer) listener);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Missing concrete RenderLivingEvent classes", e);
        }
    }
}
