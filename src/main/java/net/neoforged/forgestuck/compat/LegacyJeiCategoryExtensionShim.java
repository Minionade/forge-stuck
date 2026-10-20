package net.neoforged.forgestuck.compat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/** Bridges JEI's legacy per-recipe crafting extensions to its current crafting extension API. */
public final class LegacyJeiCategoryExtensionShim {
    private static final String LEGACY_CATEGORY =
            "mezz.jei.api.recipe.category.extensions.IExtendableRecipeCategory";

    private LegacyJeiCategoryExtensionShim() {}

    public static Object getCraftingCategory(Object registration) {
        if (registration == null) throw new IllegalArgumentException("JEI category registration is null");
        try {
            Object category = registration.getClass().getMethod("getCraftingCategory").invoke(registration);
            if (category == null) throw new IllegalStateException("JEI returned a null crafting category");
            ClassLoader loader = registration.getClass().getClassLoader();
            Class<?> legacyCategory = Class.forName(LEGACY_CATEGORY, false, loader);
            return Proxy.newProxyInstance(loader, new Class<?>[] {legacyCategory},
                    new CategoryHandler(category));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to bridge JEI's crafting category registration", unwrap(e));
        }
    }

    static Object addCraftingExtension(Object category, Object[] arguments) {
        if (arguments == null || (arguments.length != 2 && arguments.length != 3)) {
            throw new IllegalStateException("Unexpected JEI addCategoryExtension arguments");
        }
        if (!(arguments[0] instanceof Class<?> recipeClass)
                || !(arguments[arguments.length - 1] instanceof Function<?, ?> factory)) {
            throw new IllegalStateException("JEI crafting extension registration has invalid arguments");
        }
        Predicate<Object> predicate = arguments.length == 3 ? asPredicate(arguments[1]) : recipe -> true;
        Method addExtension = Arrays.stream(category.getClass().getMethods())
                .filter(method -> method.getName().equals("addExtension")
                        && method.getParameterCount() == 2
                        && method.getParameterTypes()[0] == Class.class
                        && method.getParameterTypes()[1].isInterface())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Current JEI crafting category has no compatible addExtension method"));
        Object extension = Proxy.newProxyInstance(addExtension.getParameterTypes()[1].getClassLoader(),
                new Class<?>[] {addExtension.getParameterTypes()[1]},
                new CraftingExtensionHandler(recipeClass, factory, predicate));
        invokeReflective(addExtension, category, recipeClass, extension);
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Predicate<Object> asPredicate(Object predicate) {
        if (!(predicate instanceof Predicate<?>)) {
            throw new IllegalStateException("JEI crafting extension predicate has an invalid type");
        }
        return (Predicate<Object>) predicate;
    }

    private static Object recipeValue(Object value, Class<?> recipeClass) {
        if (value == null) return null;
        if (recipeClass.isInstance(value)) return value;
        try {
            Method valueMethod = value.getClass().getMethod("value");
            Object recipe = valueMethod.invoke(value);
            return recipeClass.isInstance(recipe) ? recipe : null;
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to read recipe from JEI recipe holder", unwrap(e));
        }
    }

    private static Object invokeReflective(Method method, Object receiver, Object... arguments) {
        try {
            return method.invoke(receiver, arguments);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("JEI crafting category extension invocation failed", unwrap(e));
        }
    }

    private static Throwable unwrap(ReflectiveOperationException exception) {
        return exception instanceof InvocationTargetException invocation && invocation.getCause() != null
                ? invocation.getCause() : exception;
    }

    private static Object objectMethod(Object proxy, Method method, Object[] arguments) {
        return switch (method.getName()) {
            case "toString" -> proxy.getClass().getName();
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == (arguments == null ? null : arguments[0]);
            default -> throw new IllegalStateException("Unexpected Object method " + method.getName());
        };
    }

    private static final class CategoryHandler implements InvocationHandler {
        private final Object category;

        private CategoryHandler(Object category) {
            this.category = category;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            if (method.getDeclaringClass() == Object.class) return objectMethod(proxy, method, arguments);
            if (method.getName().equals("addCategoryExtension")) {
                return addCraftingExtension(category, arguments);
            }
            throw new UnsupportedOperationException("Unsupported legacy JEI category method: " + method);
        }
    }

    private static final class CraftingExtensionHandler implements InvocationHandler {
        private final Class<?> recipeClass;
        private final Function<?, ?> factory;
        private final Predicate<Object> predicate;
        private final Map<Object, Object> extensions = Collections.synchronizedMap(new WeakHashMap<>());

        private CraftingExtensionHandler(Class<?> recipeClass, Function<?, ?> factory, Predicate<Object> predicate) {
            this.recipeClass = recipeClass;
            this.factory = factory;
            this.predicate = predicate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            if (method.getDeclaringClass() == Object.class) return objectMethod(proxy, method, arguments);
            Object recipe = findRecipe(arguments);
            if (recipe == null || !predicate.test(recipe)) {
                if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, arguments);
                throw new IllegalStateException("JEI invoked a crafting extension without its recipe: " + method);
            }
            Object extension = extensions.computeIfAbsent(recipe, this::createExtension);
            InvocationTarget invocation = findInvocation(extension, recipe, arguments, method.getName());
            if (invocation == null) {
                if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, arguments);
                throw new UnsupportedOperationException("Legacy JEI crafting extension does not implement " + method);
            }
            return invokeReflective(invocation.method, extension, invocation.arguments);
        }

        private Object findRecipe(Object[] arguments) {
            if (arguments == null) return null;
            for (Object argument : arguments) {
                Object recipe = recipeValue(argument, recipeClass);
                if (recipe != null) return recipe;
            }
            return null;
        }

        private Object createExtension(Object recipe) {
            Object extension = apply(factory, recipe);
            if (extension == null) {
                throw new IllegalStateException("JEI legacy crafting extension factory returned null for "
                        + recipeClass.getName());
            }
            return extension;
        }

        private static InvocationTarget findInvocation(Object extension, Object recipe, Object[] arguments,
                String name) {
            List<Object> candidates = new ArrayList<>();
            candidates.add(recipe);
            if (arguments != null) {
                for (Object argument : arguments) {
                    if (recipeValue(argument, recipe.getClass()) != null) continue;
                    candidates.add(argument);
                }
            }
            return Arrays.stream(extension.getClass().getMethods())
                    .filter(candidate -> candidate.getName().equals(name))
                    .map(candidate -> selectArguments(candidate, candidates))
                    .filter(java.util.Objects::nonNull)
                    .min(java.util.Comparator.comparingInt(target -> target.skippedArguments))
                    .orElse(null);
        }

        private static InvocationTarget selectArguments(Method method, List<Object> candidates) {
            Object[] selected = new Object[method.getParameterCount()];
            int candidateIndex = 0;
            for (int parameterIndex = 0; parameterIndex < selected.length; parameterIndex++) {
                Class<?> parameterType = method.getParameterTypes()[parameterIndex];
                while (candidateIndex < candidates.size()
                        && !isCompatible(parameterType, candidates.get(candidateIndex))) {
                    candidateIndex++;
                }
                if (candidateIndex == candidates.size()) return null;
                selected[parameterIndex] = candidates.get(candidateIndex++);
            }
            return new InvocationTarget(method, selected, candidates.size() - selected.length);
        }

        private static boolean isCompatible(Class<?> parameterType, Object value) {
            if (value == null) return !parameterType.isPrimitive();
            if (parameterType.isInstance(value)) return true;
            return parameterType.isPrimitive() && (parameterType == int.class && value instanceof Integer
                    || parameterType == boolean.class && value instanceof Boolean
                    || parameterType == long.class && value instanceof Long
                    || parameterType == double.class && value instanceof Double
                    || parameterType == float.class && value instanceof Float
                    || parameterType == short.class && value instanceof Short
                    || parameterType == byte.class && value instanceof Byte
                    || parameterType == char.class && value instanceof Character);
        }

        @SuppressWarnings("unchecked")
        private static Object apply(Function<?, ?> factory, Object recipe) {
            return ((Function<Object, Object>) factory).apply(recipe);
        }
    }

    private record InvocationTarget(Method method, Object[] arguments, int skippedArguments) {}
}
