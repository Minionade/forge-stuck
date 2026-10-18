package net.neoforged.forgestuck.compat;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class LegacyCapabilityManager {
    private static final ConcurrentMap<Class<?>, LegacyCapability<?>> CAPABILITIES = new ConcurrentHashMap<>();

    private LegacyCapabilityManager() {}

    @SuppressWarnings("unchecked")
    static <T> LegacyCapability<T> forType(Class<T> type) {
        return (LegacyCapability<T>) CAPABILITIES.computeIfAbsent(type, LegacyCapability::new);
    }

    public static <T> LegacyCapability<T> get(LegacyCapabilityToken<T> token) {
        return forType(token.type());
    }
}
