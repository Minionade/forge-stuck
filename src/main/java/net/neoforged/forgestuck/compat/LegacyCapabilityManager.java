package net.neoforged.forgestuck.compat;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.ItemCapability;

public final class LegacyCapabilityManager {
    private static final ConcurrentMap<Class<?>, LegacyCapability<?>> CAPABILITIES = new ConcurrentHashMap<>();

    private LegacyCapabilityManager() {}

    @SuppressWarnings("unchecked")
    static <T> LegacyCapability<T> forType(Class<T> type) {
        return (LegacyCapability<T>) CAPABILITIES.computeIfAbsent(type, LegacyCapability::new);
    }

    static <T> LegacyCapability<T> forItemType(Class<T> type, ItemCapability<T, Void> itemCapability) {
        LegacyCapability<T> capability = forType(type);
        capability.bindItem(itemCapability);
        return capability;
    }

    static <T> LegacyCapability<T> forItemAndBlockType(Class<T> type, ItemCapability<T, Void> itemCapability,
            BlockCapability<T, Direction> blockCapability) {
        LegacyCapability<T> capability = forItemType(type, itemCapability);
        capability.bindBlock(blockCapability);
        return capability;
    }

    static <T> LegacyCapability<T> forBlockType(Class<T> type, BlockCapability<T, Direction> blockCapability) {
        LegacyCapability<T> capability = forType(type);
        capability.bindBlock(blockCapability);
        return capability;
    }

    public static <T> LegacyCapability<T> get(LegacyCapabilityToken<T> token) {
        return forType(token.type());
    }
}
