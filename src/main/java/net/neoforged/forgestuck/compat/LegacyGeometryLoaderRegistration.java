package net.neoforged.forgestuck.compat;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;

public final class LegacyGeometryLoaderRegistration {
    private LegacyGeometryLoaderRegistration() {}

    public static void register(ModelEvent.RegisterGeometryLoaders event, String name, IGeometryLoader<?> loader) {
        event.register(ResourceLocation.parse(name), loader);
    }
}
