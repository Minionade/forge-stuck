package net.neoforged.forgestuck.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Encoder;
import com.mojang.serialization.JsonOps;
import com.mojang.datafixers.util.Pair;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;

/** Adapts Forge's one-argument criterion registration API to the keyed 1.21.1 registration method. */
public final class LegacyCriteriaTriggers {
    private LegacyCriteriaTriggers() {}

    public static <T extends CriterionTrigger<?>> T register(T trigger) {
        return CriteriaTriggers.register(legacyId(trigger).toString(), trigger);
    }

    public static <T extends SimpleCriterionTrigger.SimpleInstance> Codec<T> codec(
            SimpleCriterionTrigger<T> trigger) {
        Method parser = legacyParser(trigger.getClass());
        return Codec.of(new Encoder<>() {
            @Override
            public <O> DataResult<O> encode(T input, DynamicOps<O> ops, O prefix) {
                try {
                    JsonObject json = (JsonObject) legacySerializer(input.getClass()).invoke(input, new Object[] {null});
                    return DataResult.success(JsonOps.INSTANCE.convertTo(ops, json));
                } catch (IllegalAccessException | InvocationTargetException e) {
                    Throwable cause = e instanceof InvocationTargetException invocation && invocation.getCause() != null
                            ? invocation.getCause() : e;
                    return DataResult.error(() -> "Could not encode legacy criterion "
                            + input.getClass().getName() + ": " + cause.getMessage());
                }
            }
        }, new Decoder<>() {
            @Override
            @SuppressWarnings("unchecked")
            public <O> DataResult<Pair<T, O>> decode(DynamicOps<O> ops, O input) {
                JsonElement element = ops.convertTo(JsonOps.INSTANCE, input);
                if (!element.isJsonObject()) {
                    return DataResult.error(() -> "Legacy criterion expected a JSON object");
                }
                JsonObject json = element.getAsJsonObject();
                ContextAwarePredicate player = null;
                if (json.has("player")) {
                    DataResult<ContextAwarePredicate> parsed =
                            ContextAwarePredicate.CODEC.parse(JsonOps.INSTANCE, json.get("player"));
                    if (parsed.error().isPresent()) {
                        return DataResult.error(() -> "Could not decode legacy criterion player predicate: "
                                + parsed.error().orElseThrow().message());
                    }
                    player = parsed.result().orElse(null);
                }
                try {
                    T decoded = (T) parser.invoke(trigger, json, player, null);
                    return DataResult.success(Pair.of(decoded, ops.empty()));
                } catch (IllegalAccessException | InvocationTargetException e) {
                    Throwable cause = e instanceof InvocationTargetException invocation && invocation.getCause() != null
                            ? invocation.getCause() : e;
                    return DataResult.error(() -> "Could not decode legacy criterion "
                            + trigger.getClass().getName() + ": " + cause.getMessage());
                }
            }
        });
    }

    private static Method legacyParser(Class<?> triggerType) {
        for (Class<?> type = triggerType; type != null; type = type.getSuperclass()) {
            try {
                Method method = type.getDeclaredMethod("createInstance",
                        JsonObject.class, ContextAwarePredicate.class, Object.class);
                if (!method.trySetAccessible()) {
                    throw new IllegalStateException("Legacy criterion parser is inaccessible: "
                            + triggerType.getName());
                }
                return method;
            } catch (NoSuchMethodException ignored) {
                // Continue through legacy trigger base classes.
            }
        }
        throw new IllegalStateException("Legacy criterion trigger " + triggerType.getName()
                + " has no adaptable createInstance(JsonObject, ContextAwarePredicate, Object) method");
    }

    private static Method legacySerializer(Class<?> instanceType) {
        for (Class<?> type = instanceType; type != null; type = type.getSuperclass()) {
            for (String name : new String[] {"m_7683_", "serializeToJson"}) {
                try {
                    Method method = type.getDeclaredMethod(name, Object.class);
                    if (!method.trySetAccessible()) {
                        throw new IllegalStateException("Legacy criterion serializer is inaccessible: "
                                + instanceType.getName());
                    }
                    return method;
                } catch (NoSuchMethodException ignored) {
                    // Continue through the legacy instance hierarchy.
                }
            }
        }
        throw new IllegalStateException("Legacy criterion instance " + instanceType.getName()
                + " has no adaptable JSON serializer");
    }

    private static ResourceLocation legacyId(CriterionTrigger<?> trigger) {
        Field id = null;
        for (Class<?> type = trigger.getClass(); type != null && id == null; type = type.getSuperclass()) {
            try {
                Field candidate = type.getDeclaredField("ID");
                if (Modifier.isStatic(candidate.getModifiers())) id = candidate;
            } catch (NoSuchFieldException ignored) {
                // The trigger may inherit its ID from a base class.
            }
        }
        if (id == null) {
            throw new IllegalStateException("Legacy criterion trigger " + trigger.getClass().getName()
                    + " has no static ID field");
        }
        try {
            if (!id.trySetAccessible()) {
                throw new IllegalStateException("Legacy criterion trigger ID field is inaccessible: "
                        + trigger.getClass().getName());
            }
            Object value = id.get(null);
            if (value instanceof ResourceLocation resourceLocation) return resourceLocation;
            throw new IllegalStateException("Legacy criterion trigger " + trigger.getClass().getName()
                    + " has a non-ResourceLocation ID field");
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Could not read legacy criterion trigger ID field from "
                    + trigger.getClass().getName(), e);
        }
    }
}
