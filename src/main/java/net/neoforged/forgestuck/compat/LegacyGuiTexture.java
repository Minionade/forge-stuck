package net.neoforged.forgestuck.compat;

import net.minecraft.resources.ResourceLocation;

public final class LegacyGuiTexture {
    private LegacyGuiTexture() {}

    public static ResourceLocation vanillaIcons() {
        return ResourceLocation.withDefaultNamespace("textures/atlas/gui.png");
    }
}
