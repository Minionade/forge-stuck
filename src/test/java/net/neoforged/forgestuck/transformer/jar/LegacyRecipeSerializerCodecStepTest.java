package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class LegacyRecipeSerializerCodecStepTest {
    @Test
    void addsMapAndStreamCodecAdaptersToLegacySerializerBase() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacySerializer", null, "java/lang/Object", null);
        var fromJson = writer.visitMethod(Opcodes.ACC_PUBLIC, "m_6729_",
                "(Lnet/minecraft/resources/ResourceLocation;Lcom/google/gson/JsonObject;)"
                        + "Lnet/minecraft/world/item/crafting/Recipe;",
                null, null);
        fromJson.visitCode();
        fromJson.visitInsn(Opcodes.ACONST_NULL);
        fromJson.visitInsn(Opcodes.ARETURN);
        fromJson.visitMaxs(1, 3);
        fromJson.visitEnd();
        writer.visitEnd();

        ClassNode node = new ClassNode();
        new ClassReader(new LegacyRecipeSerializerCodecStep().apply("sample.LegacySerializer", writer.toByteArray()))
                .accept(node, 0);
        var codec = node.methods.stream().filter(method -> method.name.equals("codec")).findFirst().orElseThrow();
        var streamCodec = node.methods.stream()
                .filter(method -> method.name.equals("streamCodec")).findFirst().orElseThrow();
        var codecCall = Arrays.stream(codec.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        var streamCodecCall = Arrays.stream(streamCodec.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        assertEquals("codec", codecCall.name);
        assertEquals("streamCodec", streamCodecCall.name);
        assertEquals("net/neoforged/forgestuck/compat/LegacyRecipeSerializerCodec", codecCall.owner);
        assertEquals(codecCall.owner, streamCodecCall.owner);
    }
}
