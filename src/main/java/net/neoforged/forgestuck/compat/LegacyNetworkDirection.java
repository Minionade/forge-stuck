package net.neoforged.forgestuck.compat;

import net.neoforged.fml.LogicalSide;

/** Forge 1.20.1 {@code NetworkDirection}; only the PLAY directions can be carried by NeoForge payloads. */
public enum LegacyNetworkDirection {
    PLAY_TO_SERVER(LogicalSide.CLIENT, LogicalSide.SERVER),
    PLAY_TO_CLIENT(LogicalSide.SERVER, LogicalSide.CLIENT),
    LOGIN_TO_SERVER(LogicalSide.CLIENT, LogicalSide.SERVER),
    LOGIN_TO_CLIENT(LogicalSide.SERVER, LogicalSide.CLIENT);

    private final LogicalSide origin;
    private final LogicalSide reception;

    LegacyNetworkDirection(LogicalSide origin, LogicalSide reception) {
        this.origin = origin;
        this.reception = reception;
    }

    public LogicalSide getOriginationSide() {
        return origin;
    }

    public LogicalSide getReceptionSide() {
        return reception;
    }

    public LegacyCustomPacket buildPacket(org.apache.commons.lang3.tuple.Pair<net.minecraft.network.FriendlyByteBuf, Integer> data,
            net.minecraft.resources.ResourceLocation channel) {
        if (isLogin()) {
            throw new UnsupportedOperationException("NetworkDirection." + this + ".buildPacket: LOGIN-phase packets have no NeoForge payload equivalent");
        }
        LegacyEventChannel.Raw raw = LegacyEventChannel.raw(channel, data.getLeft());
        return new LegacyCustomPacket(this == PLAY_TO_CLIENT ? raw.toVanillaClientbound() : raw.toVanillaServerbound());
    }

    boolean isLogin() {
        return this == LOGIN_TO_SERVER || this == LOGIN_TO_CLIENT;
    }
}