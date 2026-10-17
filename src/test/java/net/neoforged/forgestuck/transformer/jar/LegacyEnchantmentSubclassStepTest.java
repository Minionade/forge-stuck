package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class LegacyEnchantmentSubclassStepTest {
    private static final String ENCHANTMENT = "net/minecraft/world/item/enchantment/Enchantment";
    private static final String LEGACY = "net/neoforged/forgestuck/compat/LegacyEnchantment";
    private static final String CTOR = "(Lnet/minecraft/world/item/enchantment/Enchantment$Rarity;"
            + "Lnet/minecraft/world/item/enchantment/EnchantmentCategory;[Lnet/minecraft/world/entity/EquipmentSlot;)V";

    private static byte[] subclassOf(String superName) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "ex/Holding", null, superName, null);
        var mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "<init>", CTOR, false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(4, 1);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    @Test
    void reparentsEnchantmentSubclassAndItsSuperCall() {
        byte[] out = new LegacyEnchantmentSubclassStep().apply("ex.Holding", subclassOf(ENCHANTMENT));
        ClassNode node = new ClassNode();
        new ClassReader(out).accept(node, 0);
        assertEquals(LEGACY, node.superName);
        MethodNode init = node.methods.get(0);
        MethodInsnNode call = (MethodInsnNode) java.util.Arrays.stream(init.instructions.toArray())
                .filter(i -> i instanceof MethodInsnNode).findFirst().orElseThrow();
        assertEquals(LEGACY, call.owner);
    }

    @Test
    void leavesOtherClassesUntouched() {
        byte[] in = subclassOf("java/lang/Object");
        assertSame(in, new LegacyEnchantmentSubclassStep().apply("ex.Holding", in));
    }
}
