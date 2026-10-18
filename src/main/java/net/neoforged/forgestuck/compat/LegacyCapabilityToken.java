package net.neoforged.forgestuck.compat;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public abstract class LegacyCapabilityToken<T> {
    private final Class<T> type;

    @SuppressWarnings("unchecked")
    protected LegacyCapabilityToken() {
        Type genericSuperclass = getClass().getGenericSuperclass();
        if (!(genericSuperclass instanceof ParameterizedType parameterized)
                || !(parameterized.getActualTypeArguments()[0] instanceof Class<?> capabilityType)) {
            throw new IllegalStateException("Legacy capability tokens must declare a concrete capability type");
        }
        type = (Class<T>) capabilityType;
    }

    Class<T> type() {
        return type;
    }
}
