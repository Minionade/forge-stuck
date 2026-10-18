package net.neoforged.forgestuck.compat;

import java.util.BitSet;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.ItemModelGenerator;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

public final class LegacyUnbakedGeometryHelper {
    private static final ItemModelGenerator ITEM_MODEL_GENERATOR = new ItemModelGenerator();

    private LegacyUnbakedGeometryHelper() {}

    public static List<BlockElement> createUnbakedItemElements(int layerIndex, SpriteContents spriteContents) {
        return ITEM_MODEL_GENERATOR.processFrames(layerIndex, "layer" + layerIndex, spriteContents);
    }

    public static List<BlockElement> createUnbakedItemMaskElements(int layerIndex, SpriteContents spriteContents) {
        List<BlockElement> elements = createUnbakedItemElements(layerIndex, spriteContents);
        elements.remove(0);

        int width = spriteContents.width();
        int height = spriteContents.height();
        BitSet opaquePixels = new BitSet(width * height);
        spriteContents.getUniqueFrames().forEach(frame -> {
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    if (!spriteContents.isTransparent(frame, x, y)) {
                        opaquePixels.set(x + y * width);
                    }
                }
            }
        });

        for (int y = 0; y < height; y++) {
            int segmentStart = -1;
            for (int x = 0; x < width; x++) {
                boolean opaque = opaquePixels.get(x + y * width);
                if (opaque == (segmentStart == -1)) {
                    if (segmentStart == -1) {
                        segmentStart = x;
                        continue;
                    }

                    int endY = y + 1;
                    outer:
                    for (; endY < height; endY++) {
                        for (int scanX = segmentStart; scanX <= x; scanX++) {
                            if (!opaquePixels.get(scanX + endY * width)) {
                                break outer;
                            }
                        }
                    }

                    for (int clearX = segmentStart; clearX < x; clearX++) {
                        for (int clearY = y; clearY < endY; clearY++) {
                            opaquePixels.clear(clearX + clearY * width);
                        }
                    }

                    var faces = new EnumMap<Direction, BlockElementFace>(Direction.class);
                    for (Direction direction : Direction.values()) {
                        faces.put(direction, new BlockElementFace(
                                null, layerIndex, "layer" + layerIndex, new BlockFaceUV(null, 0)));
                    }
                    elements.add(new BlockElement(
                            new Vector3f(16 * segmentStart / (float) width, 16 - 16 * endY / (float) height, 7.5F),
                            new Vector3f(16 * x / (float) width, 16 - 16 * y / (float) height, 8.5F),
                            faces,
                            null,
                            true));
                    segmentStart = -1;
                }
            }
        }
        return elements;
    }

    public static List<BakedQuad> bakeElements(
            List<BlockElement> elements,
            Function<Material, TextureAtlasSprite> spriteGetter,
            ModelState modelState,
            ResourceLocation ignoredModelLocation) {
        return net.neoforged.neoforge.client.model.geometry.UnbakedGeometryHelper
                .bakeElements(elements, spriteGetter, modelState);
    }
}
