package net.neoforged.forgestuck.compat;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Supplies fluid textures for Gendustry fluids whose Forestry FluidType has no client extension. */
final class LegacyGendustryFluidClientCompat {
    private static boolean installed;

    private LegacyGendustryFluidClientCompat() {}

    static synchronized void install(IEventBus modEventBus) {
        if (installed) return;
        installed = true;
        modEventBus.addListener(RegisterClientExtensionsEvent.class,
                LegacyGendustryFluidClientCompat::registerFluidExtensions);
    }

    private static void registerFluidExtensions(RegisterClientExtensionsEvent event) {
        for (var entry : NeoForgeRegistries.FLUID_TYPES.entrySet()) {
            ResourceLocation id = entry.getKey().location();
            if (!id.getNamespace().equals("gendustry")) continue;
            ResourceLocation textureBase = ResourceLocation.fromNamespaceAndPath(
                    id.getNamespace(), "block/liquid/" + id.getPath());
            event.registerFluidType(new IClientFluidTypeExtensions() {
                @Override
                public ResourceLocation getStillTexture() {
                    return textureBase.withSuffix("_still");
                }

                @Override
                public ResourceLocation getFlowingTexture() {
                    return textureBase.withSuffix("_flowing");
                }
            }, entry.getValue());
        }
    }
}
