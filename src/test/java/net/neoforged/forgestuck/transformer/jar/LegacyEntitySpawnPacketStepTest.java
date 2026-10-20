package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

class LegacyEntitySpawnPacketStepTest {
    @Test
    void rewritesLegacySpawnPacketOverrideToTrackingAwareNeoForgeMethod() {
        String entity = "net/minecraft/world/entity/Entity";
        String packet = "net/minecraft/network/protocol/Packet";
        String serverEntity = "net/minecraft/server/level/ServerEntity";
        String descriptor = "(L" + entity + ";)L" + packet + ";";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/SpawnEntity", null, entity, null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "m_5654_", "()L" + packet + ";", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraftforge/network/NetworkHooks",
                "getEntitySpawningPacket", descriptor, false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();

        byte[] transformed = new LegacyEntitySpawnPacketStep().apply("sample.SpawnEntity", writer.toByteArray());
        ClassNode node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);
        var methodNode = node.methods.stream().filter(candidate -> candidate.name.equals("getAddEntityPacket"))
                .findFirst().orElseThrow();
        assertEquals("(L" + serverEntity + ";)L" + packet + ";", methodNode.desc);
        var instructions = Arrays.stream(methodNode.instructions.toArray()).filter(instruction ->
                instruction.getOpcode() >= 0).toList();
        assertEquals(Opcodes.NEW, instructions.get(0).getOpcode());
        assertEquals("net/minecraft/network/protocol/game/ClientboundAddEntityPacket",
                ((TypeInsnNode) instructions.get(0)).desc);
        assertFalse(Arrays.stream(methodNode.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .anyMatch(call -> call.owner.equals("net/minecraftforge/network/NetworkHooks")));
        MethodInsnNode constructor = Arrays.stream(methodNode.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .filter(call -> call.name.equals("<init>"))
                .findFirst().orElseThrow();
        assertEquals("(L" + entity + ";L" + serverEntity + ";)V", constructor.desc);
    }

    @Test
    void leavesUnrecognizedLegacySpawnMethodUntouched() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/OtherEntity", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "spawn", "()Ljava/lang/Object;", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();

        byte[] original = writer.toByteArray();
        assertEquals(original, new LegacyEntitySpawnPacketStep().apply("sample.OtherEntity", original));
    }
}
