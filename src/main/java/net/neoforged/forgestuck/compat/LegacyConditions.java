package net.neoforged.forgestuck.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import java.lang.reflect.InvocationTargetException;
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

    public static ResourceLocation getID(LegacyIngredientSerializer<?> serializer) {
        return LegacyIngredientTypes.idFor(serializer);
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

    /** Serializes a single condition without dispatching through the condition-codec registry. */
    public static JsonObject serializeForDatagen(ICondition condition) {
        Object codec = conditionCodec(condition);
        Map.Entry<ResourceLocation, MapCodec<ICondition>> legacy = CODECS.entrySet().stream()
                .filter(entry -> codec == entry.getValue())
                .findFirst().orElse(null);
        ResourceLocation id = legacy == null ? builtInId(condition) : legacy.getKey();
        if (id == null) {
            id = registeredCodecId(codec);
        }
        if (id == null) {
            throw new UnsupportedOperationException("No registered NeoForge condition codec for "
                    + condition.getClass().getName());
        }
        JsonObject json = encodeMapCodec(codec, condition);
        json.addProperty("type", id.toString());
        return json;
    }

    private static Object conditionCodec(ICondition condition) {
        try {
            return ICondition.class.getMethod("codec").invoke(condition);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not access condition codec for " + condition.getClass().getName(), e);
        }
    }

    private static ResourceLocation builtInId(ICondition condition) {
        return switch (condition.getClass().getName()) {
            case "net.neoforged.neoforge.common.conditions.TrueCondition" ->
                    ResourceLocation.fromNamespaceAndPath("neoforge", "true");
            case "net.neoforged.neoforge.common.conditions.FalseCondition" ->
                    ResourceLocation.fromNamespaceAndPath("neoforge", "false");
            case "net.neoforged.neoforge.common.conditions.NotCondition" ->
                    ResourceLocation.fromNamespaceAndPath("neoforge", "not");
            case "net.neoforged.neoforge.common.conditions.AndCondition" ->
                    ResourceLocation.fromNamespaceAndPath("neoforge", "and");
            case "net.neoforged.neoforge.common.conditions.OrCondition" ->
                    ResourceLocation.fromNamespaceAndPath("neoforge", "or");
            case "net.neoforged.neoforge.common.conditions.ModLoadedCondition" ->
                    ResourceLocation.fromNamespaceAndPath("neoforge", "mod_loaded");
            case "net.neoforged.neoforge.common.conditions.ItemExistsCondition" ->
                    ResourceLocation.fromNamespaceAndPath("neoforge", "item_exists");
            default -> null;
        };
    }

    private static ResourceLocation registeredCodecId(Object codec) {
        try {
            Object key = NeoForgeRegistries.CONDITION_SERIALIZERS.getClass()
                    .getMethod("getKey", Object.class).invoke(NeoForgeRegistries.CONDITION_SERIALIZERS, codec);
            return key == null ? null : ResourceLocation.parse(key.toString());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not look up condition codec id", e);
        }
    }

    private static JsonObject encodeMapCodec(Object mapCodec, ICondition condition) {
        try {
            ClassLoader loader = mapCodec.getClass().getClassLoader();
            Object codec = mapCodec.getClass().getMethod("codec").invoke(mapCodec);
            Class<?> codecType = Class.forName("com.mojang.serialization.Codec", true, loader);
            Class<?> dynamicOpsType = Class.forName("com.mojang.serialization.DynamicOps", true, loader);
            Class<?> jsonOpsType = Class.forName("com.mojang.serialization.JsonOps", true, loader);
            Object jsonOps = jsonOpsType.getField("INSTANCE").get(null);
            Object result = codecType.getMethod("encodeStart", dynamicOpsType, Object.class)
                    .invoke(codec, jsonOps, condition);
            Object element = result.getClass().getMethod("getOrThrow", java.util.function.Function.class)
                    .invoke(result, (java.util.function.Function<String, RuntimeException>) IllegalStateException::new);
            return JsonParser.parseString(element.toString()).getAsJsonObject();
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Could not serialize condition " + condition.getClass().getName(),
                    e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not serialize condition " + condition.getClass().getName(), e);
        }
    }
}