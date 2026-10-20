package net.neoforged.forgestuck.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;

/** Strict item-and-component ingredient bridge for Forge's removed {@code StrictNBTIngredient}. */
public class LegacyStrictNBTIngredient extends LegacyAbstractIngredient {
    private static final String TYPE_ID = "forge:strict_nbt";
    private final ItemStack stack;

    public LegacyStrictNBTIngredient(ItemStack stack) {
        super(stack.copyWithCount(1));
        this.stack = stack.copyWithCount(1);
    }

    public static void register() {
        Serializer.INSTANCE.registerIfNeeded();
    }

    @Override
    public boolean test(ItemStack input) {
        return DataComponentIngredient.of(true, stack).getCustomIngredient().test(input);
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public LegacyIngredientSerializer<? extends Ingredient> getSerializer() {
        return Serializer.INSTANCE;
    }

    @Override
    public JsonElement m_43942_() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE_ID);
        json.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        CustomData customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (customData != null && !customData.isEmpty()) {
            json.addProperty("nbt", customData.copyTag().toString());
        }
        return json;
    }

    private static ItemStack parseStack(JsonObject json) {
        if (!json.has("item") || !json.get("item").isJsonPrimitive()) {
            throw new JsonSyntaxException("Strict NBT ingredient requires an item id");
        }
        ResourceLocation id = ResourceLocation.parse(json.get("item").getAsString());
        var item = BuiltInRegistries.ITEM.getOptional(id)
                .orElseThrow(() -> new JsonSyntaxException("Unknown item '" + id + "'"));
        ItemStack stack = new ItemStack(item);
        if (json.has("nbt")) {
            JsonElement element = json.get("nbt");
            Tag value;
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                try {
                    value = TagParser.parseTag(element.getAsString());
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                    throw new JsonSyntaxException("Invalid strict NBT ingredient payload", e);
                }
            } else {
                value = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, element);
            }
            if (!(value instanceof CompoundTag tag)) {
                throw new JsonSyntaxException("Strict NBT ingredient payload must be a compound");
            }
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return stack;
    }

    public static final class Serializer implements LegacyIngredientSerializer<Ingredient> {
        private static final ResourceLocation ID = ResourceLocation.parse(TYPE_ID);
        public static final Serializer INSTANCE = new Serializer();
        private volatile boolean registered;

        private Serializer() {}

        private synchronized void registerIfNeeded() {
            if (registered) return;
            LegacyIngredientTypes.register(ID, this);
            registered = true;
        }

        @Override
        public Ingredient parse(JsonObject json) {
            return new Ingredient(new LegacyStrictNBTIngredient(parseStack(json)));
        }

        @Override
        public void write(net.minecraft.network.FriendlyByteBuf buffer, Ingredient ingredient) {
            if (!(ingredient.getCustomIngredient() instanceof LegacyStrictNBTIngredient strict)) {
                throw new IllegalArgumentException("Expected a strict NBT ingredient, got " + ingredient);
            }
            if (!(buffer instanceof RegistryFriendlyByteBuf registryBuffer)) {
                throw new IllegalArgumentException("Strict NBT ingredients require a registry-friendly buffer");
            }
            ItemStack.STREAM_CODEC.encode(registryBuffer, strict.stack);
        }

        @Override
        public Ingredient parse(net.minecraft.network.FriendlyByteBuf buffer) {
            if (!(buffer instanceof RegistryFriendlyByteBuf registryBuffer)) {
                throw new IllegalArgumentException("Strict NBT ingredients require a registry-friendly buffer");
            }
            return new Ingredient(new LegacyStrictNBTIngredient(ItemStack.STREAM_CODEC.decode(registryBuffer)));
        }
    }
}
