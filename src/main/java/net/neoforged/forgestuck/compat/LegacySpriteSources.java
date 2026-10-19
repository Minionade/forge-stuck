package net.neoforged.forgestuck.compat;

import com.google.common.collect.BiMap;
import com.mojang.serialization.Codec;
import java.lang.reflect.Field;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.client.renderer.texture.atlas.SpriteSources;
import net.minecraft.resources.ResourceLocation;

/** Preserves Forge's namespaced sprite-source registration API on the 1.21 MapCodec registry. */
public final class LegacySpriteSources {
    private LegacySpriteSources() {}

    public static SpriteSourceType register(String id, Codec<? extends SpriteSource> codec) {
        ResourceLocation key = ResourceLocation.parse(id);
        SpriteSourceType type = new SpriteSourceType(codec.fieldOf("value"));
        return spriteSourceTypes().put(key, type);
    }

    @SuppressWarnings("unchecked")
    private static BiMap<ResourceLocation, SpriteSourceType> spriteSourceTypes() {
        try {
            Field field = SpriteSources.class.getField("TYPES");
            return (BiMap<ResourceLocation, SpriteSourceType>) field.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Cannot access the Minecraft sprite-source type registry", e);
        }
    }
}
