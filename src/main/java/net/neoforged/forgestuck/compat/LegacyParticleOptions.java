package net.neoforged.forgestuck.compat;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.FriendlyByteBuf;

/** Legacy particle options retain Forge's explicit command and network writers on top of NeoForge's marker API. */
public interface LegacyParticleOptions extends ParticleOptions {
    void writeToNetwork(FriendlyByteBuf buffer);

    String writeToString();
}
