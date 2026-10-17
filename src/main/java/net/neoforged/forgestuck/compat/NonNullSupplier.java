package net.neoforged.forgestuck.compat;

/** Runtime stand-in for Forge's {@code net.minecraftforge.common.util.NonNullSupplier}. */
@FunctionalInterface
public interface NonNullSupplier<T> {
    T get();
}
