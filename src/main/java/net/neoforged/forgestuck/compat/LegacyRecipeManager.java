package net.neoforged.forgestuck.compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/** Adapts Forge-era recipe-manager lookups to the recipe-holder API. */
public final class LegacyRecipeManager {
    private static final ReferenceQueue<Recipe<?>> STALE_RECIPES = new ReferenceQueue<>();
    private static final Map<RecipeReference, ResourceLocation> RECIPE_IDS = new HashMap<>();

    private LegacyRecipeManager() {}

    public static <I extends RecipeInput, T extends Recipe<I>> Map<ResourceLocation, T> byType(
            RecipeManager manager, RecipeType<T> type) {
        Map<ResourceLocation, T> recipes = new LinkedHashMap<>();
        for (RecipeHolder<T> holder : manager.getAllRecipesFor(type)) {
            T recipe = holder.value();
            rememberRecipeId(recipe, holder.id());
            recipes.put(holder.id(), recipe);
        }
        return recipes;
    }

    public static <I extends RecipeInput, T extends Recipe<I>> List<T> getAllRecipesFor(
            RecipeManager manager, RecipeType<T> type) {
        List<T> recipes = new ArrayList<>();
        for (RecipeHolder<T> holder : manager.getAllRecipesFor(type)) {
            T recipe = holder.value();
            rememberRecipeId(recipe, holder.id());
            recipes.add(recipe);
        }
        return recipes;
    }

    public static <I extends RecipeInput, T extends Recipe<I>> List<T> getRecipesFor(
            RecipeManager manager, RecipeType<T> type, Container container, Level level) {
        List<T> recipes = new ArrayList<>();
        for (RecipeHolder<T> holder : manager.getAllRecipesFor(type)) {
            T recipe = holder.value();
            rememberRecipeId(recipe, holder.id());
            if (container instanceof RecipeInput input) {
                if (matches(recipe, input, level)) recipes.add(recipe);
            } else {
                Method matcher = legacyContainerMatcher(recipe, container);
                if (matcher == null || invokeMatcher(matcher, recipe, container, level)) recipes.add(recipe);
            }
        }
        return recipes;
    }

    public static <I extends RecipeInput, T extends Recipe<I>> Optional<T> getRecipeFor(
            RecipeManager manager, RecipeType<T> type, Container container, Level level) {
        for (RecipeHolder<T> holder : manager.getAllRecipesFor(type)) {
            T recipe = holder.value();
            rememberRecipeId(recipe, holder.id());
            if (container instanceof RecipeInput input) {
                if (matches(recipe, input, level)) return Optional.of(recipe);
            } else {
                Method matcher = legacyContainerMatcher(recipe, container);
                if (matcher == null) {
                    throw new IllegalStateException("Recipe " + recipe.getClass().getName()
                            + " cannot match a legacy Container input");
                }
                if (invokeMatcher(matcher, recipe, container, level)) return Optional.of(recipe);
            }
        }
        return Optional.empty();
    }

    public static boolean matchesLegacy(Recipe<?> recipe, Container container, Level level) {
        Objects.requireNonNull(recipe, "recipe");
        Objects.requireNonNull(container, "container");
        Objects.requireNonNull(level, "level");
        if (container instanceof RecipeInput input) {
            return matches(recipe, input, level);
        }
        Method matcher = legacyContainerMatcher(recipe, container);
        if (matcher == null) {
            throw new IllegalStateException("Recipe " + recipe.getClass().getName()
                    + " cannot match legacy container " + container.getClass().getName());
        }
        return invokeMatcher(matcher, recipe, container, level);
    }

    public static ResourceLocation getRecipeId(Recipe<?> recipe) {
        Objects.requireNonNull(recipe, "recipe");
        ResourceLocation id;
        synchronized (RECIPE_IDS) {
            expungeStaleRecipes();
            id = RECIPE_IDS.get(new RecipeReference(recipe, null));
        }
        if (id == null) {
            id = legacyRecipeId(recipe);
        }
        return id;
    }

    static ResourceLocation legacyRecipeId(Object recipe) {
        try {
            Method accessor = recipe.getClass().getMethod("m_6423_");
            if (!Modifier.isPublic(accessor.getDeclaringClass().getModifiers())) {
                accessor = publicInterfaceRecipeIdAccessor(recipe.getClass());
                if (accessor == null) {
                    throw new IllegalStateException("Cannot access legacy recipe ID on "
                            + recipe.getClass().getName());
                }
            }
            Object id = accessor.invoke(recipe);
            if (id instanceof ResourceLocation resourceLocation) return resourceLocation;
            throw new IllegalStateException("Legacy recipe ID accessor on " + recipe.getClass().getName()
                    + " returned " + (id == null ? "null" : id.getClass().getName()));
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException("No RecipeHolder ID or legacy recipe ID accessor is available for "
                    + recipe.getClass().getName(), exception);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot access legacy recipe ID on "
                    + recipe.getClass().getName(), exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Legacy recipe ID accessor failed on "
                    + recipe.getClass().getName(), cause);
        }
    }

    private static Method publicInterfaceRecipeIdAccessor(Class<?> recipeClass) {
        Deque<Class<?>> pending = new ArrayDeque<>();
        Set<Class<?>> visited = new HashSet<>();
        for (Class<?> current = recipeClass; current != null; current = current.getSuperclass()) {
            pending.addAll(List.of(current.getInterfaces()));
        }
        while (!pending.isEmpty()) {
            Class<?> type = pending.removeFirst();
            if (!visited.add(type)) continue;
            if (Modifier.isPublic(type.getModifiers())) {
                try {
                    return type.getMethod("m_6423_");
                } catch (NoSuchMethodException ignored) {
                    // Continue through inherited interfaces.
                }
            }
            pending.addAll(List.of(type.getInterfaces()));
        }
        return null;
    }

    static void rememberRecipeId(Recipe<?> recipe, ResourceLocation id) {
        Objects.requireNonNull(recipe, "recipe");
        Objects.requireNonNull(id, "id");
        synchronized (RECIPE_IDS) {
            expungeStaleRecipes();
            RECIPE_IDS.put(new RecipeReference(recipe, STALE_RECIPES), id);
        }
    }

    private static void expungeStaleRecipes() {
        RecipeReference stale;
        while ((stale = (RecipeReference) STALE_RECIPES.poll()) != null) {
            RECIPE_IDS.remove(stale);
        }
    }

    private static Method legacyContainerMatcher(Object recipe, Container container) {
        Method best = null;
        for (Method method : recipe.getClass().getMethods()) {
            if (!method.getName().equals("matches")
                    || method.getParameterCount() != 2
                    || method.getParameterTypes()[1] != Level.class
                    || method.getReturnType() != boolean.class
                    || !method.getParameterTypes()[0].isAssignableFrom(container.getClass())) continue;
            if (best == null || best.getParameterTypes()[0].isAssignableFrom(method.getParameterTypes()[0])) {
                best = method;
            }
        }
        return best;
    }

    private static boolean invokeMatcher(Method matcher, Object recipe, Container container, Level level) {
        try {
            return (boolean) matcher.invoke(recipe, container, level);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot access legacy recipe matcher on "
                    + recipe.getClass().getName(), exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Legacy recipe matcher failed on "
                    + recipe.getClass().getName(), cause);
        }
    }

    @SuppressWarnings("unchecked")
    private static <I extends RecipeInput, T extends Recipe<I>> boolean matches(
            T recipe, RecipeInput input, Level level) {
        return recipe.matches((I) input, level);
    }

    private static final class RecipeReference extends WeakReference<Recipe<?>> {
        private final int identityHash;

        private RecipeReference(Recipe<?> recipe, ReferenceQueue<Recipe<?>> queue) {
            super(recipe, queue);
            identityHash = System.identityHashCode(recipe);
        }

        @Override
        public int hashCode() {
            return identityHash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof RecipeReference reference)) return false;
            Recipe<?> recipe = get();
            return recipe != null && recipe == reference.get();
        }
    }
}
