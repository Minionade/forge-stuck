package net.neoforged.forgestuck.compat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Registry;
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
        private final Object from;
        private final Ingredient ingredient;
        private final Object to;

        public LegacyMix(IForgeRegistry<?> registry, Object from, Ingredient ingredient, Object to) {
            this.from = from;
            this.ingredient = ingredient;
            this.to = to;
        }

        private void register(RegisterBrewingRecipesEvent event, Registry<Potion> potions) {
            ItemStack[] ingredients = ingredient.getItems();
            if (from instanceof Potion fromPotion && to instanceof Potion toPotion) {
                for (ItemStack item : ingredients) {
                    event.getBuilder().addMix(potions.wrapAsHolder(fromPotion), item.getItem(),
                            potions.wrapAsHolder(toPotion));
                }
            } else if (from instanceof Item fromItem && to instanceof Item toItem) {
                for (ItemStack item : ingredients) {
                    event.getBuilder().addContainerRecipe(fromItem, item.getItem(), toItem);
                }
            } else {
                throw new IllegalStateException("Unsupported legacy potion mix endpoints: "
                        + from.getClass().getName() + " -> " + to.getClass().getName());
            }
        }
    }
}
