package net.neoforged.forgestuck.compat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import net.minecraft.world.item.DyeColor;
import net.neoforged.forgestuck.testsupport.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LegacyDyeColorShimTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.init();
    }

    @Test
    void convertsPackedTextureColorToNormalizedRgbComponents() {
        int rgb = DyeColor.ORANGE.getTextureDiffuseColor();

        assertArrayEquals(new float[] {
                ((rgb >> 16) & 0xff) / 255.0f,
                ((rgb >> 8) & 0xff) / 255.0f,
                (rgb & 0xff) / 255.0f
        }, LegacyDyeColorShim.getTextureDiffuseColors(DyeColor.ORANGE));
    }
}
