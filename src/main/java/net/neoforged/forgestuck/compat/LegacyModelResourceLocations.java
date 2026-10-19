package net.neoforged.forgestuck.compat;

import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;

public final class LegacyModelResourceLocations {
    private LegacyModelResourceLocations() {}

    public static ResourceLocation missingModelLocation() {
        return ModelBakery.MISSING_MODEL_LOCATION;
    }
}
