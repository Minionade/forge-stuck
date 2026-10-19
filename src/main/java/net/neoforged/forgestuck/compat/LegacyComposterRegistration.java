package net.neoforged.forgestuck.compat;

import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.ComposterBlock;

public final class LegacyComposterRegistration {
    private LegacyComposterRegistration() {}

    public static void add(float chance, ItemLike item) {
        ComposterBlock.COMPOSTABLES.put(item.asItem(), chance);
    }
}
