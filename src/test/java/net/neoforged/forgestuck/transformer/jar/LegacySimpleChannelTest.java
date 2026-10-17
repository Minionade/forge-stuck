package net.neoforged.forgestuck.transformer.jar;

import net.neoforged.forgestuck.compat.LegacyNetworkContext;
import net.neoforged.forgestuck.compat.LegacyNetworkDirection;
import net.neoforged.forgestuck.compat.LegacySimpleChannel;
import net.neoforged.bus.api.IEventBus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.junit.jupiter.api.Test;

class LegacySimpleChannelTest {
    record Ping(String text, int n) {}

    private static LegacySimpleChannel channel() {
        return new LegacySimpleChannel(ResourceLocation.fromNamespaceAndPath("synthetic", "main"), "1",
                noBus());
    }

    private static FriendlyByteBuf buf() throws Exception {
        ClassLoader game = FriendlyByteBuf.class.getClassLoader();
        Class<?> byteBuf = Class.forName("io.netty.buffer.ByteBuf", true, game);
        Object raw = Class.forName("io.netty.buffer.Unpooled", true, game).getMethod("buffer").invoke(null);
        return FriendlyByteBuf.class.getConstructor(byteBuf).newInstance(raw);
    }

    private static IEventBus noBus() {
        return (IEventBus) Proxy.newProxyInstance(IEventBus.class.getClassLoader(), new Class<?>[] {IEventBus.class},
                (p, m, a) -> null);
    }

    @Test
    void messageRoundTripsThroughRegisteredCodecAndHandler() throws Exception {
        LegacySimpleChannel ch = channel();
        AtomicReference<Object> received = new AtomicReference<>();
        AtomicReference<LegacyNetworkContext> context = new AtomicReference<>();
        ch.registerMessage(0, Ping.class,
                (m, buf) -> buf.writeUtf(m.text()).writeVarInt(m.n()),
                buf -> new Ping(buf.readUtf(), buf.readVarInt()),
                (m, ctx) -> {
                    received.set(m);
                    context.set(ctx.get());
                    ctx.get().setPacketHandled(true);
                });
        Object decoded = ch.roundTrip(new Ping("hello", 42), buf());
        assertEquals(new Ping("hello", 42), decoded);

        IPayloadContext fake = (IPayloadContext) Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(),
                new Class<?>[] {IPayloadContext.class}, (p, m, a) -> switch (m.getName()) {
                    case "flow" -> PacketFlow.SERVERBOUND;
                    case "player" -> null;
                    case "enqueueWork" -> {
                        ((Runnable) a[0]).run();
                        yield java.util.concurrent.CompletableFuture.completedFuture(null);
                    }
                    default -> throw new UnsupportedOperationException(m.getName());
                });
        var message = ch.messageFor(Ping.class);
        message.handle(new LegacySimpleChannel.Payload(message.type, new Ping("x", 1)), fake);
        assertEquals(new Ping("x", 1), received.get());
        assertTrue(context.get().getPacketHandled());
        assertEquals(LegacyNetworkDirection.PLAY_TO_SERVER, context.get().getDirection());
        assertEquals(null, context.get().getSender());
        int[] ran = {0};
        context.get().enqueueWork(() -> ran[0]++);
        assertEquals(1, ran[0]);
    }

    @Test
    void registerMessageReturnsHandlerAndHonoursDirection() throws Exception {
        LegacySimpleChannel ch = channel();
        var h = ch.registerMessage(3, Ping.class, (m, b) -> {}, (FriendlyByteBuf b) -> null,
                (m, c) -> {}, Optional.of(LegacyNetworkDirection.PLAY_TO_CLIENT));
        assertEquals(3, h.index());
        assertTrue(ch.messageFor(Ping.class) != null);
    }

    @Test
    void unsupportedPartsFailAtTheCall() throws Exception {
        LegacySimpleChannel ch = channel();
        assertThrows(UnsupportedOperationException.class, () -> ch.registerMessage(1, Ping.class, (m, b) -> {},
                (FriendlyByteBuf b) -> null, (m, c) -> {}, Optional.of(LegacyNetworkDirection.LOGIN_TO_CLIENT)));
        assertThrows(IllegalArgumentException.class, () -> ch.roundTrip(new Ping("a", 1), buf()));
    }
}