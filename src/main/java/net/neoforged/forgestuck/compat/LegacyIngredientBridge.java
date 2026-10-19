package net.neoforged.forgestuck.compat;

import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

    public static JsonObject serializeLegacy(Object serializer, Object ingredient) {
        if (!(serializer instanceof LegacyIngredientSerializer<?> legacySerializer)
                || !(ingredient instanceof LegacyAbstractIngredient)) {
            throw new IllegalStateException("Cannot serialize legacy ingredient "
                    + (ingredient == null ? "null" : ingredient.getClass().getName())
                    + " with " + (serializer == null ? "null" : serializer.getClass().getName()));
        }
        JsonObject json = new JsonObject();
        json.addProperty("type", LegacyConditions.getID(legacySerializer).toString());
        try {
            Object loadable = serializer.getClass().getMethod("loadable").invoke(serializer);
            Method serialize = loadable.getClass().getMethod("serialize", Object.class, JsonObject.class);
            serialize.invoke(loadable, ingredient, json);
            return json;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Legacy ingredient serialization failed", cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot access the legacy ingredient loadable serializer", e);
        }
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
