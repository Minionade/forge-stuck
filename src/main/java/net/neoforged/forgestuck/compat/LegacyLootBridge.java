package net.neoforged.forgestuck.compat;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonSerializationContext;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.minecraft.world.level.storage.loot.providers.nbt.LootNbtProviderType;
import net.minecraft.world.level.storage.loot.providers.number.LootNumberProviderType;
import net.minecraft.world.level.storage.loot.providers.score.LootScoreProviderType;

/**
 * Wraps a 1.20-style JSON {@link LegacyLootSerializer} as a 1.21 {@link MapCodec} so
 * {@code new LootXxxType(Serializer)} call sites can be rewritten to the {@code xxxType} factories.
 *
 * <p>Preserved: the serializer's own JSON read/write logic over a {@link JsonObject} holding the sibling fields of
 * the dispatch {@code type} key, including condition and function arrays routed through NeoForge codecs. Failures
 * become {@link DataResult} errors (never swallowed silently). Other nested loot types without a verified codec
 * adapter still fail explicitly.
 */
public final class LegacyLootBridge {
    private static final Gson GSON = new Gson();

    private static final JsonSerializationContext SERIALIZATION = new JsonSerializationContext() {
        @Override
        public JsonElement serialize(Object src) {
            return serializeLootValue(src, src == null ? null : src.getClass());
        }

        @Override
        public JsonElement serialize(Object src, Type typeOfSrc) {
            JsonElement encoded = encodeLootValue(src, typeOfSrc);
            if (encoded != null) return encoded;
            return GSON.toJsonTree(src, typeOfSrc);
        }
    };

    private static final JsonDeserializationContext DESERIALIZATION = new JsonDeserializationContext() {
        @Override
        @SuppressWarnings("unchecked")
        public <R> R deserialize(JsonElement json, Type typeOfT) {
            Object decoded = decodeLootValue(json, typeOfT);
            if (decoded != null) return (R) decoded;
            if (typeOfT instanceof Class<?> c && c.getName().startsWith("net.minecraft.world.level.storage.loot.")) {
                throw new UnsupportedOperationException("legacy loot JSON context cannot deserialize nested "
                        + c.getName() + " (Forge's loot Gson adapters do not exist in 1.21.1)");
            }
            return (R) GSON.fromJson(json, typeOfT);
        }
    };

    private LegacyLootBridge() {}

    private static JsonElement serializeLootValue(Object src, Type type) {
        JsonElement encoded = encodeLootValue(src, type);
        if (encoded != null) return encoded;
        return type == null ? GSON.toJsonTree(src) : GSON.toJsonTree(src, type);
    }

    private static JsonElement encodeLootValue(Object src, Type type) {
        if (src instanceof LootItemCondition condition) {
            return value(LootItemCondition.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, condition));
        }
        if (src instanceof LootItemFunction function) {
            return value(LootItemFunctions.ROOT_CODEC.encodeStart(JsonOps.INSTANCE, function));
        }
        if (src instanceof LootItemCondition[] conditions) {
            return value(LootItemCondition.DIRECT_CODEC.listOf().encodeStart(JsonOps.INSTANCE, List.of(conditions)));
        }
        if (src instanceof LootItemFunction[] functions) {
            return value(LootItemFunctions.ROOT_CODEC.listOf().encodeStart(JsonOps.INSTANCE, List.of(functions)));
        }
        return null;
    }

    private static Object decodeLootValue(JsonElement json, Type type) {
        if (type == LootItemCondition.class) {
            return value(LootItemCondition.DIRECT_CODEC.parse(JsonOps.INSTANCE, json));
        }
        if (type == LootItemFunction.class) {
            return value(LootItemFunctions.ROOT_CODEC.parse(JsonOps.INSTANCE, json));
        }
        if (type == LootItemCondition[].class) {
            return value(LootItemCondition.DIRECT_CODEC.listOf().parse(JsonOps.INSTANCE, json))
                    .toArray(LootItemCondition[]::new);
        }
        if (type == LootItemFunction[].class) {
            return value(LootItemFunctions.ROOT_CODEC.listOf().parse(JsonOps.INSTANCE, json))
                    .toArray(LootItemFunction[]::new);
        }
        return null;
    }

    private static <T> T value(DataResult<T> result) {
        return result.result().orElseThrow(() -> codecFailure(
                result.error().map(DataResult.PartialResult::message).orElse("loot codec returned no value")));
    }

    private static IllegalStateException codecFailure(String message) {
        return new IllegalStateException(message);
    }

    public static LootItemCondition[] conditions(Object value) {
        return arrayField(value, "conditions", LootItemCondition.class);
    }

    public static LootItemFunction[] functions(Object value) {
        return arrayField(value, "functions", LootItemFunction.class);
    }

    public static LootItemCondition[] predicates(Object value) {
        return arrayField(value, "predicates", LootItemCondition.class);
    }

    public static int intField(Object value, String name) {
        Object fieldValue = fieldValue(value, name);
        if (fieldValue instanceof Number number) return number.intValue();
        throw new IllegalStateException("Legacy loot field " + value.getClass().getName() + "." + name
                + " is not numeric: " + fieldValue);
    }

    private static <T> T[] arrayField(Object value, String name, Class<T> type) {
        Object fieldValue = fieldValue(value, name);
        if (fieldValue instanceof List<?> list) {
            return list.stream().map(type::cast).toArray(size -> (T[]) java.lang.reflect.Array.newInstance(type, size));
        }
        if (fieldValue instanceof Object[] array) {
            T[] result = (T[]) java.lang.reflect.Array.newInstance(type, array.length);
            for (int i = 0; i < array.length; i++) result[i] = type.cast(array[i]);
            return result;
        }
        throw new IllegalStateException("Legacy loot field " + value.getClass().getName() + "." + name
                + " has unsupported value " + fieldValue);
    }

    private static Object fieldValue(Object value, String name) {
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(value);
            } catch (NoSuchFieldException ignored) {
                // Continue through the class hierarchy.
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot read legacy loot field " + value.getClass().getName() + "."
                        + name, e);
            }
        }
        throw new IllegalStateException("Missing legacy loot field " + value.getClass().getName() + "." + name);
    }

    public static <T> MapCodec<T> codec(LegacyLootSerializer<T> serializer) {
        return new MapCodec<>() {
            @Override
            public <O> Stream<O> keys(DynamicOps<O> ops) {
                return Stream.empty();
            }

            @Override
            public <O> DataResult<T> decode(DynamicOps<O> ops, MapLike<O> input) {
                JsonObject json = new JsonObject();
                try {
                    for (Pair<O, O> entry : (Iterable<Pair<O, O>>) input.entries()::iterator) {
                        var key = ops.getStringValue(entry.getFirst());
                        if (key.result().isEmpty()) return DataResult.error(() -> "non-string key in loot object");
                        json.add(key.result().get(), ops.convertTo(JsonOps.INSTANCE, entry.getSecond()));
                    }
                    return DataResult.success(serializer.deserialize(json, DESERIALIZATION));
                } catch (RuntimeException e) {
                    return DataResult.error(() -> "legacy loot serializer " + serializer.getClass().getName()
                            + " failed to deserialize: " + e);
                }
            }

            @Override
            public <O> RecordBuilder<O> encode(T input, DynamicOps<O> ops, RecordBuilder<O> prefix) {
                JsonObject json = new JsonObject();
                try {
                    serializer.serialize(json, input, SERIALIZATION);
                } catch (RuntimeException e) {
                    return prefix.withErrorsFrom(DataResult.error(() -> "legacy loot serializer "
                            + serializer.getClass().getName() + " failed to serialize: " + e));
                }
                for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                    prefix.add(entry.getKey(), JsonOps.INSTANCE.convertTo(ops, entry.getValue()));
                }
                return prefix;
            }
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static LootItemFunctionType functionType(LegacyLootSerializer serializer) {
        return new LootItemFunctionType(codec(serializer));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static LootItemConditionType conditionType(LegacyLootSerializer serializer) {
        return new LootItemConditionType(codec(serializer));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static LootNbtProviderType nbtProviderType(LegacyLootSerializer serializer) {
        return new LootNbtProviderType(codec(serializer));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static LootPoolEntryType entryType(LegacyLootSerializer serializer) {
        return new LootPoolEntryType(codec(serializer));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static LootNumberProviderType numberProviderType(LegacyLootSerializer serializer) {
        return new LootNumberProviderType(codec(serializer));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static LootScoreProviderType scoreProviderType(LegacyLootSerializer serializer) {
        return new LootScoreProviderType(codec(serializer));
    }
}
