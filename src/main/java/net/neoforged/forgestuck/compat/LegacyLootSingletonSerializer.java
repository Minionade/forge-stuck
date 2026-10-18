package net.neoforged.forgestuck.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonSerializationContext;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/** Forge 1.20 singleton-entry serializer contract, including weight, quality, functions, and conditions. */
public abstract class LegacyLootSingletonSerializer<T extends LootPoolSingletonContainer>
        extends LegacyLootEntrySerializer<T> {
    @Override
    public void serializeCustom(JsonObject json, T value, JsonSerializationContext context) {
        int weight = (int) LegacyLootBridge.intField(value, "weight");
        int quality = (int) LegacyLootBridge.intField(value, "quality");
        if (weight != 1) json.addProperty("weight", weight);
        if (quality != 0) json.addProperty("quality", quality);
        LootItemFunction[] functions = LegacyLootBridge.functions(value);
        if (functions.length != 0) json.add("functions", context.serialize(functions));
    }

    @Override
    public final T deserializeCustom(JsonObject json, JsonDeserializationContext context,
            LootItemCondition[] conditions) {
        int weight = json.has("weight") ? json.get("weight").getAsInt() : 1;
        int quality = json.has("quality") ? json.get("quality").getAsInt() : 0;
        LootItemFunction[] functions = json.has("functions")
                ? context.deserialize(json.get("functions"), LootItemFunction[].class)
                : new LootItemFunction[0];
        return deserialize(json, context, weight, quality, conditions, functions);
    }

    protected abstract T deserialize(JsonObject json, JsonDeserializationContext context, int weight, int quality,
            LootItemCondition[] conditions, LootItemFunction[] functions);
}
