package net.neoforged.forgestuck.compat;

import net.minecraft.world.item.crafting.Ingredient;

public final class LegacyIngredientHooks {
    private LegacyIngredientHooks() {}

    public static boolean hasNoElements(Ingredient ingredient) {
        return ingredient.getItems().length == 0;
    }
}
