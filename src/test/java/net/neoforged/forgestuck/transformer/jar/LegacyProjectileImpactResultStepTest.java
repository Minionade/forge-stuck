package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

class LegacyProjectileImpactResultStepTest {
    @Test
    void mapsSkipEntityToNeoForgeImpactCancellation() {
        String event = "net/minecraftforge/event/entity/ProjectileImpactEvent";
        String result = event + "$ImpactResult";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/ProjectileListener", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "skip",
                "(L" + event + ";)V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitFieldInsn(Opcodes.GETSTATIC, result, "SKIP_ENTITY", "L" + result + ";");
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, event, "setImpactResult",
                "(L" + result + ";)V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(2, 1);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new LegacyProjectileImpactResultStep().apply(
                "sample.ProjectileListener", writer.toByteArray())).accept(transformed, 0);
        var instructions = Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(instruction -> instruction.getOpcode() >= 0).toList();
        assertEquals(Opcodes.ICONST_1, instructions.get(1).getOpcode());
        assertFalse(instructions.stream().anyMatch(FieldInsnNode.class::isInstance));
        MethodInsnNode call = instructions.stream().filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        assertEquals("setCanceled", call.name);
        assertEquals("(Z)V", call.desc);
        assertEquals(Opcodes.INVOKEVIRTUAL, call.getOpcode());
        assertFalse(call.desc.contains(result));
    }

    @Test
    void leavesOtherProjectileImpactMembersUntouched() {
        String event = "net/minecraftforge/event/entity/ProjectileImpactEvent";
        String result = event + "$ImpactResult";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/OtherProjectileListener", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "ordinary",
                "(L" + event + ";)V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitInsn(Opcodes.ICONST_0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, event, "setCanceled", "(Z)V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(2, 1);
        method.visitEnd();
        writer.visitEnd();

        byte[] original = writer.toByteArray();
        byte[] transformed = new LegacyProjectileImpactResultStep().apply(
                "sample.OtherProjectileListener", original);
        assertEquals(Arrays.toString(original), Arrays.toString(transformed));
    }
}
