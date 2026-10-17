package net.neoforged.forgestuck.compat;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.conditions.ICondition;

/** Stand-in for Forge 1.20.1's {@code IConditionSerializer}; erased signatures match so legacy implementers link. */
public interface LegacyConditionSerializer<T extends ICondition> {
    void write(JsonObject json, T value);

    T read(JsonObject json);

    ResourceLocation getID();

    default JsonObject getJson(T value) {
        JsonObject json = new JsonObject();
        write(json, value);
        json.addProperty("type", getID().toString());
        return json;
    }
}