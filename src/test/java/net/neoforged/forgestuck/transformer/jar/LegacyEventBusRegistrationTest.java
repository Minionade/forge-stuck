package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.*;

import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.forgestuck.compat.LegacyEventBusRegistration;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodInsnNode;

class LegacyEventBusRegistrationTest {
    public static class Empty {}

    public static class TestEvent extends Event {}

    public static class Listener {
        int calls;

        @SubscribeEvent
        public void on(TestEvent event) {
            calls++;
        }
    }

    public static class InheritedListener extends Listener {}

    public static class InvalidListener {
        @SubscribeEvent
        public void on(String notAnEvent) {}
    }

    @Test
    void emptyClassAndInstanceKeepForgeNoOpBehavior() {
        var bus = BusBuilder.builder().build();
        assertThrows(IllegalArgumentException.class, () -> bus.register(Empty.class));
        assertDoesNotThrow(() -> LegacyEventBusRegistration.register(bus, Empty.class));
        assertDoesNotThrow(() -> LegacyEventBusRegistration.register(bus, new Empty()));
    }

    @Test
    void annotatedHandlersAreRegisteredAndReceiveEvents() {
        var bus = BusBuilder.builder().build();
        var listener = new Listener();
        LegacyEventBusRegistration.register(bus, listener);
        LegacyEventBusRegistration.register(bus, listener);
        bus.post(new TestEvent());
        assertEquals(1, listener.calls);
    }

    @Test
    void validationFailuresAreNotSilenced() {
        var bus = BusBuilder.builder().build();
        assertThrows(IllegalArgumentException.class,
                () -> LegacyEventBusRegistration.register(bus, new InvalidListener()));
        assertThrows(IllegalArgumentException.class,
                () -> LegacyEventBusRegistration.register(bus, Listener.class));
        assertThrows(IllegalArgumentException.class,
                () -> LegacyEventBusRegistration.register(bus, new InheritedListener()));
    }

    @Test
    void legacyRegisterCallUsesRelocatableBridge() {
        String bus = "net/minecraftforge/eventbus/api/IEventBus";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/RegisterEmpty", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "register",
                "(L" + bus + ";Ljava/lang/Object;)V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, bus, "register", "(Ljava/lang/Object;)V", true);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        byte[] output = new ForgeOwnerRemapStep().apply("com.ex.RegisterEmpty", writer.toByteArray());
        output = CompatRelocation.relocate(output);
        var node = ForgeOwnerRemapTest.parse(output);
        var call = (MethodInsnNode) node.methods.get(0).instructions.toArray()[2];
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals(CompatRelocation.TO + "LegacyEventBusRegistration", call.owner);
        assertEquals("(Lnet/neoforged/bus/api/IEventBus;Ljava/lang/Object;)V", call.desc);
        assertFalse(call.itf);
    }
}
