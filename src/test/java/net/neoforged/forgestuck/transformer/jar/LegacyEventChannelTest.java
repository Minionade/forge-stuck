package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.forgestuck.compat.LegacyCapabilities;
import net.neoforged.forgestuck.compat.LegacyEventChannel;
import net.neoforged.forgestuck.compat.LegacyNetworkEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class LegacyEventChannelTest {
    public static final class Handler {
        final List<String> got = new ArrayList<>();

        @SubscribeEvent
        public void onClient(LegacyNetworkEvent.ClientCustomPayloadEvent e) {
            got.add("server-received:" + e.getPayload().readUtf());
            e.getSource().get().setPacketHandled(true);
        }

        @SubscribeEvent
        public void onServer(LegacyNetworkEvent.ServerCustomPayloadEvent e) {
            got.add("client-received:" + e.getPayload().readUtf());
        }
    }

    private static FriendlyByteBuf buf() throws Exception {
        ClassLoader game = FriendlyByteBuf.class.getClassLoader();
        Class<?> byteBuf = Class.forName("io.netty.buffer.ByteBuf", true, game);
        Object raw = Class.forName("io.netty.buffer.Unpooled", true, game).getMethod("buffer").invoke(null);
        return FriendlyByteBuf.class.getConstructor(byteBuf).newInstance(raw);
    }

    private static IPayloadContext ctx(PacketFlow flow) {
        return (IPayloadContext) Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(),
                new Class<?>[] {IPayloadContext.class}, (p, m, a) -> switch (m.getName()) {
                    case "flow" -> flow;
                    case "player" -> null;
                    default -> throw new UnsupportedOperationException(m.getName());
                });
    }

    @Test
    void rawPayloadRoundTripsAndDispatchesToRegisteredObjectByDirection() throws Exception {
        ResourceLocation name = ResourceLocation.fromNamespaceAndPath("synthetic", "events");
        IEventBus bus = (IEventBus) Proxy.newProxyInstance(IEventBus.class.getClassLoader(),
                new Class<?>[] {IEventBus.class}, (p, m, a) -> null);
        LegacyEventChannel ch = new LegacyEventChannel(name, "1", bus);
        Handler h = new Handler();
        ch.registerObject(h);

        FriendlyByteBuf out = buf();
        out.writeUtf("hello");
        LegacyEventChannel.Raw raw = LegacyEventChannel.raw(name, out);
        FriendlyByteBuf wire = buf();
        var codec = LegacyEventChannel.codec(raw.type());
        codec.encode(wire, raw);
        LegacyEventChannel.Raw decoded = codec.decode(wire);
        assertArrayEquals(raw.data(), decoded.data());
        assertEquals(name, decoded.type().id());

        java.util.function.Function<byte[], FriendlyByteBuf> f = data -> {
            try {
                FriendlyByteBuf b = buf();
                b.writeBytes(data);
                return b;
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        };
        ch.handle(decoded, ctx(PacketFlow.SERVERBOUND), f);
        ch.handle(decoded, ctx(PacketFlow.CLIENTBOUND), f);
        assertEquals(List.of("server-received:hello", "client-received:hello"), h.got);
        assertThrows(UnsupportedOperationException.class, () -> ch.addListener(x -> {}));
    }

    @Test
    void registerCapabilitiesAndToolActionRewritesLink() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "x/Caps", null, "java/lang/Object", null);
        var mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "reg",
                "(Lnet/minecraftforge/common/capabilities/RegisterCapabilitiesEvent;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitLdcInsn(org.objectweb.asm.Type.getType(String.class));
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraftforge/common/capabilities/RegisterCapabilitiesEvent",
                "register", "(Ljava/lang/Class;)V", false);
        mv.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraftforge/common/ToolActions", "AXE_DIG",
                "Lnet/minecraftforge/common/ToolAction;");
        mv.visitInsn(Opcodes.POP);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        byte[] out = new ForgeOwnerRemapStep().apply("x/Caps", cw.toByteArray());
        ClassNode n = new ClassNode();
        new ClassReader(out).accept(n, 0);
        var m = n.methods.get(0);
        assertEquals("(Lnet/neoforged/neoforge/capabilities/RegisterCapabilitiesEvent;)V", m.desc);
        MethodInsnNode call = (MethodInsnNode) java.util.Arrays.stream(m.instructions.toArray())
                .filter(i -> i instanceof MethodInsnNode).findFirst().orElseThrow();
        assertEquals("net/neoforged/forgestuck/compat/LegacyCapabilities", call.owner);
        assertEquals("(Lnet/neoforged/neoforge/capabilities/RegisterCapabilitiesEvent;Ljava/lang/Class;)V", call.desc);
        assertTrue(java.util.Arrays.stream(m.instructions.toArray()).anyMatch(i -> i instanceof org.objectweb.asm.tree.FieldInsnNode f
                && f.owner.equals("net/neoforged/neoforge/common/ItemAbilities") && f.desc.equals("Lnet/neoforged/neoforge/common/ItemAbility;")));
        assertThrows(UnsupportedOperationException.class,
                () -> LegacyCapabilities.register(null, String.class));
    }
}