package net.neoforged.forgestuck.compat;

import java.util.Objects;

import net.minecraft.network.protocol.Packet;

/** Forge 1.20.1 {@code ICustomPacket}: holder for a vanilla packet built by {@code NetworkDirection.buildPacket}. */
@FunctionalInterface
public interface LegacyCustomPacket {
    Packet<?> getThis();

    static LegacyCustomPacket wrap(Packet<?> packet) {
        Objects.requireNonNull(packet, "packet");
        return () -> packet;
    }
}
