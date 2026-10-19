package net.neoforged.forgestuck.compat;

import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** Resolves the registry keys required by the 1.21.1 StemBlock constructor. */
public final class LegacyStemBlockShim {
    private LegacyStemBlockShim() {}

    public static ResourceKey<Block> fruitKey(Block fruit) {
        return BuiltInRegistries.BLOCK.getResourceKey(fruit)
                .orElseThrow(() -> new IllegalStateException("Legacy stem fruit is not registered: " + fruit));
    }

    public static ResourceKey<Block> attachedStemKey(Block fruit) {
        ResourceKey<Block> fruitKey = fruitKey(fruit);
        return ResourceKey.create(Registries.BLOCK,
                fruitKey.location().withPrefix("attached_").withSuffix("_stem"));
    }

    public static ResourceKey<Block> stemKey(Block fruit) {
        ResourceKey<Block> fruitKey = fruitKey(fruit);
        return ResourceKey.create(Registries.BLOCK, fruitKey.location().withSuffix("_stem"));
    }

    public static ResourceKey<Item> seedKey(Block fruit) {
        ResourceKey<Block> fruitKey = fruitKey(fruit);
        return ResourceKey.create(Registries.ITEM, fruitKey.location().withSuffix("_seeds"));
    }

    public static ItemStack seedStack(Supplier<Item> seed) {
        return new ItemStack(seed.get());
    }
}
