package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class LegacyLootSerializerBridgeStepTest {
    @Test
    void restoresErasedDeserializeBridgeForLegacySerializer() {
        String serializer = "net/neoforged/forgestuck/compat/LegacyLootConditionalFunctionSerializer";
        String result = "sample/TileFunction";
        String arguments = "(Lcom/google/gson/JsonObject;Lcom/google/gson/JsonDeserializationContext;"
                + "[Lnet/minecraft/world/level/storage/loot/predicates/LootItemCondition;)";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/TileSerializer", null, serializer, null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "deserialize", arguments + "L" + result + ";",
                null, null);
        method.visitCode();
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 4);
        method.visitEnd();
        writer.visitEnd();

        ClassNode node = new ClassNode();
        new ClassReader(new LegacyLootSerializerBridgeStep().apply("sample.TileSerializer", writer.toByteArray()))
                .accept(node, 0);
        var bridge = node.methods.stream()
                .filter(candidate -> candidate.name.equals("deserialize")
                        && candidate.desc.equals(arguments
                                + "Lnet/minecraft/world/level/storage/loot/functions/LootItemConditionalFunction;"))
                .findFirst().orElseThrow();
        assertTrue((bridge.access & Opcodes.ACC_BRIDGE) != 0);
        assertTrue((bridge.access & Opcodes.ACC_SYNTHETIC) != 0);
        var target = Arrays.stream(bridge.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKEVIRTUAL, target.getOpcode());
        assertEquals("sample/TileSerializer", target.owner);
        assertEquals(arguments + "L" + result + ";", target.desc);
    }
}
