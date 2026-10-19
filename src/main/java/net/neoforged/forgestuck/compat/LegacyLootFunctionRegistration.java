package net.neoforged.forgestuck.compat;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.registries.RegisterEvent;

public final class LegacyLootFunctionRegistration {
    private static final ResourceLocation NBT_SYNC =
            ResourceLocation.fromNamespaceAndPath("cofh_core", "nbt_sync");

    private LegacyLootFunctionRegistration() {}

    public static void install(IEventBus bus) {
        var activeContainer = ModLoadingContext.get().getActiveContainer();
        if (activeContainer == null || !activeContainer.getModId().equals("cofh_core")) return;
        bus.addListener(RegisterEvent.class, event -> {
            if (event.getRegistryKey().equals(Registries.LOOT_FUNCTION_TYPE)) {
                event.register(Registries.LOOT_FUNCTION_TYPE, NBT_SYNC,
                        LegacyLootFunctionRegistration::createType);
            }
        });
    }

    public static <T> T existing(Registry<T> registry, ResourceLocation id, T ignored) {
        return registry.getOptional(id).orElseThrow(() -> new IllegalStateException(
                "Expected early registration of loot function type " + id
                        + " before the built-in registry was frozen"));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static LootItemFunctionType createType() {
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            Class<?> serializerClass = Class.forName("cofh.lib.common.loot.TileNBTSync$Serializer", true, loader);
            Object serializer = serializerClass.getDeclaredConstructor().newInstance();
            if (!(serializer instanceof LegacyLootSerializer legacySerializer)) {
                throw new IllegalStateException("CoFH TileNBTSync serializer does not implement the legacy loot API");
            }
            return LegacyLootBridge.functionType(legacySerializer);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not create the cofh_core:nbt_sync loot function type", exception);
        }
    }
}
