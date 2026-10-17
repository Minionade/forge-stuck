package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

class LegacyInstanceSubscriberStepTest {
    private static byte[] subscriber(boolean annotated) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/Obj$", null, "java/lang/Object", null);
        if (annotated) cw.visitAnnotation("Lnet/minecraftforge/fml/common/Mod$EventBusSubscriber;", true).visitEnd();
        for (int access : new int[] {Opcodes.ACC_PUBLIC, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC}) {
            var m = cw.visitMethod(access, (access & Opcodes.ACC_STATIC) != 0 ? "stat" : "inst",
                    "(Ljava/lang/Object;)V", null, null);
            m.visitAnnotation("Lnet/minecraftforge/eventbus/api/SubscribeEvent;", true).visitEnd();
            m.visitCode();
            m.visitInsn(Opcodes.RETURN);
            m.visitMaxs(0, 2);
            m.visitEnd();
        }
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static String annotated(byte[] bytes) {
        ClassNode n = new ClassNode();
        new ClassReader(bytes).accept(n, 0);
        StringBuilder sb = new StringBuilder();
        for (var m : n.methods) if (m.visibleAnnotations != null && !m.visibleAnnotations.isEmpty()) sb.append(m.name);
        return sb.toString();
    }

    @Test
    void instanceHandlersOfAutoSubscribedClassesAreDroppedStaticOnesKept() {
        var step = new LegacyInstanceSubscriberStep();
        byte[] out = step.apply("com.ex.Obj$", subscriber(true));
        assertEquals("stat", annotated(out));
        assertSame(out, step.apply("com.ex.Obj$", out), "idempotent");
    }

    @Test
    void classesWithoutTheSubscriberAnnotationAreUntouched() {
        byte[] in = subscriber(false);
        assertSame(in, new LegacyInstanceSubscriberStep().apply("com.ex.Obj$", in));
    }

    @Test
    void instanceOnlyModuleClassLosesTheClassAnnotationAndRegistersOnARealBus() throws Exception {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/Mod$", null, "java/lang/Object", null);
        cw.visitAnnotation("Lnet/neoforged/fml/common/EventBusSubscriber;", true).visitEnd();
        var m = cw.visitMethod(Opcodes.ACC_PUBLIC, "on", "(Lnet/neoforged/bus/api/Event;)V", null, null);
        m.visitAnnotation("Lnet/neoforged/bus/api/SubscribeEvent;", true).visitEnd();
        m.visitCode();
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        cw.visitEnd();
        byte[] in = cw.toByteArray();
        var bus = net.neoforged.bus.api.BusBuilder.builder().build();
        Class<?> before = define(in);
        assertThrows(IllegalArgumentException.class, () -> bus.register(before));

        byte[] out = new LegacyInstanceSubscriberStep().apply("com.ex.Mod$", in);
        ClassNode n = new ClassNode();
        new ClassReader(out).accept(n, 0);
        assertTrue(n.visibleAnnotations == null || n.visibleAnnotations.isEmpty());
        assertEquals("", annotated(out));
        assertSame(out, new LegacyInstanceSubscriberStep().apply("com.ex.Mod$", out), "idempotent");
    }

    private static Class<?> define(byte[] bytes) {
        return new ClassLoader(LegacyInstanceSubscriberStepTest.class.getClassLoader()) {
            Class<?> def() {
                return defineClass(null, bytes, 0, bytes.length);
            }
        }.def();
    }
}
