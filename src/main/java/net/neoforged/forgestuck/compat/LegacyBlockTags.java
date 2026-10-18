package net.neoforged.forgestuck.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class LegacyBlockTags {
    public static final TagKey<Block> STAINED_GLASS =
            BlockTags.create(ResourceLocation.fromNamespaceAndPath("forge", "stained_glass"));
    public static final TagKey<Block> STAINED_GLASS_PANES =
            BlockTags.create(ResourceLocation.fromNamespaceAndPath("forge", "stained_glass_panes"));

    private LegacyBlockTags() {}
}
