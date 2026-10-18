package net.neoforged.forgestuck.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;

public final class LegacyVanillaIngredientSerializer {
    public static final LegacyVanillaIngredientSerializer INSTANCE = new LegacyVanillaIngredientSerializer();

    private LegacyVanillaIngredientSerializer() {}

    public Ingredient parse(JsonObject json) {
        if (json.has("item") == json.has("tag")) {
            throw new JsonParseException("Ingredient must specify exactly one of 'item' or 'tag'");
        }
        ResourceLocation id = ResourceLocation.tryParse(json.get(json.has("item") ? "item" : "tag").getAsString());
        if (id == null) throw new JsonParseException("Invalid ingredient identifier");
        if (json.has("tag")) return Ingredient.of(TagKey.create(Registries.ITEM, id));
        Item item = BuiltInRegistries.ITEM.getOptional(id)
                .orElseThrow(() -> new JsonParseException("Unknown item: " + id));
        return Ingredient.of(item);
    }
}
