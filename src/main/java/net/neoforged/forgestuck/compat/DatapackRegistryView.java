package net.neoforged.forgestuck.compat;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * {@link IForgeRegistry} for a registry that is a datapack registry in 1.21.1 (for example enchantments): only the
 * key is known, because contents need a {@code RegistryAccess}. Every lookup throws
 * {@link UnsupportedOperationException} naming the registry, so holding the field (static init) links while the
 * call that needs contents fails. Registering through a {@code DeferredRegister} created from it yields a local,
 * unregistered object (see {@code LocalRegistryObject}).
 */
final class DatapackRegistryView<V> implements IForgeRegistry<V> {
    private final ResourceKey<? extends Registry<V>> key;

    DatapackRegistryView(ResourceKey<? extends Registry<V>> key) {
        this.key = key;
    }

    private UnsupportedOperationException unsupported(String what) {
        return new UnsupportedOperationException("Forge registry " + key.location() + "." + what + ": it is a datapack "
                + "registry in 1.21.1, so contents need a RegistryAccess and objects cannot be registered from code");
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
        throw unsupported("containsKey");
    }

    @Override
    public boolean containsValue(V value) {
        throw unsupported("containsValue");
    }

    @Override
    public V getValue(ResourceLocation id) {
        throw unsupported("getValue");
    }

    @Override
    public ResourceLocation getKey(V value) {
        throw unsupported("getKey");
    }

    @Override
    public Optional<ResourceKey<V>> getResourceKey(V value) {
        throw unsupported("getResourceKey");
    }

    @Override
    public Set<ResourceLocation> getKeys() {
        throw unsupported("getKeys");
    }

    @Override
    public Collection<V> getValues() {
        throw unsupported("getValues");
    }

    @Override
    public Set<Map.Entry<ResourceKey<V>, V>> getEntries() {
        throw unsupported("getEntries");
    }

    UnsupportedOperationException registrationUnsupported(String name) {
        return unsupported("register(" + name + ")");
    }
}