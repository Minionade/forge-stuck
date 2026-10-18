package net.neoforged.forgestuck.compat;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;

/** Forge 1.20 particle parsing contract, retained for legacy particle type implementations. */
public interface LegacyParticleDeserializer<T extends LegacyParticleOptions> {
    T fromCommand(ParticleType<T> type, StringReader reader) throws CommandSyntaxException;

    T fromNetwork(ParticleType<T> type, FriendlyByteBuf buffer);
}
