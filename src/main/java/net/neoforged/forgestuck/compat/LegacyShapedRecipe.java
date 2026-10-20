package net.neoforged.forgestuck.compat;

import net.minecraft.world.item.crafting.ShapedRecipe;

public final class LegacyShapedRecipe {
    private LegacyShapedRecipe() {}

    public static int getRecipeWidth(LegacyIShapedRecipe recipe) {
        return recipe.getRecipeWidth();
    }

    public static int getRecipeHeight(LegacyIShapedRecipe recipe) {
        return recipe.getRecipeHeight();
    }

    public static int getRecipeWidth(ShapedRecipe recipe) {
        return recipe.getWidth();
    }

    public static int getRecipeHeight(ShapedRecipe recipe) {
        return recipe.getHeight();
    }
}
