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
import java.lang.reflect.Type;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.minecraft.world.level.storage.loot.providers.nbt.LootNbtProviderType;
import net.minecraft.world.level.storage.loot.providers.number.LootNumberProviderType;
import net.minecraft.world.level.storage.loot.providers.score.LootScoreProviderType;

/**
 * Wraps a 1.20-style JSON {@link LegacyLootSerializer} as a 1.21 {@link MapCodec} so
 * {@code new LootXxxType(Serializer)} call sites can be rewritten to the {@code xxxType} factories.
 *
 * <p>Preserved: the serializer's own JSON read/write logic over a {@link JsonObject} holding the sibling fields of
 * the dispatch {@code type} key; failures become {@link DataResult} errors (never swallowed silently).
 * Not preserved: Forge's loot Gson context. The contexts passed to the serializer are plain-Gson based, so nested
 * loot elements (conditions/functions deserialized through the context) cannot be resolved and fail with an error
 * naming the unsupported nested type.
 */
public final class LegacyLootBridge {
    private static final Gson GSON = new Gson();

    private static final JsonSerializationContext SERIALIZATION = new JsonSerializationContext() {
        @Override
        public JsonElement serialize(Object src) {
            return GSON.toJsonTree(src);
        }

        @Override
        public JsonElement serialize(Object src, Type typeOfSrc) {
            return GSON.toJsonTree(src, typeOfSrc);
        }
    };

    private static final JsonDeserializationContext DESERIALIZATION = new JsonDeserializationContext() {
        @Override
        @SuppressWarnings("unchecked")
        public <R> R deserialize(JsonElement json, Type typeOfT) {
            if (typeOfT instanceof Class<?> c && c.getName().startsWith("net.minecraft.world.level.storage.loot.")) {
                throw new UnsupportedOperationException("legacy loot JSON context cannot deserialize nested "
                        + c.getName() + " (Forge's loot Gson adapters do not exist in 1.21.1)");
            }
            return (R) GSON.fromJson(json, typeOfT);
        }
    };

    private LegacyLootBridge() {}

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
