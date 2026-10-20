package net.neoforged.forgestuck.compat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

public record LegacyPlacementModifierType<P extends PlacementModifier>(MapCodec<P> codec)
        implements PlacementModifierType<P> {
    public static <P extends PlacementModifier> PlacementModifierType<P> adapt(Codec<P> codec) {
        return new LegacyPlacementModifierType<>(MapCodec.assumeMapUnsafe(codec));
    }
}
