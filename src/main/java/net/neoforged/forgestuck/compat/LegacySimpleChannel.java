package net.neoforged.forgestuck.compat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Forge 1.20.1 {@code SimpleChannel} over NeoForge payload registration. Each {@code registerMessage} becomes one
 * payload type {@code <channel namespace>:<channel path>/<id>} registered when {@link RegisterPayloadHandlersEvent}
 * fires on the registering mod's bus.
 *
 * <p>Preserved: encoder/decoder over {@link FriendlyByteBuf}, consumer invoked on the network thread with a context
 * offering enqueueWork/getSender/getDirection/setPacketHandled, direction restriction, PacketDistributor targets.
 * Not preserved: accepted-version predicates (NeoForge compares the version string exactly; the legacy
 * ACCEPTVANILLA/ABSENT markers become optional channels), LOGIN-phase messages, {@code messageBuilder},
 * {@code isRemotePresent}; registering after the event has fired fails in that call.
 */
public final class LegacySimpleChannel {
    private final ResourceLocation name;
    private final String version;
    private final List<Message<?>> messages = new ArrayList<>();
    private final Map<Class<?>, Message<?>> byClass = new HashMap<>();
    private boolean registrationClosed;

    public LegacySimpleChannel(ResourceLocation name, String version, IEventBus bus) {
        this.name = name;
        this.version = version;
        bus.addListener(RegisterPayloadHandlersEvent.class, this::register);
    }

    /** Forge's payload: the decoded legacy message plus the NeoForge type it travels under. */
    public record Payload(CustomPacketPayload.Type<Payload> type, Object message) implements CustomPacketPayload {}

    public final class Message<M> {
        final int id;
        final Class<M> clazz;
        final BiConsumer<M, FriendlyByteBuf> encoder;
        final Function<FriendlyByteBuf, M> decoder;
        final BiConsumer<M, Supplier<LegacyNetworkContext>> consumer;
        final Optional<LegacyNetworkDirection> direction;
        public final CustomPacketPayload.Type<Payload> type;

        Message(int id, Class<M> clazz, BiConsumer<M, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, M> decoder,
                BiConsumer<M, Supplier<LegacyNetworkContext>> consumer, Optional<LegacyNetworkDirection> direction) {
            this.id = id;
            this.clazz = clazz;
            this.encoder = encoder;
            this.decoder = decoder;
            this.consumer = consumer;
            this.direction = direction;
            this.type = new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(name.getNamespace(), name.getPath() + "/" + id));
        }

        @SuppressWarnings("unchecked")
        StreamCodec<FriendlyByteBuf, Payload> codec() {
            return StreamCodec.of(
                    (buf, payload) -> encoder.accept((M) payload.message(), buf),
                    buf -> new Payload(type, decoder.apply(buf)));
        }

        @SuppressWarnings("unchecked")
        public void handle(Payload payload, IPayloadContext context) {
            LegacyNetworkContext legacy = new LegacyNetworkContext(context);
            consumer.accept((M) payload.message(), () -> legacy);
        }
    }

    public <M> LegacyMessageHandler registerMessage(int index, Class<M> type, BiConsumer<M, FriendlyByteBuf> encoder,
            Function<FriendlyByteBuf, M> decoder, BiConsumer<M, Supplier<LegacyNetworkContext>> consumer) {
        return registerMessage(index, type, encoder, decoder, consumer, Optional.empty());
    }

    public synchronized <M> LegacyMessageHandler registerMessage(int index, Class<M> type, BiConsumer<M, FriendlyByteBuf> encoder,
            Function<FriendlyByteBuf, M> decoder, BiConsumer<M, Supplier<LegacyNetworkContext>> consumer,
            Optional<LegacyNetworkDirection> direction) {
        if (registrationClosed) {
            throw new UnsupportedOperationException("SimpleChannel " + name + ".registerMessage(" + index + ", "
                    + type.getName() + ") was called after NeoForge's RegisterPayloadHandlersEvent; payloads can only "
                    + "be registered during that event");
        }
        if (direction.isPresent() && direction.get().isLogin()) {
            throw new UnsupportedOperationException("SimpleChannel " + name + ": LOGIN-phase message "
                    + type.getName() + " (" + direction.get() + ") has no NeoForge payload equivalent here");
        }
        Message<M> message = new Message<>(index, type, encoder, decoder, consumer, direction);
        if (byClass.putIfAbsent(type, message) != null || messages.stream().anyMatch(m -> m.id == index)) {
            throw new IllegalStateException("Duplicate message id " + index + " or class " + type.getName()
                    + " on channel " + name);
        }
        messages.add(message);
        return new LegacyMessageHandler(index);
    }

    private synchronized void register(RegisterPayloadHandlersEvent event) {
        registrationClosed = true;
        boolean optional = LegacyNetworkRegistry.ACCEPT_VANILLA.equals(version)
                || LegacyNetworkRegistry.ABSENT.equals(version);
        PayloadRegistrar registrar = event.registrar(version).executesOn(HandlerThread.NETWORK);
        if (optional) registrar = registrar.optional();
        for (Message<?> m : messages) register(registrar, m);
    }

    private <M> void register(PayloadRegistrar registrar, Message<M> m) {
        if (m.direction.isEmpty()) {
            registrar.playBidirectional(m.type, m.codec(), m::handle);
        } else if (m.direction.get() == LegacyNetworkDirection.PLAY_TO_SERVER) {
            registrar.playToServer(m.type, m.codec(), m::handle);
        } else {
            registrar.playToClient(m.type, m.codec(), m::handle);
        }
    }

    public Message<?> messageFor(Class<?> type) {
        Message<?> m = byClass.get(type);
        if (m == null) throw new IllegalArgumentException("Message " + type.getName() + " is not registered on " + name);
        return m;
    }

    private Payload payload(Object message) {
        return new Payload(messageFor(message.getClass()).type, message);
    }

    public <M> void send(LegacyPacketDistributor.LegacyPacketTarget target, M message) {
        target.send(payload(message));
    }

    public <M> void sendToServer(M message) {
        PacketDistributor.sendToServer(payload(message));
    }

    public <M> void sendTo(M message, Connection connection, LegacyNetworkDirection direction) {
        Payload payload = payload(message);
        if (direction == LegacyNetworkDirection.PLAY_TO_CLIENT) {
            connection.send(payload.toVanillaClientbound());
        } else if (direction == LegacyNetworkDirection.PLAY_TO_SERVER) {
            connection.send(payload.toVanillaServerbound());
        } else {
            throw new UnsupportedOperationException("SimpleChannel " + name + ".sendTo with " + direction);
        }
    }

    public <M> void reply(M message, LegacyNetworkContext context) {
        context.payloadContext().reply(payload(message));
    }

    /** Test hook: encodes then decodes a message into the given buffer through the registered codec. */
    public Object roundTrip(Object message, FriendlyByteBuf buf) {
        Message<?> m = messageFor(message.getClass());
        m.codec().encode(buf, payload(message));
        return m.codec().decode(buf).message();
    }
}