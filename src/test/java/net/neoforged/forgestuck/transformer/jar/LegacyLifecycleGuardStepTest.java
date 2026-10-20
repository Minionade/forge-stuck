package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

class LegacyLifecycleGuardStepTest {
    private static byte[] modClass(String setupDescriptor) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "example/TestMod", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "setup", setupDescriptor, null, null);
        method.visitCode();
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 2);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    @Test
    void setupCallbacksAreGuardedAndTransformIsIdempotent() {
        var step = new LegacyLifecycleGuardStep();
        byte[] input = modClass("(Lnet/minecraftforge/fml/event/lifecycle/FMLCommonSetupEvent;)V");
        byte[] transformed = step.apply("example.TestMod", input);
        ClassNode node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);
        var method = node.methods.stream().filter(m -> m.name.equals("setup")).findFirst().orElseThrow();
        var guard = java.util.Arrays.stream(method.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .filter(call -> call.owner.equals("net/neoforged/forgestuck/compat/LegacyLifecycleGuards"))
                .findFirst().orElseThrow();
        assertEquals("firstInvocation", guard.name);
        assertEquals("(Ljava/lang/Object;Ljava/lang/String;)Z", guard.desc);
        assertTrue(java.util.Arrays.stream(method.instructions.toArray()).anyMatch(JumpInsnNode.class::isInstance));
        assertSame(transformed, step.apply("example.TestMod", transformed));
    }

    @Test
    void clientAndDedicatedServerSetupCallbacksAreGuarded() {
        var step = new LegacyLifecycleGuardStep();
        for (String event : new String[] {"FMLClientSetupEvent", "FMLDedicatedServerSetupEvent"}) {
            byte[] input = modClass("(Lnet/minecraftforge/fml/event/lifecycle/" + event + ";)V");
            ClassNode node = new ClassNode();
            new ClassReader(step.apply("example.TestMod", input)).accept(node, 0);
            var method = node.methods.stream().filter(m -> m.name.equals("setup")).findFirst().orElseThrow();
            assertTrue(java.util.Arrays.stream(method.instructions.toArray())
                    .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                    .anyMatch(call -> call.owner.equals("net/neoforged/forgestuck/compat/LegacyLifecycleGuards")));
        }
    }

    @Test
    void nonSetupMethodsAreUntouched() {
        byte[] input = modClass("(Ljava/lang/Object;)V");
        assertSame(input, new LegacyLifecycleGuardStep().apply("example.TestMod", input));
    }

    @Test
    void firstInvocationIsTrackedPerOwnerAndCallback() {
        Object owner = new Object();
        assertTrue(net.neoforged.forgestuck.compat.LegacyLifecycleGuards.firstInvocation(owner, "setup()V"));
        assertTrue(net.neoforged.forgestuck.compat.LegacyLifecycleGuards
                .firstInvocation(owner, "anotherSetup()V"));
        org.junit.jupiter.api.Assertions.assertFalse(
                net.neoforged.forgestuck.compat.LegacyLifecycleGuards.firstInvocation(owner, "setup()V"));
        assertTrue(net.neoforged.forgestuck.compat.LegacyLifecycleGuards
                .firstInvocation(new Object(), "setup()V"));
    }
}
