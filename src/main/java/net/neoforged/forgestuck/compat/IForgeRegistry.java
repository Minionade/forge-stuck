package net.neoforged.forgestuck.compat;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * Runtime stand-in for the commonly used read-only subset of Forge 1.20.1's {@code IForgeRegistry}, backed by the
 * vanilla registry of the same key. It stays an interface because legacy bytecode invokes it with
 * {@code invokeinterface}.
 */
public interface IForgeRegistry<V> {
    ResourceKey<? extends net.minecraft.core.Registry<V>> getRegistryKey();

    ResourceLocation getRegistryName();

    boolean containsKey(ResourceLocation key);

    boolean containsValue(V value);

    V getValue(ResourceLocation key);

    ResourceLocation getKey(V value);

    Optional<ResourceKey<V>> getResourceKey(V value);

    Set<ResourceLocation> getKeys();

    Collection<V> getValues();

    Set<Map.Entry<ResourceKey<V>, V>> getEntries();
}
