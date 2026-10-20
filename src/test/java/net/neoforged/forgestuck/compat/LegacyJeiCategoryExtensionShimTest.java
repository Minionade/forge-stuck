package net.neoforged.forgestuck.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class LegacyJeiCategoryExtensionShimTest {
    @Test
    void adaptsPerRecipeFactoryToCurrentCraftingExtension() {
        FakeCategoryTest category = new FakeCategoryTest();
        FakeRecipeTest recipe = new FakeRecipeTest("test-recipe");
        FakeRecipeHolderTest holder = new FakeRecipeHolderTest(recipe);
        AtomicInteger factoryCalls = new AtomicInteger();

        LegacyJeiCategoryExtensionShim.addCraftingExtension(category,
                new Object[] {FakeRecipeTest.class, (Function<Object, Object>) value -> {
                    factoryCalls.incrementAndGet();
                    return new FakeLegacyExtensionTest((FakeRecipeTest) value);
                }});

        assertSame(FakeRecipeTest.class, category.recipeClass);
        category.extension.setRecipe(holder, "layout");
        assertEquals(11, category.extension.getWidth(holder));
        assertEquals(1, factoryCalls.get());
    }

    public static final class FakeCategoryTest {
        private Class<?> recipeClass;
        private FakeCraftingExtensionTest extension;

        public void addExtension(Class<?> recipeClass, FakeCraftingExtensionTest extension) {
            this.recipeClass = recipeClass;
            this.extension = extension;
        }
    }

    public interface FakeCraftingExtensionTest {
        void setRecipe(FakeRecipeHolderTest recipe, String layout);

        int getWidth(FakeRecipeHolderTest recipe);
    }

    public static final class FakeRecipeHolderTest {
        private final FakeRecipeTest recipe;

        private FakeRecipeHolderTest(FakeRecipeTest recipe) {
            this.recipe = recipe;
        }

        public FakeRecipeTest value() {
            return recipe;
        }
    }

    public record FakeRecipeTest(String id) {}

    public static final class FakeLegacyExtensionTest {
        private final FakeRecipeTest recipe;
        private String layout;

        private FakeLegacyExtensionTest(FakeRecipeTest recipe) {
            this.recipe = recipe;
        }

        public void setRecipe(String layout) {
            this.layout = layout;
        }

        public int getWidth() {
            return layout == null ? 0 : recipe.id().length();
        }
    }
}
