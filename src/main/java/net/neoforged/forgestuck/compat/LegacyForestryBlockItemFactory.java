package net.neoforged.forgestuck.compat;

import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.world.item.Item;

/** Adapts Forestry's old block-item factory to the 1.21 builder's properties-aware factory. */
public final class LegacyForestryBlockItemFactory {
    private LegacyForestryBlockItemFactory() {}

    public static <B, I> BiFunction<B, Item.Properties, I> adapt(Function<? super B, ? extends I> factory) {
        return (block, properties) -> factory.apply(block);
    }
}
