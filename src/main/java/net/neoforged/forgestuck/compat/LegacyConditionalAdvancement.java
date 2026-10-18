package net.neoforged.forgestuck.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Function;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.conditions.ICondition;
import java.util.Objects;

/** Adapts Forge's single-variant conditional advancement builder to NeoForge's conditional advancement codec. */
public final class LegacyConditionalAdvancement {
    private LegacyConditionalAdvancement() {}

    public static final class Builder {
        private ICondition pendingCondition;
        private Advancement.Builder advancement;

        public Builder addCondition(ICondition condition) {
            if (advancement != null) {
                throw new UnsupportedOperationException(
                        "Legacy conditional advancement conditions cannot be changed after adding the variant");
            }
            if (pendingCondition != null) {
                throw new UnsupportedOperationException(
                        "Legacy conditional advancements with multiple conditions per variant are unsupported");
            }
            pendingCondition = Objects.requireNonNull(condition, "condition");
            return this;
        }

        public Builder addAdvancement(Advancement.Builder value) {
            if (advancement != null) {
                throw new UnsupportedOperationException(
                        "Legacy conditional advancements with multiple variants are unsupported");
            }
            if (pendingCondition == null) {
                throw new IllegalStateException("A condition must be added before its advancement");
            }
            advancement = Objects.requireNonNull(value, "advancement");
            return this;
        }

        public JsonObject write() {
            if (pendingCondition == null || advancement == null) {
                throw new IllegalStateException("Conditional advancement requires one condition and one advancement");
            }
            AdvancementHolder holder = advancement.build(
                    ResourceLocation.fromNamespaceAndPath("forgestuck", "conditional_advancement"));
            JsonObject json = serialize(holder.value());
            JsonArray conditions = new JsonArray();
            conditions.add(serializeCondition(pendingCondition));
            json.add("neoforge:conditions", conditions);
            return json;
        }

        private static JsonObject serialize(Advancement value) {
            try {
                Field codecField = Advancement.class.getField("CODEC");
                Class<?> codecType = codecField.getType();
                ClassLoader loader = codecType.getClassLoader();
                Class<?> dynamicOpsType = Class.forName("com.mojang.serialization.DynamicOps", true, loader);
                Class<?> jsonOpsType = Class.forName("com.mojang.serialization.JsonOps", true, loader);
                Object jsonOps = jsonOpsType.getField("INSTANCE").get(null);
                Class<?> registryType = Class.forName("net.minecraft.core.Registry", true, loader);
                Class<?> builtinsType = Class.forName("net.minecraft.core.registries.BuiltInRegistries", true, loader);
                Object registries = builtinsType.getField("REGISTRY").get(null);
                Class<?> registryAccessType = Class.forName("net.minecraft.core.RegistryAccess", true, loader);
                Object registryAccess = registryAccessType
                        .getMethod("fromRegistryOfRegistries", registryType).invoke(null, registries);
                Class<?> providerType = Class.forName("net.minecraft.core.HolderLookup$Provider", true, loader);
                Class<?> registryOpsType = Class.forName("net.minecraft.resources.RegistryOps", true, loader);
                Object ops = registryOpsType.getMethod("create", dynamicOpsType, providerType)
                        .invoke(null, jsonOps, registryAccess);
                Object codec = codecField.get(null);
                Object result = codecType.getMethod("encodeStart", dynamicOpsType, Object.class)
                        .invoke(codec, ops, value);
                Object element = result.getClass().getMethod("getOrThrow", Function.class)
                        .invoke(result, (Function<String, RuntimeException>) IllegalStateException::new);
                return JsonParser.parseString(element.toString()).getAsJsonObject();
            } catch (InvocationTargetException e) {
                throw new IllegalStateException("Could not serialize legacy conditional data", e.getCause());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Could not serialize legacy conditional data", e);
            }
        }

        private static JsonObject serializeCondition(ICondition value) {
            return LegacyConditions.serializeForDatagen(value);
        }
    }
}
