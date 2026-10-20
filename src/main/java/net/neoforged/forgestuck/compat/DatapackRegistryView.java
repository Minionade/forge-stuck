package net.neoforged.forgestuck.compat;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * {@link IForgeRegistry} for a registry that is a datapack registry in 1.21.1 (for example enchantments): only the
 * key is intrinsically available. When a lookup supplier is provided it resolves current contents lazily; otherwise,
 * lookups throw {@link UnsupportedOperationException} naming the registry. Registering through a
 * {@code DeferredRegister} created from it yields a local, unregistered object (see {@code LocalRegistryObject}).
 */
final class DatapackRegistryView<V> implements IForgeRegistry<V> {
    private final ResourceKey<? extends Registry<V>> key;
    private final Supplier<Registry<V>> registry;

    DatapackRegistryView(ResourceKey<? extends Registry<V>> key) {
        this(key, null);
    }

    DatapackRegistryView(ResourceKey<? extends Registry<V>> key, Supplier<Registry<V>> registry) {
        this.key = key;
        this.registry = registry;
    }

    private Registry<V> registry(String operation) {
        if (registry == null) throw unsupported(operation);
        return registry.get();
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
        return registry("containsKey").containsKey(id);
    }

    @Override
    public boolean containsValue(V value) {
        return registry("containsValue").getResourceKey(value).isPresent();
    }

    @Override
    public V getValue(ResourceLocation id) {
        return registry("getValue").get(id);
    }

    @Override
    public int getId(V value) {
        return registry("getId").getId(value);
    }

    @Override
    public V byId(int id) {
        return registry("byId").byId(id);
    }

    @Override
    public ResourceLocation getKey(V value) {
        return registry("getKey").getKey(value);
    }

    @Override
    public Optional<ResourceKey<V>> getResourceKey(V value) {
        return registry("getResourceKey").getResourceKey(value);
    }

    @Override
    public Set<ResourceLocation> getKeys() {
        return registry("getKeys").keySet();
    }

    @Override
    public Collection<V> getValues() {
        return registry("getValues").stream().toList();
    }

    @Override
    public Set<Map.Entry<ResourceKey<V>, V>> getEntries() {
        return registry("getEntries").entrySet();
    }

    @Override
    public LegacyTagManager<V> tags() {
        return LegacyTagManager.create(registry("tags"));
    }

    UnsupportedOperationException registrationUnsupported(String name) {
        return unsupported("register(" + name + ")");
    }
}