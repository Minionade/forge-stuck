package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class LegacyTranslatableComponentStepTest {
    @Test
    void redirectsVarargsTranslationCallsToTheLegacyArgumentShim() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyTranslations", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "translate",
                "(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/network/chat/Component", "translatable",
                "(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;", true);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(2, 2);
        method.visitEnd();
        writer.visitEnd();

        ClassNode node = new ClassNode();
        new ClassReader(new LegacyTranslatableComponentStep().apply("sample.LegacyTranslations", writer.toByteArray()))
                .accept(node, 0);
        var call = Arrays.stream(node.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals("net/neoforged/forgestuck/compat/LegacyTranslatableComponentShim", call.owner);
        assertEquals("translatable", call.name);
        assertEquals("(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;",
                call.desc);
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        org.junit.jupiter.api.Assertions.assertFalse(call.itf);
    }
}
