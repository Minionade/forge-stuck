package net.neoforged.forgestuck.compat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Particle type adapter from Forge's deserializer/Codec API to NeoForge's MapCodec/StreamCodec API. */
public abstract class LegacyParticleType<T extends LegacyParticleOptions> extends ParticleType<T> {
    private final LegacyParticleDeserializer<T> deserializer;

    protected LegacyParticleType(boolean overrideLimiter, LegacyParticleDeserializer<T> deserializer) {
        super(overrideLimiter);
        this.deserializer = deserializer;
    }

    protected abstract Codec<T> legacyCodec();

    @Override
    public MapCodec<T> codec() {
        return legacyCodec().fieldOf("data");
    }

    @Override
    public StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec() {
        return StreamCodec.of(
                (buffer, value) -> value.writeToNetwork(buffer),
                buffer -> deserializer.fromNetwork(this, buffer));
    }
}
