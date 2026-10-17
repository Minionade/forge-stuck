package net.neoforged.forgestuck.compat;

import io.netty.buffer.Unpooled;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.HandlerThread;

/**
 * Forge 1.20.1 {@code EventNetworkChannel} over one NeoForge payload type named after the channel. The payload is
 * the raw remaining bytes of the buffer (what Forge's custom-payload packet carried). Objects registered with
 * {@link #registerObject} have their {@code @SubscribeEvent} methods taking a {@link LegacyNetworkEvent} subtype
 * invoked on the network thread with a copy-able {@code getPayload()} buffer and a context supplier.
 *
 * <p>Preserved: raw payload bytes both ways, packets built through {@code NetworkDirection.buildPacket}, listener
 * dispatch by event type, context enqueueWork/setPacketHandled. Not preserved: {@code ChannelRegistryEvent},
 * {@code addListener(Consumer)} (Forge resolves the event type from generics; fails per call), accepted-version
 * predicates (NeoForge compares the version string exactly), LOGIN-phase payloads.
 */
public final class LegacyEventChannel {
    private final ResourceLocation name;
    private final CustomPacketPayload.Type<Raw> type;
    private final List<Object> listeners = new CopyOnWriteArrayList<>();

    public LegacyEventChannel(ResourceLocation name, String version, IEventBus bus) {
        this.name = name;
        this.type = new CustomPacketPayload.Type<>(name);
        bus.addListener(RegisterPayloadHandlersEvent.class, event -> {
            boolean optional = LegacyNetworkRegistry.ACCEPT_VANILLA.equals(version)
                    || LegacyNetworkRegistry.ABSENT.equals(version);
            var registrar = event.registrar(version).executesOn(HandlerThread.NETWORK);
            if (optional) registrar = registrar.optional();
            registrar.playBidirectional(type, codec(type), this::handle);
        });
    }

    public record Raw(CustomPacketPayload.Type<Raw> type, byte[] data) implements CustomPacketPayload {}

    public static StreamCodec<FriendlyByteBuf, Raw> codec(CustomPacketPayload.Type<Raw> type) {
        return StreamCodec.of(
                (buf, raw) -> buf.writeBytes(raw.data()),
                buf -> {
                    byte[] data = new byte[buf.readableBytes()];
                    buf.readBytes(data);
                    return new Raw(type, data);
                });
    }

    public static Raw raw(ResourceLocation channel, FriendlyByteBuf payload) {
        byte[] data = new byte[payload.readableBytes()];
        payload.getBytes(payload.readerIndex(), data);
        return new Raw(new CustomPacketPayload.Type<>(channel), data);
    }

    public void registerObject(Object object) {
        listeners.add(object);
    }

    public void addListener(java.util.function.Consumer<?> listener) {
        throw new UnsupportedOperationException("EventNetworkChannel " + name + ".addListener(Consumer): Forge resolved "
                + "the event type from the lambda's generic signature, which is not recoverable here; use "
                + "registerObject with @SubscribeEvent methods");
    }

    public void handle(Raw raw, IPayloadContext context) {
        handle(raw, context, data -> new FriendlyByteBuf(Unpooled.wrappedBuffer(data)));
    }

    /** Overload with an explicit buffer factory (lets tests build buffers under a different netty loader). */
    public void handle(Raw raw, IPayloadContext context, java.util.function.Function<byte[], FriendlyByteBuf> buffers) {
        LegacyNetworkContext legacy = new LegacyNetworkContext(context);
        FriendlyByteBuf buf = buffers.apply(raw.data());
        LegacyNetworkEvent event = context.flow() == PacketFlow.SERVERBOUND
                ? new LegacyNetworkEvent.ClientCustomPayloadEvent(buf, () -> legacy)
                : new LegacyNetworkEvent.ServerCustomPayloadEvent(buf, () -> legacy);
        dispatch(event);
    }

    void dispatch(LegacyNetworkEvent event) {
        for (Object listener : listeners) {
            for (Method m : listener.getClass().getDeclaredMethods()) {
                if (!m.isAnnotationPresent(SubscribeEvent.class) || m.getParameterCount() != 1
                        || !m.getParameterTypes()[0].isInstance(event)) {
                    continue;
                }
                try {
                    m.setAccessible(true);
                    m.invoke(listener, event);
                } catch (ReflectiveOperationException ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    throw new IllegalStateException("EventNetworkChannel " + name + " listener " + m + " failed", cause);
                }
            }
        }
    }
}