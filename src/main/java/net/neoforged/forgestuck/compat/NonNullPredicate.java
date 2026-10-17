package net.neoforged.forgestuck.compat;

/** Runtime stand-in for Forge's {@code net.minecraftforge.common.util.NonNullPredicate}. */
@FunctionalInterface
public interface NonNullPredicate<T> {
    boolean test(T t);
}
