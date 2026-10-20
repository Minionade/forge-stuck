package net.neoforged.forgestuck.compat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.brewing.IBrewingRecipe;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.minecraft.world.item.crafting.Ingredient;

public final class LegacyBrewingRecipeRegistry {
    private static final List<IBrewingRecipe> RECIPES = new ArrayList<>();
    public static final List<LegacyMix> LEGACY_MIXES = new ArrayList<>();
    private static boolean registrationEventFired;

    static {
        NeoForge.EVENT_BUS.addListener(LegacyBrewingRecipeRegistry::registerRecipes);
    }

    private LegacyBrewingRecipeRegistry() {}

    public static synchronized boolean addRecipe(IBrewingRecipe recipe) {
        if (registrationEventFired) {
            throw new IllegalStateException("Cannot register a legacy brewing recipe after registration has completed");
        }
        RECIPES.add(java.util.Objects.requireNonNull(recipe, "recipe"));
        return true;
    }

    public static synchronized void addPotionMix(Potion from, Item ingredient, Potion to) {
        if (registrationEventFired) {
            throw new IllegalStateException("Cannot register a legacy potion mix after registration has completed");
        }
        LEGACY_MIXES.add(new LegacyMix(null, from, Ingredient.of(ingredient), to));
    }

    private static synchronized void registerRecipes(RegisterBrewingRecipesEvent event) {
        registrationEventFired = true;
        RECIPES.forEach(event.getBuilder()::addRecipe);
        RECIPES.clear();
        Registry<Potion> potions = event.getRegistryAccess().registryOrThrow(Registries.POTION);
        for (LegacyMix mix : LEGACY_MIXES) {
            mix.register(event, potions);
        }
        LEGACY_MIXES.clear();
    }

    public static final class LegacyMix {
        public final Holder.Reference<Potion> from;
        public final Ingredient ingredient;
        public final Holder.Reference<Potion> to;

        public LegacyMix(IForgeRegistry<?> registry, Object from, Ingredient ingredient, Object to) {
            this.from = potionHolder(from);
            this.ingredient = ingredient;
            this.to = potionHolder(to);
        }

        private static Holder.Reference<Potion> potionHolder(Object value) {
            if (value instanceof Holder.Reference<?> holder) {
                @SuppressWarnings("unchecked")
                Holder.Reference<Potion> potionHolder = (Holder.Reference<Potion>) holder;
                return potionHolder;
            }
            if (value instanceof Potion potion) {
                var key = BuiltInRegistries.POTION.getResourceKey(potion).orElseThrow(
                        () -> new IllegalArgumentException("Unregistered potion in legacy mix: " + potion));
                return BuiltInRegistries.POTION.getHolder(key).orElseThrow(
                        () -> new IllegalArgumentException("Potion has no registry holder: " + key));
            }
            throw new IllegalArgumentException("Legacy potion mix endpoint is not a potion: "
                    + (value == null ? "null" : value.getClass().getName()));
        }

        private void register(RegisterBrewingRecipesEvent event, Registry<Potion> potions) {
            ItemStack[] ingredients = ingredient.getItems();
            Potion fromPotion = from.value();
            Potion toPotion = to.value();
            for (ItemStack item : ingredients) {
                event.getBuilder().addMix(potions.wrapAsHolder(fromPotion), item.getItem(),
                        potions.wrapAsHolder(toPotion));
            }
        }
    }
}
