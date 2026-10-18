package net.neoforged.forgestuck.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Registers legacy ingredient serializers as NeoForge custom ingredient types and map codecs. */
public final class LegacyIngredientTypes {
    private static final Map<ResourceLocation, IngredientType<LegacyAbstractIngredient>> TYPES =
            new ConcurrentHashMap<>();
    private static final Map<LegacyIngredientSerializer<?>, IngredientType<LegacyAbstractIngredient>> TYPES_BY_SERIALIZER =
            new java.util.IdentityHashMap<>();

    private LegacyIngredientTypes() {}

    public static <T extends Ingredient> LegacyIngredientSerializer<T> register(
            ResourceLocation id, LegacyIngredientSerializer<T> serializer) {
        MapCodec<LegacyAbstractIngredient> codec = LegacyLootBridge.codec(new LegacyLootSerializer<>() {
            @Override
            public void serialize(JsonObject json, LegacyAbstractIngredient ingredient,
                    JsonSerializationContext context) {
                JsonElement encoded = ingredient.m_43942_();
                if (!encoded.isJsonObject()) {
                    throw new IllegalStateException("Legacy ingredient " + ingredient.getClass().getName()
                            + " encoded non-object JSON");
                }
                JsonObject fields = encoded.getAsJsonObject();
                for (Map.Entry<String, JsonElement> field : fields.entrySet()) {
                    if (!field.getKey().equals("type")) {
                        json.add(field.getKey(), field.getValue().deepCopy());
                    }
                }
            }

            @Override
            public LegacyAbstractIngredient deserialize(JsonObject json,
                    com.google.gson.JsonDeserializationContext context) {
                Ingredient parsed = serializer.parse(json);
                if (parsed == null || parsed.getCustomIngredient() == null) {
                    throw new IllegalStateException("Legacy ingredient serializer " + id
                            + " did not return a custom ingredient");
                }
                if (!(parsed.getCustomIngredient() instanceof LegacyAbstractIngredient legacy)) {
                    throw new IllegalStateException("Legacy ingredient serializer " + id + " returned "
                            + parsed.getCustomIngredient().getClass().getName()
                            + " instead of an AbstractIngredient subclass");
                }
                return legacy;
            }
        });
        IngredientType<LegacyAbstractIngredient> type = new IngredientType<>(codec);
        if (TYPES.putIfAbsent(id, type) != null) {
            throw new IllegalStateException("Duplicate legacy ingredient serializer " + id);
        }
        synchronized (TYPES_BY_SERIALIZER) {
            if (TYPES_BY_SERIALIZER.putIfAbsent(serializer, type) != null) {
                TYPES.remove(id, type);
                throw new IllegalStateException("Legacy ingredient serializer is registered more than once: " + id);
            }
        }

        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(RegisterEvent.class, event -> {
            if (event.getRegistryKey().equals(NeoForgeRegistries.Keys.INGREDIENT_TYPES)) {
                event.register(NeoForgeRegistries.Keys.INGREDIENT_TYPES, id, () -> type);
            }
        });
        return serializer;
    }

    static IngredientType<?> typeFor(LegacyIngredientSerializer<?> serializer) {
        synchronized (TYPES_BY_SERIALIZER) {
            IngredientType<?> type = TYPES_BY_SERIALIZER.get(serializer);
            if (type == null) {
                throw new IllegalStateException("Legacy ingredient serializer " + serializer.getClass().getName()
                        + " was not registered through CraftingHelper.register");
            }
            return type;
        }
    }
}
