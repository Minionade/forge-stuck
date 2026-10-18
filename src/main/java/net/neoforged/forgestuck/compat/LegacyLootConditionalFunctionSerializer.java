package net.neoforged.forgestuck.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonSerializationContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/** Forge 1.20 conditional loot-function serializer contract, adapted to codec-backed function types. */
public abstract class LegacyLootConditionalFunctionSerializer<T extends LootItemConditionalFunction>
        implements LegacyLootSerializer<T> {
    @Override
    public void serialize(JsonObject json, T value, JsonSerializationContext context) {
        LootItemCondition[] conditions = LegacyLootBridge.predicates(value);
        if (conditions.length != 0) json.add("conditions", context.serialize(conditions));
    }

    @Override
    public final T deserialize(JsonObject json, JsonDeserializationContext context) {
        LootItemCondition[] conditions = json.has("conditions")
                ? context.deserialize(json.get("conditions"), LootItemCondition[].class)
                : new LootItemCondition[0];
        return deserialize(json, context, conditions);
    }

    public abstract T deserialize(JsonObject json, JsonDeserializationContext context, LootItemCondition[] conditions);
}
