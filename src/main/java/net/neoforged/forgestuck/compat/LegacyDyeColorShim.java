package net.neoforged.forgestuck.compat;

import net.minecraft.world.item.DyeColor;

/** Adapts Forge-era DyeColor texture components to the packed color used by 1.21.1. */
public final class LegacyDyeColorShim {
    private LegacyDyeColorShim() {}

    public static float[] getTextureDiffuseColors(DyeColor color) {
        int rgb = color.getTextureDiffuseColor();
        return new float[] {
                ((rgb >> 16) & 0xff) / 255.0f,
                ((rgb >> 8) & 0xff) / 255.0f,
                (rgb & 0xff) / 255.0f
        };
    }
}
