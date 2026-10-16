package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

class ResourceLocationClassTransformStepTest {
    private static final String RL = "net/minecraft/resources/ResourceLocation";
    private final ResourceLocationClassTransformStep step = new ResourceLocationClassTransformStep();

    private static byte[] build(String owner, String desc, int args) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/Foo", null, "java/lang/Object", null);
        var mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "make", "()Ljava/lang/Object;", null, null);
        mv.visitCode();
        mv.visitTypeInsn(Opcodes.NEW, owner);
        mv.visitInsn(Opcodes.DUP);
        for (int i = 0; i < args; i++) mv.visitLdcInsn("a" + i);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, "<init>", desc, false);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static MethodNode method(byte[] bytes) {
        ClassNode n = new ClassNode();
        new ClassReader(bytes).accept(n, 0);
        return n.methods.get(0);
    }

    @Test
    void rewritesTwoArgConstructor() {
        MethodNode m = method(step.apply("com.example.Foo",
                build(RL, "(Ljava/lang/String;Ljava/lang/String;)V", 2)));
        MethodInsnNode call = null;
        for (AbstractInsnNode i : m.instructions) {
            assertNotEquals(Opcodes.NEW, i.getOpcode());
            assertNotEquals(Opcodes.DUP, i.getOpcode());
            if (i instanceof MethodInsnNode mi) call = mi;
        }
        assertNotNull(call);
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals(RL, call.owner);
        assertEquals("fromNamespaceAndPath", call.name);
        assertEquals("(Ljava/lang/String;Ljava/lang/String;)L" + RL + ";", call.desc);
    }

    @Test
    void rewritesOneArgConstructor() {
        MethodNode m = method(step.apply("com.example.Foo", build(RL, "(Ljava/lang/String;)V", 1)));
        MethodInsnNode call = null;
        for (AbstractInsnNode i : m.instructions) {
            assertNotEquals(Opcodes.NEW, i.getOpcode());
            if (i instanceof MethodInsnNode mi) call = mi;
        }
        assertNotNull(call);
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("parse", call.name);
        assertEquals("(Ljava/lang/String;)L" + RL + ";", call.desc);
    }

    @Test
    void leavesUnrelatedConstructorsUntouched() {
        byte[] other = build("java/lang/StringBuilder", "(Ljava/lang/String;)V", 1);
        assertSame(other, step.apply("com.example.Foo", other));
        byte[] unknownDesc = build(RL, "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", 3);
        assertSame(unknownDesc, step.apply("com.example.Foo", unknownDesc));
        assertTrue(method(other).instructions.getFirst() != null);
        assertInstanceOf(TypeInsnNode.class, firstReal(method(unknownDesc)));
    }

    private static AbstractInsnNode firstReal(MethodNode m) {
        for (AbstractInsnNode i : m.instructions) if (i.getOpcode() >= 0) return i;
        return null;
    }
}
