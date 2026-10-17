package net.neoforged.forgestuck.compat;

/** Runtime stand-in for Forge's {@code net.minecraftforge.common.util.NonNullFunction}. */
@FunctionalInterface
public interface NonNullFunction<T, R> {
    R apply(T t);
}
