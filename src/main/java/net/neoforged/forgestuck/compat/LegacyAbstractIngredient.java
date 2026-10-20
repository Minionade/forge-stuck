package net.neoforged.forgestuck.compat;

import com.google.gson.JsonElement;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;

/**
 * Structural adapter for Forge 1.20's {@code AbstractIngredient}, which NeoForge 1.21 replaced with
 * {@link ICustomIngredient}. The SRG-named methods intentionally preserve the old JVM descriptors.
 */
public abstract class LegacyAbstractIngredient implements ICustomIngredient {
    private final ItemStack[] constructorItems;

    protected LegacyAbstractIngredient() {
        this.constructorItems = null;
    }

    protected LegacyAbstractIngredient(ItemStack stack) {
        this.constructorItems = new ItemStack[] {stack};
    }

    protected LegacyAbstractIngredient(Stream<?> values) {
        this.constructorItems = values.flatMap(LegacyAbstractIngredient::valueItems).toArray(ItemStack[]::new);
    }

    @Override
    public Stream<ItemStack> getItems() {
        return Arrays.stream(m_43908_());
    }

    @Override
    public IngredientType<?> getType() {
        return LegacyIngredientTypes.typeFor(getSerializer());
    }

    public ItemStack[] m_43908_() {
        if (constructorItems == null) {
            throw new IllegalStateException("Legacy ingredient " + getClass().getName()
                    + " does not expose item stacks or use the legacy value constructor");
        }
        return constructorItems;
    }

    public IntList m_43931_() {
        IntArrayList ids = new IntArrayList();
        for (ItemStack stack : m_43908_()) {
            ids.add(ItemStack.hashItemAndComponents(stack));
        }
        return ids;
    }

    public boolean m_43947_() {
        return m_43908_().length == 0;
    }

    public abstract LegacyIngredientSerializer<? extends Ingredient> getSerializer();

    public abstract JsonElement m_43942_();

    public boolean checkInvalidation() {
        return false;
    }

    public void markValid() {}

    protected void invalidate() {}

    private static Stream<ItemStack> valueItems(Object value) {
        try {
            Object items = value.getClass().getMethod("getItems").invoke(value);
            if (items instanceof Stream<?> stream) {
                return stream.map(ItemStack.class::cast);
            }
            if (items instanceof Collection<?> collection) {
                return collection.stream().map(ItemStack.class::cast);
            }
            throw new IllegalStateException("Unsupported legacy ingredient value item collection "
                    + (items == null ? "null" : items.getClass().getName()));
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Cannot read item stacks from legacy ingredient value "
                    + value.getClass().getName(), e);
        }
    }
}
