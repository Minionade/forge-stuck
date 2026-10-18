package net.neoforged.forgestuck.compat;

import net.minecraft.world.item.crafting.ShapedRecipe;

public final class LegacyShapedRecipe {
    private LegacyShapedRecipe() {}

    public static int getRecipeWidth(ShapedRecipe recipe) {
        return recipe.getWidth();
    }

    public static int getRecipeHeight(ShapedRecipe recipe) {
        return recipe.getHeight();
    }
}
