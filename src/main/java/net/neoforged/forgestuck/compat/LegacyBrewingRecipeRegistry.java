package net.neoforged.forgestuck.compat;

import java.util.ArrayList;
import java.util.List;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.brewing.IBrewingRecipe;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;

public final class LegacyBrewingRecipeRegistry {
    private static final List<IBrewingRecipe> RECIPES = new ArrayList<>();
    private static boolean registrationEventFired;

    static {
        NeoForge.EVENT_BUS.addListener(LegacyBrewingRecipeRegistry::registerRecipes);
    }

    private LegacyBrewingRecipeRegistry() {}

    public static synchronized boolean addRecipe(IBrewingRecipe recipe) {
        if (registrationEventFired) {
            throw new IllegalStateException("Cannot register a legacy brewing recipe after registration has completed");
        }
        RECIPES.add(java.util.Objects.requireNonNull(recipe, "recipe"));
        return true;
    }

    private static synchronized void registerRecipes(RegisterBrewingRecipesEvent event) {
        registrationEventFired = true;
        RECIPES.forEach(event.getBuilder()::addRecipe);
        RECIPES.clear();
    }
}
