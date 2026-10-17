package net.neoforged.forgestuck.compat;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Runtime stand-in for Forge 1.20.1's {@code RegistryObject}. It extends NeoForge's {@link DeferredHolder} so it is a
 * {@code Holder} and {@code Supplier} for NeoForge APIs, and adds the Optional-style members and {@code create}
 * overloads that DeferredHolder does not provide.
 */
public class RegistryObject<T> extends DeferredHolder<T, T> {
    protected RegistryObject(ResourceKey<T> key) {
        super(key);
    }

    public static <T, U extends T> RegistryObject<U> create(ResourceLocation name,
            ResourceKey<? extends Registry<T>> registryKey, String modid) {
        return new RegistryObject<>(castKey(ResourceKey.create(registryKey, name)));
    }

    public static <T, U extends T> RegistryObject<U> create(ResourceLocation name, ResourceLocation registryName,
            String modid) {
        return new RegistryObject<>(castKey(ResourceKey.create(ResourceKey.createRegistryKey(registryName), name)));
    }

    public static <T, U extends T> RegistryObject<U> create(ResourceLocation name, IForgeRegistry<T> registry) {
        return new RegistryObject<>(castKey(ResourceKey.create(registry.getRegistryKey(), name)));
    }

    @SuppressWarnings("unchecked")
    private static <U> ResourceKey<U> castKey(ResourceKey<?> key) {
        return (ResourceKey<U>) key;
    }

    public boolean isPresent() {
        return isBound();
    }

    public void ifPresent(Consumer<? super T> consumer) {
        if (isPresent()) consumer.accept(get());
    }

    public Stream<T> stream() {
        return isPresent() ? Stream.of(get()) : Stream.empty();
    }

    public RegistryObject<T> filter(Predicate<? super T> predicate) {
        return isPresent() && predicate.test(get()) ? this : null;
    }

    public T orElse(T other) {
        return isPresent() ? get() : other;
    }

    public T orElseGet(Supplier<? extends T> supplier) {
        return isPresent() ? get() : supplier.get();
    }

    public <X extends Throwable> T orElseThrow(Supplier<? extends X> exception) throws X {
        if (!isPresent()) throw exception.get();
        return get();
    }

    public Optional<Holder<T>> getHolder() {
        return isPresent() ? Optional.of(this) : Optional.empty();
    }

    /** Parity with Forge: {@code get()} on an unregistered object throws a NullPointerException with this message. */
    @Override
    public T get() {
        try {
            return super.get();
        } catch (NullPointerException e) {
            throw new NullPointerException("Registry Object not present: " + getId());
        }
    }
}
