package net.neoforged.forgestuck.compat;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.advancements.Advancement;
import net.minecraft.world.item.Items;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;
import net.neoforged.neoforge.common.conditions.TrueCondition;
import org.junit.jupiter.api.Test;

class LegacyConditionalAdvancementTest {
    @Test
    void singleConditionalAdvancementUsesNeoForgeConditionalCodecShape() {
        net.neoforged.forgestuck.testsupport.TestBootstrap.init();
        var advancement = Advancement.Builder.recipeAdvancement()
                .addCriterion("has_stone", InventoryChangeTrigger.TriggerInstance.hasItems(Items.STONE));

        var builder = new LegacyConditionalAdvancement.Builder()
                .addCondition(TrueCondition.INSTANCE)
                .addAdvancement(advancement);
        var json = builder.write();

        assertEquals(1, json.getAsJsonArray("neoforge:conditions").size());
        assertEquals("neoforge:true",
                json.getAsJsonArray("neoforge:conditions").get(0).getAsJsonObject().get("type").getAsString());
        assertTrue(json.has("criteria"));
        assertFalse(json.has("advancements"));
        assertEquals(json, builder.write());
    }

    @Test
    void conditionDatagenUsesTheConditionCodecWithoutRegistryDispatch() {
        var json = LegacyConditions.serializeForDatagen(new ModLoadedCondition("optionalmod"));

        assertEquals("neoforge:mod_loaded", json.get("type").getAsString());
        assertEquals("optionalmod", json.get("modid").getAsString());
    }

    @Test
    void unsupportedMultipleVariantsFailRatherThanDroppingAnAdvancement() {
        var builder = new LegacyConditionalAdvancement.Builder()
                .addCondition(TrueCondition.INSTANCE)
                .addAdvancement(Advancement.Builder.recipeAdvancement()
                        .addCriterion("first", InventoryChangeTrigger.TriggerInstance.hasItems(Items.STONE)));

        assertThrows(UnsupportedOperationException.class,
                () -> builder.addAdvancement(Advancement.Builder.recipeAdvancement()
                        .addCriterion("second", InventoryChangeTrigger.TriggerInstance.hasItems(Items.DIRT))));
    }
}
