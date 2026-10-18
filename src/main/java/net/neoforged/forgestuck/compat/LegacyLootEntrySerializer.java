package net.neoforged.forgestuck.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonSerializationContext;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/** Forge 1.20 loot-entry serializer contract, adapted for 1.21's codec-backed loot types. */
public abstract class LegacyLootEntrySerializer<T extends LootPoolEntryContainer> implements LegacyLootSerializer<T> {
    @Override
    public final void serialize(JsonObject json, T value, JsonSerializationContext context) {
        LootItemCondition[] conditions = LegacyLootBridge.conditions(value);
        if (conditions.length != 0) json.add("conditions", context.serialize(conditions));
        serializeCustom(json, value, context);
    }

    @Override
    public final T deserialize(JsonObject json, JsonDeserializationContext context) {
        LootItemCondition[] conditions = json.has("conditions")
                ? context.deserialize(json.get("conditions"), LootItemCondition[].class)
                : new LootItemCondition[0];
        return deserializeCustom(json, context, conditions);
    }

    public abstract void serializeCustom(JsonObject json, T value, JsonSerializationContext context);

    public abstract T deserializeCustom(JsonObject json, JsonDeserializationContext context,
            LootItemCondition[] conditions);
}
