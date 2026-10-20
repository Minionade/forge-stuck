package net.neoforged.forgestuck.compat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

public record LegacyStructureType<S extends Structure>(MapCodec<S> codec) implements StructureType<S> {
    public static <S extends Structure> StructureType<S> adapt(Codec<S> codec) {
        return new LegacyStructureType<>(MapCodec.assumeMapUnsafe(codec));
    }
}
