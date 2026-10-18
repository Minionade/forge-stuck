package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class SrgOwnerAwareMappingTest {
    private static final LegacyNameMappings M = new LegacyNameMappings(Map.of(
            "m_1_", "bare",
            "m_1_(I)V", "byDesc",
            "net/minecraft/a/A#m_1_", "byOwner",
            "net/minecraft/a/A#m_1_(I)V", "byOwnerDesc",
            "f_2_@I", "intField"), Set.of());

    @Test
    void precedenceIsOwnerDescThenDescThenOwnerThenBare() {
        assertEquals("byOwnerDesc", M.map("net/minecraft/a/A", "m_1_", "(I)V"));
        assertEquals("byDesc", M.map("net/minecraft/b/B", "m_1_", "(I)V"));
        assertEquals("byOwner", M.map("net/minecraft/a/A", "m_1_", "()V"));
        assertEquals("bare", M.map("net/minecraft/b/B", "m_1_", "()V"));
        assertEquals("intField", M.map("net/minecraft/b/B", "f_2_", "I"));
        assertNull(M.map("net/minecraft/b/B", "f_2_", "J"));
    }

    @Test
    void sameSrgNameOnDifferentOwnersOrDescriptorsMapsDifferently() {
        byte[] out = new SrgMemberRemapStep(M).apply("zz.C", caller());
        ClassNode node = new ClassNode();
        new ClassReader(out).accept(node, 0);
        var insns = node.methods.get(0).instructions.toArray();
        String a = null, b = null;
        for (var i : insns) {
            if (i instanceof MethodInsnNode c) {
                if (c.owner.equals("net/minecraft/a/A")) a = c.name; else b = c.name;
            }
        }
        assertEquals("byOwnerDesc", a);
        assertEquals("byDesc", b);
    }

    @Test
    void addsNamedBridgeForSrgOverrideOfGameInterface() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "zz/E", null, "java/lang/Object",
                new String[] {"net/minecraft/a/Iface"});
        var m = cw.visitMethod(Opcodes.ACC_PUBLIC, "m_1_", "(I)Ljava/lang/String;", null, null);
        m.visitCode();
        m.visitInsn(Opcodes.ACONST_NULL);
        m.visitInsn(Opcodes.ARETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        cw.visitEnd();
        byte[] out = new SrgMemberRemapStep(M).apply("zz.E", cw.toByteArray());
        ClassNode node = new ClassNode();
        new ClassReader(out).accept(node, 0);
        assertTrue(node.methods.stream().anyMatch(x -> x.name.equals("bare") && x.desc.equals("(I)Ljava/lang/String;")));
        assertTrue(node.methods.stream().anyMatch(x -> x.name.equals("m_1_")));
    }

    @Test
    void remapsInheritedResourceLocationWithSuffixOnModSubclassOwner() {
        String owner = "slimeknights/tconstruct/library/modifiers/ModifierId";
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "zz/ResourceIdCaller", null, "java/lang/Object", null);
        var method = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "append",
                "(L" + owner + ";)Lnet/minecraft/resources/ResourceLocation;", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitLdcInsn("_pattern");
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, "m_266382_",
                "(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        cw.visitEnd();

        ClassNode node = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("zz.ResourceIdCaller", cw.toByteArray())).accept(node, 0);
        MethodInsnNode call = null;
        for (var insn : node.methods.get(0).instructions) {
            if (insn instanceof MethodInsnNode methodCall) call = methodCall;
        }
        assertNotNull(call);
        assertEquals(owner, call.owner);
        assertEquals("withSuffix", call.name);
    }

    private static byte[] caller() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "zz/C", null, "java/lang/Object", null);
        var m = cw.visitMethod(Opcodes.ACC_PUBLIC, "run", "(Lnet/minecraft/a/A;Lnet/minecraft/b/B;)V", null, null);
        m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD, 1);
        m.visitInsn(Opcodes.ICONST_1);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/a/A", "m_1_", "(I)V", false);
        m.visitVarInsn(Opcodes.ALOAD, 2);
        m.visitInsn(Opcodes.ICONST_1);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/b/B", "m_1_", "(I)V", false);
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }
}
