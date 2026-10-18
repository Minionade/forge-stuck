package net.neoforged.forgestuck.compat;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.crafting.Ingredient;
import com.google.gson.JsonObject;

/** Descriptor-compatible stand-in for Forge 1.20's {@code IIngredientSerializer}. */
public interface LegacyIngredientSerializer<T extends Ingredient> {
    T parse(JsonObject json);

    void write(FriendlyByteBuf buffer, T ingredient);

    T parse(FriendlyByteBuf buffer);
}
