package net.neoforged.forgestuck.compat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

/**
 * Stand-in for Forge 1.20.1's {@code DeferredRegister}. Registration itself is delegated to NeoForge's
 * {@link DeferredRegister}; this class only provides the Forge factory overloads and returns the Forge-style
 * {@link RegistryObject}. The registered object ids are exactly {@code <modid>:<name>}; no names are altered.
 */
public final class LegacyDeferredRegister<T> {
    private final DeferredRegister<T> delegate;
    private final Map<ResourceLocation, RegistryObject<? extends T>> entries = new LinkedHashMap<>();

    private final DatapackRegistryView<?> datapack;
    private Supplier<LegacyRegistryBuilder<T>> registryFactory;

    private LegacyDeferredRegister(DeferredRegister<T> delegate) {
        this(delegate, null);
    }

    private LegacyDeferredRegister(DeferredRegister<T> delegate, DatapackRegistryView<?> datapack) {
        this.delegate = delegate;
        this.datapack = datapack;
    }

    public static <B> LegacyDeferredRegister<B> create(IForgeRegistry<B> registry, String modid) {
        return new LegacyDeferredRegister<>(DeferredRegister.create(registry.getRegistryKey(), modid),
                registry instanceof DatapackRegistryView<?> view ? view : null);
    }

    public static <B> LegacyDeferredRegister<B> create(ResourceKey<? extends Registry<B>> key, String modid) {
        return new LegacyDeferredRegister<>(DeferredRegister.create(key, modid));
    }

    public static <B> LegacyDeferredRegister<B> create(ResourceLocation registryName, String modid) {
        return new LegacyDeferredRegister<>(DeferredRegister.create(registryName, modid));
    }

    @SuppressWarnings("unchecked")
    public <I extends T> RegistryObject<I> register(String name, Supplier<? extends I> sup) {
        if (datapack != null) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(delegate.getNamespace(), name);
            RegistryObject<I> local = new LocalRegistryObject<I>((ResourceKey<? extends Registry<I>>) delegate.getRegistryKey(), id, sup);
            entries.put(id, local);
            return local;
        }
        return track(name, delegate.register(name, sup));
    }

    @SuppressWarnings("unchecked")
    private <I extends T> RegistryObject<I> track(String name, net.neoforged.neoforge.registries.DeferredHolder<T, I> holder) {
        RegistryObject<I> object = (RegistryObject<I>) RegistryObject.create(holder.getId(), delegate.getRegistryKey(),
                delegate.getNamespace());
        entries.put(holder.getId(), object);
        return object;
    }

    public void register(IEventBus bus) {
        delegate.register(bus);
        if (registryFactory != null) bus.addListener(this::createRegistry);
    }

    /** Forge's {@code makeRegistry}: the registry is created from the legacy builder when NeoForge asks for new registries. */
    @SuppressWarnings("unchecked")
    public Supplier<IForgeRegistry<T>> makeRegistry(Supplier<LegacyRegistryBuilder<T>> factory) {
        if (registryFactory != null) {
            throw new IllegalStateException("makeRegistry called twice for " + delegate.getRegistryName());
        }
        registryFactory = factory;
        IForgeRegistry<T> view = new VanillaRegistryView<>((ResourceKey<? extends Registry<T>>) delegate.getRegistryKey());
        return () -> view;
    }

    @SuppressWarnings("unchecked")
    private void createRegistry(NewRegistryEvent event) {
        var builder = new RegistryBuilder<T>((ResourceKey<? extends Registry<T>>) delegate.getRegistryKey());
        registryFactory.get().configure(builder);
        event.create(builder);
    }

    public Collection<RegistryObject<? extends T>> getEntries() {
        return Collections.unmodifiableCollection(new ArrayList<>(entries.values()));
    }

    public ResourceLocation getRegistryName() {
        return delegate.getRegistryName();
    }

    public ResourceKey<? extends Registry<T>> getRegistryKey() {
        return delegate.getRegistryKey();
    }
}
