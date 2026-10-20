package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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
    void scalaModuleInstanceHandlerGetsAStaticBridgeForAutomaticRegistration() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/Mod$", null, "java/lang/Object", null);
        cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "MODULE$", "Lcom/ex/Mod$;", null, null).visitEnd();
        cw.visitAnnotation("Lnet/neoforged/fml/common/EventBusSubscriber;", true).visitEnd();
        var m = cw.visitMethod(Opcodes.ACC_PUBLIC, "on", "(Lnet/neoforged/bus/api/Event;)V", null, null);
        m.visitAnnotation("Lnet/neoforged/bus/api/SubscribeEvent;", true).visitEnd();
        m.visitCode();
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        cw.visitEnd();
        byte[] in = cw.toByteArray();
        byte[] out = new LegacyInstanceSubscriberStep().apply("com.ex.Mod$", in);
        ClassNode n = new ClassNode();
        new ClassReader(out).accept(n, 0);
        assertTrue(n.visibleAnnotations != null && n.visibleAnnotations.stream()
                .anyMatch(annotation -> annotation.desc.equals("Lnet/neoforged/fml/common/EventBusSubscriber;")));
        assertEquals("forgestuck$event$on$0", annotated(out));
        var bridge = n.methods.stream().filter(method -> method.name.equals("forgestuck$event$on$0")
                && (method.access & Opcodes.ACC_STATIC) != 0).findFirst().orElseThrow();
        assertTrue(bridge.visibleAnnotations.stream()
                .anyMatch(annotation -> annotation.desc.equals("Lnet/neoforged/bus/api/SubscribeEvent;")));
        assertSame(out, new LegacyInstanceSubscriberStep().apply("com.ex.Mod$", out), "idempotent");
    }

    @Test
    void scalaModuleDoesNotDuplicateItsStaticCompanionHandlers() {
        ClassWriter companionWriter = new ClassWriter(0);
        companionWriter.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/Mod", null, "java/lang/Object", null);
        companionWriter.visitAnnotation("Lnet/neoforged/fml/common/EventBusSubscriber;", true).visitEnd();
        var staticHandler = companionWriter.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "on",
                "(Ljava/lang/Object;)V", null, null);
        staticHandler.visitAnnotation("Lnet/neoforged/bus/api/SubscribeEvent;", true).visitEnd();
        staticHandler.visitEnd();
        companionWriter.visitEnd();

        ClassWriter moduleWriter = new ClassWriter(0);
        moduleWriter.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/Mod$", null, "java/lang/Object", null);
        moduleWriter.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "MODULE$", "Lcom/ex/Mod$;", null, null).visitEnd();
        moduleWriter.visitAnnotation("Lnet/neoforged/fml/common/EventBusSubscriber;", true).visitEnd();
        var instanceHandler = moduleWriter.visitMethod(Opcodes.ACC_PUBLIC, "on", "(Ljava/lang/Object;)V", null, null);
        instanceHandler.visitAnnotation("Lnet/neoforged/bus/api/SubscribeEvent;", true).visitEnd();
        instanceHandler.visitEnd();
        moduleWriter.visitEnd();

        LegacyInstanceSubscriberStep step = new LegacyInstanceSubscriberStep();
        step.scan("com.ex.Mod", companionWriter.toByteArray(), null);
        byte[] transformed = step.apply("com.ex.Mod$", moduleWriter.toByteArray());
        ClassNode node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);

        assertTrue(node.visibleAnnotations == null || node.visibleAnnotations.stream()
                .noneMatch(annotation -> annotation.desc.equals("Lnet/neoforged/fml/common/Mod$EventBusSubscriber;")
                        || annotation.desc.equals("Lnet/neoforged/fml/common/EventBusSubscriber;")));
        assertTrue(node.methods.stream().noneMatch(method -> method.name.startsWith("forgestuck$event$")));
    }

}
