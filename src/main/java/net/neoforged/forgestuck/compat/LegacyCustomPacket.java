package net.neoforged.forgestuck.compat;

import net.minecraft.network.protocol.Packet;

/** Forge 1.20.1 {@code ICustomPacket}: holder for the vanilla packet built by {@code NetworkDirection.buildPacket}. */
public final class LegacyCustomPacket {
    private final Packet<?> packet;

    LegacyCustomPacket(Packet<?> packet) {
        this.packet = packet;
    }

    public Packet<?> getThis() {
        return packet;
    }
}