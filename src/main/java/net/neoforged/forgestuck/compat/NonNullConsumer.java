package net.neoforged.forgestuck.compat;

/** Runtime stand-in for Forge's {@code net.minecraftforge.common.util.NonNullConsumer}, absent on NeoForge 1.21.1. */
@FunctionalInterface
public interface NonNullConsumer<T> {
    void accept(T t);
}
