package net.neoforged.forgestuck.compat;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Adapts Forge registration-event accessors to NeoForge's event-scoped registry. */
public final class LegacyRegisterEvent {
    private LegacyRegisterEvent() {}

    @SuppressWarnings("unchecked")
    public static <T> IForgeRegistry<T> getForgeRegistry(RegisterEvent event) {
        ResourceKey<? extends Registry<T>> key = (ResourceKey<? extends Registry<T>>) (ResourceKey<?>) event.getRegistryKey();
        Registry<T> registry = (Registry<T>) event.getRegistry();
        return new VanillaRegistryView<>(key, registry);
    }
}
