package net.neoforged.forgestuck.compat;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;

/**
 * Stand-in for Minecraft 1.20's {@code net.minecraft.world.level.storage.loot.Serializer}, removed in 1.21 where
 * loot types carry a {@code MapCodec}. Erased signatures match the 1.20 interface so legacy implementations link.
 */
public interface LegacyLootSerializer<T> {
    void serialize(JsonObject json, T value, JsonSerializationContext context);

    T deserialize(JsonObject json, JsonDeserializationContext context);
}
