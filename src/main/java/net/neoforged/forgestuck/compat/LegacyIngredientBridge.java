package net.neoforged.forgestuck.compat;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;

/** Runtime conversion helpers for legacy ingredient serializer return values. */
public final class LegacyIngredientBridge {
    private LegacyIngredientBridge() {}

    public static Ingredient asVanilla(Object value) {
        if (value instanceof ICustomIngredient custom) {
            return custom.toVanilla();
        }
        if (value instanceof Ingredient ingredient) {
            return ingredient;
        }
        throw new IllegalStateException("Legacy ingredient serializer returned "
                + (value == null ? "null" : value.getClass().getName()));
    }

    public static ItemStack[] getItems(Ingredient ingredient) {
        ICustomIngredient custom = ingredient.getCustomIngredient();
        if (custom instanceof LegacyAbstractIngredient legacy) {
            return legacy.m_43908_();
        }
        return ingredient.getItems();
    }

    public static IntList getStackingIds(Ingredient ingredient) {
        ICustomIngredient custom = ingredient.getCustomIngredient();
        if (custom instanceof LegacyAbstractIngredient legacy) {
            return legacy.m_43931_();
        }
        IntArrayList ids = new IntArrayList();
        for (ItemStack stack : ingredient.getItems()) {
            ids.add(ItemStack.hashItemAndComponents(stack));
        }
        return ids;
    }
}
