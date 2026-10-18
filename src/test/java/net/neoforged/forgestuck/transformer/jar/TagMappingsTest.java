package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class TagMappingsTest {
    @Test
    void registryTagCallsRemainInterfaceInvocationsAfterRemapping() {
        String registry = "net/minecraftforge/registries/IForgeRegistry";
        String manager = "net/minecraftforge/registries/tags/ITagManager";
        String tag = "net/minecraftforge/registries/tags/ITag";
        String key = "net/minecraft/tags/TagKey";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/ex/TagCalls", null, "java/lang/Object", null);

        var getManager = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "getManager",
                "(L" + registry + ";)L" + manager + ";", null, null);
        getManager.visitCode();
        getManager.visitVarInsn(Opcodes.ALOAD, 0);
        getManager.visitMethodInsn(Opcodes.INVOKEINTERFACE, registry, "tags", "()L" + manager + ";", true);
        getManager.visitInsn(Opcodes.ARETURN);
        getManager.visitMaxs(0, 0);
        getManager.visitEnd();

        var getTag = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "getTag",
                "(L" + manager + ";L" + key + ";)L" + tag + ";", null, null);
        getTag.visitCode();
        getTag.visitVarInsn(Opcodes.ALOAD, 0);
        getTag.visitVarInsn(Opcodes.ALOAD, 1);
        getTag.visitMethodInsn(Opcodes.INVOKEINTERFACE, manager, "getTag",
                "(L" + key + ";)L" + tag + ";", true);
        getTag.visitInsn(Opcodes.ARETURN);
        getTag.visitMaxs(0, 0);
        getTag.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new ForgeOwnerRemapStep().apply("com.ex.TagCalls", writer.toByteArray()))
                .accept(transformed, 0);

        MethodInsnNode[] calls = transformed.methods.stream()
                .flatMap(method -> java.util.Arrays.stream(method.instructions.toArray()))
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .toArray(MethodInsnNode[]::new);
        assertEquals(2, calls.length);
        assertEquals(Opcodes.INVOKEINTERFACE, calls[0].getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/IForgeRegistry", calls[0].owner);
        assertEquals("()Lnet/neoforged/forgestuck/compat/LegacyTagManager;", calls[0].desc);
        assertEquals(Opcodes.INVOKEINTERFACE, calls[1].getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyTagManager", calls[1].owner);
        assertEquals("(Lnet/minecraft/tags/TagKey;)Lnet/neoforged/forgestuck/compat/LegacyTag;", calls[1].desc);
    }
}
