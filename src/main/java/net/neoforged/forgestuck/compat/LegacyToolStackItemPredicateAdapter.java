package net.neoforged.forgestuck.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.ItemSubPredicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Bridges Tinkers' legacy ItemPredicate subclass into 1.21's registered item-subpredicate model. */
public final class LegacyToolStackItemPredicateAdapter implements ItemSubPredicate {
    private static final ResourceLocation TYPE_ID = ResourceLocation.fromNamespaceAndPath("forgestuck", "tool_stack");
    private static final ResourceLocation LEGACY_ID = ResourceLocation.fromNamespaceAndPath("tconstruct", "tool_stack");
    private static final Map<ResourceLocation, Function<JsonObject, ?>> DECODERS = new ConcurrentHashMap<>();
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final Codec<LegacyToolStackItemPredicateAdapter> CODEC = Codec.PASSTHROUGH.xmap(
            dynamic -> decode(dynamic.convert(JsonOps.INSTANCE).getValue().getAsJsonObject()),
            value -> new Dynamic<>(JsonOps.INSTANCE, encode(value)));
    private static final ItemSubPredicate.Type<LegacyToolStackItemPredicateAdapter> TYPE =
            new ItemSubPredicate.Type<>(CODEC);

    private final Object predicate;

    private LegacyToolStackItemPredicateAdapter(Object predicate) {
        this.predicate = predicate;
    }

    public static void install(IEventBus bus) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        bus.addListener(RegisterEvent.class, event -> {
            if (event.getRegistryKey().equals(Registries.ITEM_SUB_PREDICATE_TYPE)) {
                event.register(Registries.ITEM_SUB_PREDICATE_TYPE, TYPE_ID, () -> TYPE);
            }
        });
    }

    public static void register(ResourceLocation id, Function<JsonObject, ?> decoder) {
        Function<JsonObject, ?> previous = DECODERS.putIfAbsent(id, decoder);
        if (previous != null && previous != decoder) {
            throw new IllegalStateException("Duplicate legacy item predicate decoder " + id);
        }
    }

    public static ItemPredicate ofTool(Object predicate) {
        return ItemPredicate.Builder.item()
                .withSubPredicate(TYPE, new LegacyToolStackItemPredicateAdapter(predicate))
                .build();
    }

    public static ItemPredicate ofContext(Object predicate) {
        return ofTool(predicate);
    }

    @Override
    public boolean matches(ItemStack stack) {
        try {
            Method matches = java.util.Arrays.stream(predicate.getClass().getMethods())
                    .filter(method -> method.getParameterCount() == 1
                            && method.getParameterTypes()[0] == ItemStack.class
                            && method.getReturnType() == boolean.class)
                    .findFirst()
                    .orElseThrow(() -> new NoSuchMethodException("ToolStackItemPredicate.matches(ItemStack)"));
            return (boolean) matches.invoke(predicate, stack);
        } catch (InvocationTargetException e) {
            throw propagate("Tinkers' tool predicate failed", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot invoke Tinkers' tool predicate compatibility adapter", e);
        }
    }

    private static LegacyToolStackItemPredicateAdapter decode(JsonObject json) {
        Function<JsonObject, ?> decoder = DECODERS.get(LEGACY_ID);
        if (decoder == null) {
            throw new IllegalStateException("Tinkers' tool-stack item predicate decoder has not been registered");
        }
        return new LegacyToolStackItemPredicateAdapter(decoder.apply(json));
    }

    private static JsonElement encode(LegacyToolStackItemPredicateAdapter value) {
        try {
            Class<?> toolPredicate = Class.forName("slimeknights.tconstruct.library.json.predicate.tool.ToolStackPredicate");
            Object loader = toolPredicate.getField("LOADER").get(null);
            Method serialize = java.util.Arrays.stream(loader.getClass().getMethods())
                    .filter(method -> method.getName().equals("serialize") && method.getParameterCount() == 1)
                    .findFirst()
                    .orElseThrow(() -> new NoSuchMethodException("FallbackPredicateRegistry.serialize"));
            JsonObject json = new JsonObject();
            json.add("predicate", (JsonElement) serialize.invoke(loader, value.predicate));
            return json;
        } catch (InvocationTargetException e) {
            throw propagate("Tinkers' tool predicate serialization failed", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot serialize Tinkers' tool predicate", e);
        }
    }

    private static RuntimeException propagate(String message, Throwable cause) {
        if (cause instanceof RuntimeException runtimeException) return runtimeException;
        if (cause instanceof Error error) throw error;
        return new IllegalStateException(message, cause);
    }
}
