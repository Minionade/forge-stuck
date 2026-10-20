package net.neoforged.forgestuck.compat;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Encoder;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.datafixers.util.Pair;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Adapts 1.20 recipe serializer methods to the codec-based 1.21 recipe API. */
public final class LegacyRecipeSerializerCodec {
    private static final ResourceLocation LEGACY_RECIPE_ID =
            ResourceLocation.fromNamespaceAndPath("forgestuck", "legacy_recipe");
    private static final ConcurrentHashMap<Class<?>, Methods> METHODS = new ConcurrentHashMap<>();

    private LegacyRecipeSerializerCodec() {}

    public static ResourceLocation legacyRecipeId() {
        return LEGACY_RECIPE_ID;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Recipe<?>> MapCodec<T> codec(RecipeSerializer<T> serializer) {
        Codec<T> legacy = Codec.of(new Encoder<>() {
            @Override
            public <O> DataResult<O> encode(T input, DynamicOps<O> ops, O prefix) {
                return DataResult.error(() -> "Legacy recipe serializers do not provide JSON encoding");
            }
        }, new Decoder<>() {
            @Override
            public <O> DataResult<Pair<T, O>> decode(DynamicOps<O> ops, O input) {
                return LegacyRecipeSerializerCodec.decode(serializer, new Dynamic<>(ops, input))
                        .map(recipe -> Pair.of(recipe, ops.empty()));
            }
        });
        return MapCodec.assumeMapUnsafe(legacy);
    }

    public static <T extends Recipe<?>> StreamCodec<RegistryFriendlyByteBuf, T> streamCodec(
            RecipeSerializer<T> serializer) {
        Methods methods = methods(serializer.getClass());
        return StreamCodec.of(
                (buffer, recipe) -> invokeWriter(serializer, methods.toNetwork(), buffer, recipe),
                buffer -> invokeReader(serializer, methods.fromNetwork(), buffer));
    }

    private static <T extends Recipe<?>> DataResult<T> decode(RecipeSerializer<T> serializer, Dynamic<?> dynamic) {
        var json = dynamic.convert(JsonOps.INSTANCE).getValue();
        if (!json.isJsonObject()) {
            return DataResult.error(() -> "Legacy recipe serializer expected a JSON object");
        }
        try {
            return DataResult.success(invokeJson(serializer, methods(serializer.getClass()).fromJson(),
                    json.getAsJsonObject()));
        } catch (RuntimeException exception) {
            return DataResult.error(() -> "Legacy recipe serializer failed to decode: " + exception.getMessage());
        }
    }

    private static Methods methods(Class<?> type) {
        return METHODS.computeIfAbsent(type, LegacyRecipeSerializerCodec::findMethods);
    }

    private static Methods findMethods(Class<?> type) {
        Class<?>[] jsonParameters = {ResourceLocation.class, JsonObject.class};
        Class<?>[] networkReadParameters = {ResourceLocation.class, FriendlyByteBuf.class};
        Class<?>[] networkWriteParameters = {FriendlyByteBuf.class, Recipe.class};
        return new Methods(
                findMethod(type, new String[] {"fromJson", "m_6729_"}, jsonParameters, Recipe.class),
                findMethod(type, new String[] {"fromNetworkSafe", "fromNetwork", "m_8005_"},
                        networkReadParameters, Recipe.class),
                findMethod(type, new String[] {"toNetworkSafe", "toNetwork", "m_6178_"},
                        networkWriteParameters, void.class));
    }

    private static Method findMethod(Class<?> type, String[] names, Class<?>[] parameters, Class<?> result) {
        for (String name : names) {
            try {
                Method method = type.getMethod(name, parameters);
                if (result.isAssignableFrom(method.getReturnType())
                        || result == void.class && method.getReturnType() == void.class) return method;
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new IllegalStateException("Legacy recipe serializer " + type.getName()
                + " does not expose a compatible method for " + String.join("/", names));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Recipe<?>> T invokeJson(RecipeSerializer<T> serializer, Method method, JsonObject json) {
        return (T) invoke(method, serializer, LEGACY_RECIPE_ID, json);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Recipe<?>> T invokeReader(
            RecipeSerializer<T> serializer, Method method, FriendlyByteBuf buffer) {
        return (T) invoke(method, serializer, LEGACY_RECIPE_ID, buffer);
    }

    private static <T extends Recipe<?>> void invokeWriter(
            RecipeSerializer<T> serializer, Method method, FriendlyByteBuf buffer, T recipe) {
        try {
            invoke(method, serializer, buffer, recipe);
        } catch (io.netty.handler.codec.EncoderException exception) {
            throw new io.netty.handler.codec.EncoderException("Failed to encode legacy recipe "
                    + recipe.getClass().getName() + " [id=" + recipeId(recipe) + "] with serializer "
                    + serializer.getClass().getName(), exception);
        }
    }

    private static String recipeId(Recipe<?> recipe) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return "unknown";
        return server.getRecipeManager().getRecipes().stream()
                .filter(holder -> holder.value() == recipe)
                .map(RecipeHolder::id)
                .map(ResourceLocation::toString)
                .findFirst()
                .orElse("unregistered");
    }

    private static Object invoke(Method method, Object receiver, Object... arguments) {
        try {
            return method.invoke(receiver, arguments);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot access legacy recipe serializer method " + method, exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Legacy recipe serializer method failed: " + method, cause);
        }
    }

    private record Methods(Method fromJson, Method fromNetwork, Method toNetwork) {}
}
