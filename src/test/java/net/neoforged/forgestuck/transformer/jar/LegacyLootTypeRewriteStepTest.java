package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class LegacyLootTypeRewriteStepTest {
    @Test
    void convertsLegacyConditionArrayForTheListBasedFunctionConstructor() {
        String function = "net/minecraft/world/level/storage/loot/functions/LootItemConditionalFunction";
        String conditions = "[Lnet/minecraft/world/level/storage/loot/predicates/LootItemCondition;";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyFunction", null, function, null);
        var constructor = writer.visitMethod(Opcodes.ACC_PROTECTED, "<init>", "(" + conditions + ")V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitVarInsn(Opcodes.ALOAD, 1);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, function, "<init>", "(" + conditions + ")V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(2, 2);
        constructor.visitEnd();
        writer.visitEnd();

        ClassNode node = new ClassNode();
        new ClassReader(new LegacyLootTypeRewriteStep().apply("sample.LegacyFunction", writer.toByteArray()))
                .accept(node, 0);
        var instructions = Arrays.stream(node.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .toList();
        var conversion = instructions.stream().filter(call -> call.owner.equals("java/util/Arrays"))
                .findFirst().orElseThrow();
        assertEquals("([Ljava/lang/Object;)Ljava/util/List;", conversion.desc);
        var call = instructions.stream().filter(method -> method.owner.equals(function)).findFirst().orElseThrow();
        assertEquals("(Ljava/util/List;)V", call.desc);
    }
}
