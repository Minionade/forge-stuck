package net.neoforged.forgestuck.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import slimeknights.tconstruct.library.recipe.tinkerstation.IDisplayToolTinkering;
import net.neoforged.forgestuck.testsupport.LegacyRecipeIdTestStub;
import net.neoforged.forgestuck.testsupport.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LegacyRecipeManagerTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.init();
    }

    @Test
    void returnsRecipeHolderIdForLegacyRecipeAccess() {
        Recipe<?> recipe = (Recipe<?>) Proxy.newProxyInstance(Recipe.class.getClassLoader(),
                new Class<?>[] {Recipe.class}, (proxy, method, arguments) -> null);
        ResourceLocation id = ResourceLocation.parse("test:legacy_recipe");

        LegacyRecipeManager.rememberRecipeId(recipe, id);

        assertEquals(id, LegacyRecipeManager.getRecipeId(recipe));
    }

    @Test
    void failsClearlyWhenRecipeWasNotObtainedFromRecipeManagerBridge() {
        Recipe<?> recipe = (Recipe<?>) Proxy.newProxyInstance(Recipe.class.getClassLoader(),
                new Class<?>[] {Recipe.class}, (proxy, method, arguments) -> null);

        assertThrows(IllegalStateException.class, () -> LegacyRecipeManager.getRecipeId(recipe));
    }

    @Test
    void readsLegacyRecipeIdBeforeHolderRegistrationDuringNetworkDecode() throws Exception {
        ResourceLocation id = ResourceLocation.parse("test:decoded_recipe");
        Method legacyRecipeId = LegacyRecipeManager.class.getDeclaredMethod("legacyRecipeId", Object.class);
        legacyRecipeId.setAccessible(true);
        assertEquals(id, legacyRecipeId.invoke(null, new LegacyRecipeIdTestStub(id)));
    }

    @Test
    void wrapsLegacyRecipesWhenCurrentJeiTypeRequiresRecipeHolders() {
        Recipe<?> recipe = (Recipe<?>) Proxy.newProxyInstance(Recipe.class.getClassLoader(),
                new Class<?>[] {Recipe.class}, (proxy, method, arguments) -> null);
        ResourceLocation id = ResourceLocation.parse("test:jei_recipe");
        LegacyRecipeManager.rememberRecipeId(recipe, id);
        JeiRegistrationTest registration = new JeiRegistrationTest();

        LegacyJeiRecipeRegistration.addRecipes(registration, new JeiRecipeTypeTest(), List.of(recipe));

        RecipeHolder<?> holder = assertInstanceOf(RecipeHolder.class, registration.recipes.get(0));
        assertEquals(id, holder.id());
        assertSame(recipe, holder.value());
    }

    @Test
    void removesTConstructDisplayRecipesCraftingMarkerForJei() {
        Object recipe = Proxy.newProxyInstance(IDisplayToolTinkering.class.getClassLoader(),
                new Class<?>[] {IDisplayToolTinkering.class, net.minecraft.world.item.crafting.CraftingRecipe.class},
                (proxy, method, arguments) -> method.getName().equals("name") ? "display" : null);
        JeiRecipeTypeTest type = new JeiRecipeTypeTest(IDisplayToolTinkering.class);
        JeiRegistrationTest registration = new JeiRegistrationTest();
        LegacyJeiRecipeRegistration.addRecipes(registration, type, List.of(recipe));

        Object adapted = registration.recipes.get(0);
        assertInstanceOf(IDisplayToolTinkering.class, adapted);
        org.junit.jupiter.api.Assertions.assertFalse(adapted instanceof net.minecraft.world.item.crafting.CraftingRecipe);
    }

    public static final class JeiRecipeTypeTest {
        private final Class<?> recipeClass;

        public JeiRecipeTypeTest() {
            this(RecipeHolder.class);
        }

        JeiRecipeTypeTest(Class<?> recipeClass) {
            this.recipeClass = recipeClass;
        }

        public Class<?> getRecipeClass() {
            return recipeClass;
        }
    }

    public static final class JeiRegistrationTest {
        private List<?> recipes;

        public void addRecipes(Object type, List<?> recipes) {
            this.recipes = recipes;
        }
    }
}
