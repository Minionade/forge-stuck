package net.neoforged.forgestuck.compat;

import java.util.concurrent.CompletableFuture;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Forge 1.20.1 {@code NetworkEvent.Context} over a NeoForge {@link IPayloadContext}. */
public final class LegacyNetworkContext {
    private final IPayloadContext delegate;
    private boolean handled;

    LegacyNetworkContext(IPayloadContext delegate) {
        this.delegate = delegate;
    }

    public LegacyNetworkDirection getDirection() {
        return delegate.flow() == PacketFlow.SERVERBOUND ? LegacyNetworkDirection.PLAY_TO_SERVER
                : LegacyNetworkDirection.PLAY_TO_CLIENT;
    }

    /** The sending player on the server; {@code null} on the client, as in Forge. */
    public ServerPlayer getSender() {
        return delegate.flow() == PacketFlow.SERVERBOUND && delegate.player() instanceof ServerPlayer p ? p : null;
    }

    public Connection getNetworkManager() {
        return delegate.connection();
    }

    public CompletableFuture<Void> enqueueWork(Runnable work) {
        return delegate.enqueueWork(work);
    }

    public void setPacketHandled(boolean handled) {
        this.handled = handled;
    }

    public boolean getPacketHandled() {
        return handled;
    }

    IPayloadContext payloadContext() {
        return delegate;
    }
}