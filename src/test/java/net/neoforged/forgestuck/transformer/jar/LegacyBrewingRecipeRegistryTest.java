package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import net.minecraft.core.Holder;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.forgestuck.compat.LegacyBrewingRecipeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LegacyBrewingRecipeRegistryTest {
    @BeforeAll
    static void bootstrap() {
        net.neoforged.forgestuck.testsupport.TestBootstrap.init();
    }

    @Test
    void legacyMixExposesHolderFieldsExpectedByRemappedCallers() throws Exception {
        LegacyBrewingRecipeRegistry.LegacyMix mix = new LegacyBrewingRecipeRegistry.LegacyMix(
                null, Potions.WATER, Ingredient.EMPTY, Potions.AWKWARD);

        assertEquals(Holder.Reference.class, mix.from.getClass());
        assertEquals(Potions.WATER.value(), mix.from.value());
        assertEquals(Potions.AWKWARD.value(), mix.to.value());
        assertNotNull(mix.ingredient);
        assertEquals(Holder.Reference.class,
                LegacyBrewingRecipeRegistry.LegacyMix.class.getDeclaredField("from").getType());
        assertEquals(Holder.Reference.class,
                LegacyBrewingRecipeRegistry.LegacyMix.class.getDeclaredField("to").getType());
        assertEquals(Ingredient.class,
                LegacyBrewingRecipeRegistry.LegacyMix.class.getDeclaredField("ingredient").getType());
    }
}
