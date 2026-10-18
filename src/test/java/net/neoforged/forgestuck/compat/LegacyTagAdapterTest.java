package net.neoforged.forgestuck.compat;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

class LegacyTagAdapterTest {
    @Test
    void registryTagAndReverseTagViewsUseHolderValues() {
        LegacyTagManager<net.minecraft.world.item.Item> tags = LegacyTagManager.create(BuiltInRegistries.ITEM);

        LegacyTag<net.minecraft.world.item.Item> planks = tags.getTag(ItemTags.PLANKS);
        assertSame(planks, tags.getTag(ItemTags.PLANKS));
        assertNotNull(planks.stream().toList());
        var reverse = tags.getReverseTag(Items.OAK_PLANKS);
        assertTrue(reverse.isPresent());
        assertNotNull(reverse.orElseThrow().getTagKeys().toList());
    }
}
