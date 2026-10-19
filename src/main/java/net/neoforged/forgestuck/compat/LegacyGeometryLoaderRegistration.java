package net.neoforged.forgestuck.compat;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;

public final class LegacyGeometryLoaderRegistration {
    private LegacyGeometryLoaderRegistration() {}

    public static void register(ModelEvent.RegisterGeometryLoaders event, String name, IGeometryLoader<?> loader) {
        String namespace = ModLoadingContext.get().getActiveContainer().getModId();
        ResourceLocation id = name.indexOf(':') >= 0
                ? ResourceLocation.parse(name)
                : ResourceLocation.fromNamespaceAndPath(namespace, name);
        event.register(id, loader);
    }
}
