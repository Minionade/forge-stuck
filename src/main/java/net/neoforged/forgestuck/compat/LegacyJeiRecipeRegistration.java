package net.neoforged.forgestuck.compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/** Adapts legacy JEI recipe lists to APIs that use recipe holders for vanilla recipe types. */
public final class LegacyJeiRecipeRegistration {
    private static final String TCONSTRUCT_TOOL_MODIFICATION_TYPE =
            "slimeknights.tconstruct.library.recipe.tinkerstation.IDisplayToolTinkering";

    private LegacyJeiRecipeRegistration() {}

    public static void addRecipes(Object registration, Object type, List<?> recipes) {
        Objects.requireNonNull(registration, "registration");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(recipes, "recipes");
        List<?> adaptedRecipes = adaptRecipes(type, recipes);
        Method addRecipes = findAddRecipesMethod(registration.getClass(), type, adaptedRecipes);
        try {
            addRecipes.invoke(registration, type, adaptedRecipes);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot access JEI recipe registration method", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("JEI recipe registration failed", cause);
        }
    }

    private static List<?> adaptRecipes(Object type, List<?> recipes) {
        Class<?> recipeClass = recipeClass(type);
        if (recipeClass == null) return recipes;
        boolean expectsRecipeHolders = RecipeHolder.class.isAssignableFrom(recipeClass);
        boolean tconstructToolModification = recipeClass.getName().equals(TCONSTRUCT_TOOL_MODIFICATION_TYPE)
                && recipeClass.isInterface();
        if (!expectsRecipeHolders && !tconstructToolModification) return recipes;
        List<Object> adapted = new ArrayList<>(recipes.size());
        boolean changed = false;
        for (Object recipe : recipes) {
            if (recipe instanceof RecipeHolder<?>) {
                adapted.add(recipe);
            } else if (tconstructToolModification && isTConstructToolModification(recipeClass, recipe)) {
                adapted.add(withoutCraftingRecipeMarker(recipe, recipeClass));
                changed = true;
            } else if (recipe instanceof Recipe<?> legacyRecipe) {
                if (!expectsRecipeHolders) return recipes;
                adapted.add(new RecipeHolder<>(LegacyRecipeManager.getRecipeId(legacyRecipe), legacyRecipe));
                changed = true;
            } else {
                return recipes;
            }
        }
        return changed ? adapted : recipes;
    }

    private static boolean isTConstructToolModification(Class<?> recipeClass, Object recipe) {
        return recipeClass != null
                && recipeClass.getName().equals(TCONSTRUCT_TOOL_MODIFICATION_TYPE)
                && recipeClass.isInterface()
                && recipeClass.isInstance(recipe)
                && recipe instanceof CraftingRecipe;
    }

    static Object withoutCraftingRecipeMarker(Object recipe, Class<?> recipeClass) {
        return Proxy.newProxyInstance(recipeClass.getClassLoader(), new Class<?>[] {recipeClass},
                (proxy, method, arguments) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> recipe.toString();
                            case "hashCode" -> recipe.hashCode();
                            case "equals" -> recipe.equals(arguments == null ? null : arguments[0]);
                            default -> throw new UnsupportedOperationException(
                                    "Unsupported Object method on JEI recipe adapter: " + method);
                        };
                    }
                    try {
                        return method.invoke(recipe, arguments);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });
    }

    private static Class<?> recipeClass(Object type) {
        try {
            Object recipeClass = type.getClass().getMethod("getRecipeClass").invoke(type);
            return recipeClass instanceof Class<?> result ? result : null;
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot inspect JEI recipe type", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("JEI recipe type inspection failed", cause);
        }
    }

    private static Method findAddRecipesMethod(Class<?> registrationClass, Object type, List<?> recipes) {
        for (Method method : registrationClass.getMethods()) {
            if (method.getName().equals("addRecipes")
                    && method.getParameterCount() == 2
                    && method.getParameterTypes()[0].isInstance(type)
                    && method.getParameterTypes()[1].isInstance(recipes)) {
                return method;
            }
        }
        throw new IllegalStateException("No compatible JEI addRecipes method on " + registrationClass.getName());
    }
}
