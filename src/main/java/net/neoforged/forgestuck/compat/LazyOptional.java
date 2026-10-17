package net.neoforged.forgestuck.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Runtime stand-in for Forge's {@code net.minecraftforge.common.util.LazyOptional}, which NeoForge 1.21.1 no longer
 * ships. Only the commonly used subset of the API is provided; see {@code ForgeOwnerMappings}.
 */
public final class LazyOptional<T> {
    private static final LazyOptional<Object> EMPTY = new LazyOptional<>(null);

    private final NonNullSupplier<T> supplier;
    private T resolved;
    private boolean isResolved;
    private boolean valid = true;
    private List<NonNullConsumer<LazyOptional<T>>> listeners;

    private LazyOptional(NonNullSupplier<T> supplier) {
        this.supplier = supplier;
    }

    public static <T> LazyOptional<T> of(NonNullSupplier<T> supplier) {
        return supplier == null ? empty() : new LazyOptional<>(supplier);
    }

    @SuppressWarnings("unchecked")
    public static <T> LazyOptional<T> empty() {
        return (LazyOptional<T>) EMPTY;
    }

    private T getValue() {
        if (!isPresent()) return null;
        if (!isResolved) {
            resolved = supplier.get();
            isResolved = true;
        }
        return resolved;
    }

    public boolean isPresent() {
        return supplier != null && valid;
    }

    public void ifPresent(NonNullConsumer<? super T> consumer) {
        T value = getValue();
        if (value != null) consumer.accept(value);
    }

    public Optional<T> resolve() {
        return Optional.ofNullable(getValue());
    }

    public T orElse(T other) {
        T value = getValue();
        return value != null ? value : other;
    }

    public <X extends Throwable> T orElseThrow(Supplier<? extends X> exception) throws X {
        T value = getValue();
        if (value == null) throw exception.get();
        return value;
    }

    public <U> LazyOptional<U> lazyMap(NonNullFunction<? super T, ? extends U> mapper) {
        if (!isPresent()) return empty();
        return of(() -> mapper.apply(getValue()));
    }

    public void addListener(NonNullConsumer<LazyOptional<T>> listener) {
        if (!isPresent()) {
            listener.accept(this);
            return;
        }
        if (listeners == null) listeners = new ArrayList<>();
        listeners.add(listener);
    }

    public void invalidate() {
        if (supplier == null || !valid) return;
        valid = false;
        List<NonNullConsumer<LazyOptional<T>>> l = listeners;
        listeners = null;
        if (l != null) l.forEach(c -> c.accept(this));
    }
}
