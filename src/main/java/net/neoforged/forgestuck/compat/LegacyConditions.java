package net.neoforged.forgestuck.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Replacement for the condition part of Forge 1.20.1's {@code CraftingHelper}. Legacy JSON serializers are wrapped
 * as {@link MapCodec}s and registered in NeoForge's condition-codec registry during {@link RegisterEvent} on the
 * registering mod's event bus (never early); JSON lookup/serialization goes through NeoForge's {@code ICondition.CODEC}.
 *
 * <p>Preserved: serializer read/write logic, condition ids, {@code test(IContext)}. Not preserved: Forge's built-in
 * condition type ids ({@code forge:and}, ...) and builtin And/Or constructors; registering after the registry event
 * (e.g. during setup) is too late and is reported as an error.
 */
public final class LegacyConditions {
    private static final Map<ResourceLocation, MapCodec<ICondition>> CODECS = new ConcurrentHashMap<>();

    private LegacyConditions() {}

    @SuppressWarnings("unchecked")
    public static <T extends ICondition> LegacyConditionSerializer<T> register(LegacyConditionSerializer<T> serializer) {
        ResourceLocation id = serializer.getID();
        MapCodec<ICondition> codec = LegacyLootBridge.codec(new LegacyLootSerializer<ICondition>() {
            @Override
            public void serialize(JsonObject json, ICondition value, com.google.gson.JsonSerializationContext c) {
                serializer.write(json, (T) value);
            }

            @Override
            public ICondition deserialize(JsonObject json, com.google.gson.JsonDeserializationContext c) {
                return serializer.read(json);
            }
        });
        if (CODECS.putIfAbsent(id, codec) != null) {
            throw new IllegalStateException("Duplicate legacy condition serializer " + id);
        }
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(RegisterEvent.class, event -> {
            if (event.getRegistryKey().equals(NeoForgeRegistries.Keys.CONDITION_CODECS)) {
                event.register(NeoForgeRegistries.Keys.CONDITION_CODECS, id, () -> codec);
            }
        });
        return serializer;
    }

    public static <T extends net.minecraft.world.item.crafting.Ingredient> LegacyIngredientSerializer<T> register(
            ResourceLocation id, LegacyIngredientSerializer<T> serializer) {
        return LegacyIngredientTypes.register(id, serializer);
    }

    /** Codec instance registered for a legacy condition id; used by generated {@code codec()} bridges. */
    public static MapCodec<? extends ICondition> codecFor(ResourceLocation id) {
        MapCodec<ICondition> codec = CODECS.get(id);
        if (codec == null) {
            throw new UnsupportedOperationException("legacy condition " + id
                    + " has no registered IConditionSerializer (CraftingHelper.register was not called for it)");
        }
        return codec;
    }

    public static ICondition getCondition(JsonObject json) {
        return ICondition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(com.google.gson.JsonSyntaxException::new);
    }

    public static boolean processConditions(JsonArray conditions, ICondition.IContext context) {
        for (JsonElement element : conditions) {
            if (!element.isJsonObject()) {
                throw new com.google.gson.JsonSyntaxException("Conditions must be an array of JsonObjects");
            }
            if (!getCondition(element.getAsJsonObject()).test(context)) return false;
        }
        return true;
    }

    public static boolean processConditions(JsonObject json, String memberName, ICondition.IContext context) {
        return !json.has(memberName) || processConditions(json.getAsJsonArray(memberName), context);
    }

    public static JsonObject serialize(ICondition condition) {
        return ICondition.CODEC.encodeStart(JsonOps.INSTANCE, condition)
                .getOrThrow(IllegalArgumentException::new).getAsJsonObject();
    }

    public static JsonArray serialize(ICondition... conditions) {
        JsonArray array = new JsonArray();
        for (ICondition condition : conditions) array.add(serialize(condition));
        return array;
    }
}