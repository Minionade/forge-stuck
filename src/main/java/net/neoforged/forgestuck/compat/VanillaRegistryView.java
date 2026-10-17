package net.neoforged.forgestuck.compat;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/** {@link IForgeRegistry} view over a vanilla registry that is resolved lazily from its registry key. */
final class VanillaRegistryView<V> implements IForgeRegistry<V> {
    private final ResourceKey<? extends Registry<V>> key;

    VanillaRegistryView(ResourceKey<? extends Registry<V>> key) {
        this.key = key;
    }

    @SuppressWarnings("unchecked")
    private Registry<V> registry() {
        Registry<?> registry = BuiltInRegistries.REGISTRY.get(key.location());
        if (registry == null) throw new IllegalStateException("Registry " + key.location() + " is not available yet");
        return (Registry<V>) registry;
    }

    @Override
    public ResourceKey<? extends Registry<V>> getRegistryKey() {
        return key;
    }

    @Override
    public ResourceLocation getRegistryName() {
        return key.location();
    }

    @Override
    public boolean containsKey(ResourceLocation id) {
        return registry().containsKey(id);
    }

    @Override
    public boolean containsValue(V value) {
        return registry().getResourceKey(value).isPresent();
    }

    @Override
    public V getValue(ResourceLocation id) {
        return registry().get(id);
    }

    @Override
    public ResourceLocation getKey(V value) {
        return registry().getKey(value);
    }

    @Override
    public Optional<ResourceKey<V>> getResourceKey(V value) {
        return registry().getResourceKey(value);
    }

    @Override
    public Set<ResourceLocation> getKeys() {
        return registry().keySet();
    }

    @Override
    public Collection<V> getValues() {
        return registry().stream().toList();
    }

    @Override
    public Set<Map.Entry<ResourceKey<V>, V>> getEntries() {
        return registry().entrySet();
    }
}
