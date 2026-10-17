package net.neoforged.forgestuck.compat;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Forge 1.20.1 {@code NetworkEvent} custom-payload events as delivered to {@link LegacyEventChannel} listeners.
 * Only the payload/source accessors are provided; the login index and channel registry events are not.
 */
public class LegacyNetworkEvent {
    private final FriendlyByteBuf payload;
    private final Supplier<LegacyNetworkContext> source;

    LegacyNetworkEvent(FriendlyByteBuf payload, Supplier<LegacyNetworkContext> source) {
        this.payload = payload;
        this.source = source;
    }

    public FriendlyByteBuf getPayload() {
        return payload;
    }

    public Supplier<LegacyNetworkContext> getSource() {
        return source;
    }

    /** Received by the server from a client. */
    public static final class ClientCustomPayloadEvent extends LegacyNetworkEvent {
        ClientCustomPayloadEvent(FriendlyByteBuf payload, Supplier<LegacyNetworkContext> source) {
            super(payload, source);
        }
    }

    /** Received by the client from the server. */
    public static final class ServerCustomPayloadEvent extends LegacyNetworkEvent {
        ServerCustomPayloadEvent(FriendlyByteBuf payload, Supplier<LegacyNetworkContext> source) {
            super(payload, source);
        }
    }
}