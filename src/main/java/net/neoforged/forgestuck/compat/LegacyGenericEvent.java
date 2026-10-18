package net.neoforged.forgestuck.compat;

import java.lang.reflect.Type;
import net.neoforged.bus.api.Event;

/** Compatibility base for Forge events whose listeners are filtered by a generic payload class. */
public class LegacyGenericEvent<T> extends Event implements LegacyIGenericEvent<T> {
    private final Class<T> type;

    public LegacyGenericEvent() {
        type = null;
    }

    public LegacyGenericEvent(Class<T> type) {
        this.type = type;
    }

    @Override
    public Type getGenericType() {
        return type;
    }
}
