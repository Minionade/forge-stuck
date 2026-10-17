package net.neoforged.forgestuck.compat;

import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * {@link RegistryObject} for an object registered into a datapack registry. It cannot be registered from code in
 * 1.21.1, so the object is created lazily from its supplier on first access and simply held; it is never visible in
 * the game registry.
 */
final class LocalRegistryObject<T> extends RegistryObject<T> {
    private final Supplier<? extends T> supplier;
    private volatile T value;

    LocalRegistryObject(ResourceKey<? extends Registry<T>> registryKey, ResourceLocation id,
            Supplier<? extends T> supplier) {
        super(castKey(ResourceKey.create(registryKey, id)));
        this.supplier = supplier;
    }

    @SuppressWarnings("unchecked")
    private static <U> ResourceKey<U> castKey(ResourceKey<?> key) {
        return (ResourceKey<U>) key;
    }

    @Override
    public T get() {
        T v = value;
        if (v == null) {
            synchronized (this) {
                v = value;
                if (v == null) value = v = supplier.get();
            }
        }
        return v;
    }

    @Override
    public T value() {
        return get();
    }

    @Override
    public boolean isBound() {
        return true;
    }
}
