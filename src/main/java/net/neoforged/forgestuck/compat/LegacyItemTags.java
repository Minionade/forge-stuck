package net.neoforged.forgestuck.compat;

import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.resources.ResourceLocation;

public final class LegacyItemTags {
    public static final TagKey<Item> ARMORS_BOOTS = ItemTags.FOOT_ARMOR;
    public static final TagKey<Item> ARMORS_CHESTPLATES = ItemTags.CHEST_ARMOR;
    public static final TagKey<Item> ARMORS_HELMETS = ItemTags.HEAD_ARMOR;
    public static final TagKey<Item> ARMORS_LEGGINGS = ItemTags.LEG_ARMOR;
    public static final TagKey<Item> HEADS = ItemTags.SKULLS;
    public static final TagKey<Item> DUSTS_PRISMARINE =
            ItemTags.create(ResourceLocation.fromNamespaceAndPath("forge", "dusts/prismarine"));
    public static final TagKey<Item> STAINED_GLASS =
            ItemTags.create(ResourceLocation.fromNamespaceAndPath("forge", "stained_glass"));
    public static final TagKey<Item> STAINED_GLASS_PANES =
            ItemTags.create(ResourceLocation.fromNamespaceAndPath("forge", "stained_glass_panes"));
    public static final TagKey<Item> STORAGE_BLOCKS_QUARTZ =
            ItemTags.create(ResourceLocation.fromNamespaceAndPath("c", "storage_blocks/quartz"));

    private LegacyItemTags() {}
}
