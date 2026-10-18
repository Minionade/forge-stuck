package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class LegacyParticleTypeRewriteStepTest {
    private static final String PARTICLE_TYPE = "net/minecraft/core/particles/ParticleType";
    private static final String DESERIALIZER = "net/minecraft/core/particles/ParticleOptions$Deserializer";
    private static final String CODEC = "com/mojang/serialization/Codec";

    @Test
    void reparentsLegacyParticleTypeAndKeepsCodecImplementationOnAdapter() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyType", null,
                PARTICLE_TYPE, null);
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "DESERIALIZER", "L" + DESERIALIZER + ";",
                null, null).visitEnd();
        var init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitInsn(Opcodes.ICONST_0);
        init.visitFieldInsn(Opcodes.GETSTATIC, "sample/LegacyType", "DESERIALIZER", "L" + DESERIALIZER + ";");
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, PARTICLE_TYPE, "<init>",
                "(ZL" + DESERIALIZER + ";)V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(3, 1);
        init.visitEnd();
        var codec = writer.visitMethod(Opcodes.ACC_PUBLIC, "m_7652_", "()L" + CODEC + ";", null, null);
        codec.visitCode();
        codec.visitInsn(Opcodes.ACONST_NULL);
        codec.visitInsn(Opcodes.ARETURN);
        codec.visitMaxs(1, 1);
        codec.visitEnd();
        writer.visitEnd();

        byte[] input = writer.toByteArray();
        byte[] output = new LegacyParticleTypeRewriteStep().apply("sample.LegacyType", input);
        output = new ForgeOwnerRemapStep().apply("sample.LegacyType", output);
        ClassNode node = new ClassNode();
        new ClassReader(output).accept(node, 0);

        assertEquals("net/neoforged/forgestuck/compat/LegacyParticleType", node.superName);
        assertEquals("Lnet/neoforged/forgestuck/compat/LegacyParticleDeserializer;",
                node.fields.stream().filter(field -> field.name.equals("DESERIALIZER"))
                        .findFirst().orElseThrow().desc);
        var constructor = node.methods.stream().filter(method -> method.name.equals("<init>")).findFirst().orElseThrow();
        var superCall = java.util.Arrays.stream(constructor.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .filter(call -> call.name.equals("<init>")).findFirst().orElseThrow();
        assertEquals("net/neoforged/forgestuck/compat/LegacyParticleType", superCall.owner);
        assertEquals("(ZLnet/neoforged/forgestuck/compat/LegacyParticleDeserializer;)V", superCall.desc);
        assertEquals("legacyCodec", node.methods.stream().filter(method -> method.name.equals("legacyCodec"))
                .findFirst().orElseThrow().name);
    }
}
