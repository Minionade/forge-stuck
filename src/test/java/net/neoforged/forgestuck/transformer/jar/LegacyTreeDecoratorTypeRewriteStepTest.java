package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

class LegacyTreeDecoratorTypeRewriteStepTest {
    @Test
    void wrapsLegacyCodecInNamedMapCodecField() {
        String type = "net/minecraft/world/level/levelgen/feature/treedecorators/TreeDecoratorType";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyDecoratorType", null, type, null);
        var init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Lcom/mojang/serialization/Codec;)V",
                null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitVarInsn(Opcodes.ALOAD, 1);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, type, "<init>", "(Lcom/mojang/serialization/Codec;)V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(2, 2);
        init.visitEnd();
        writer.visitEnd();

        byte[] transformed = new LegacyTreeDecoratorTypeRewriteStep()
                .apply("sample.LegacyDecoratorType", writer.toByteArray());
        ClassNode node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);
        var instructions = node.methods.get(0).instructions.toArray();
        assertEquals("data", Arrays.stream(instructions).filter(LdcInsnNode.class::isInstance)
                .map(LdcInsnNode.class::cast).findFirst().orElseThrow().cst);
        var call = Arrays.stream(instructions).filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast).filter(method -> method.name.equals("<init>"))
                .findFirst().orElseThrow();
        assertEquals("(Lcom/mojang/serialization/MapCodec;)V", call.desc);
        assertEquals(3, node.methods.get(0).maxStack);
    }

    @Test
    void recomputesMaxStackForCodecWrapperInsideConstructorInvocation() {
        String type = "net/minecraft/world/level/levelgen/feature/treedecorators/TreeDecoratorType";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyDecoratorType", null, type, null);
        var init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Lcom/mojang/serialization/Codec;)V",
                null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitVarInsn(Opcodes.ALOAD, 1);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, type, "<init>", "(Lcom/mojang/serialization/Codec;)V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(2, 2);
        init.visitEnd();
        var factory = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "factory",
                "()L" + type + ";", null, null);
        factory.visitCode();
        factory.visitTypeInsn(Opcodes.NEW, type);
        factory.visitInsn(Opcodes.DUP);
        factory.visitMethodInsn(Opcodes.INVOKESTATIC, "sample/LegacyDecoratorType", "codec",
                "()Lcom/mojang/serialization/Codec;", false);
        factory.visitMethodInsn(Opcodes.INVOKESPECIAL, type, "<init>",
                "(Lcom/mojang/serialization/Codec;)V", false);
        factory.visitInsn(Opcodes.ARETURN);
        factory.visitMaxs(3, 0);
        factory.visitEnd();
        writer.visitEnd();

        ClassNode node = new ClassNode();
        new ClassReader(new LegacyTreeDecoratorTypeRewriteStep().apply("sample.LegacyDecoratorType",
                writer.toByteArray())).accept(node, 0);
        assertEquals(4, node.methods.get(1).maxStack);
    }
}
