package net.neoforged.forgestuck.compat;

import java.lang.reflect.Type;

/** Generic-event payload type contract retained for transformed Forge event subclasses. */
public interface LegacyIGenericEvent<T> {
    Type getGenericType();
}
