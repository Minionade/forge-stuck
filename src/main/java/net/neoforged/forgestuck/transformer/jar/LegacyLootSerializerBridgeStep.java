package net.neoforged.forgestuck.transformer.jar;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/** Restores the erased deserialize override expected by the legacy loot serializer shim. */
final class LegacyLootSerializerBridgeStep implements ClassTransformStep {
    private static final String LEGACY_SERIALIZER =
            "net/neoforged/forgestuck/compat/LegacyLootConditionalFunctionSerializer";
    private static final String CONDITIONAL_FUNCTION =
            "net/minecraft/world/level/storage/loot/functions/LootItemConditionalFunction";
    private static final String DESERIALIZE_ARGUMENTS =
            "(Lcom/google/gson/JsonObject;Lcom/google/gson/JsonDeserializationContext;"
                    + "[Lnet/minecraft/world/level/storage/loot/predicates/LootItemCondition;)";
    private static final String ERASED_METHOD = DESERIALIZE_ARGUMENTS + "L" + CONDITIONAL_FUNCTION + ";";

    @Override
    public byte[] apply(String className, byte[] bytes) {
        if (bytes == null) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        if (!LEGACY_SERIALIZER.equals(node.superName)) return bytes;

        MethodNode implementation = null;
        for (MethodNode method : node.methods) {
            if (method.name.equals("deserialize")
                    && method.desc.startsWith(DESERIALIZE_ARGUMENTS)
                    && !method.desc.equals(ERASED_METHOD)
                    && (method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_STATIC)) == 0) {
                implementation = method;
                break;
            }
        }
        if (implementation == null || node.methods.stream().anyMatch(method ->
                method.name.equals("deserialize") && method.desc.equals(ERASED_METHOD))) return bytes;

        Type result = Type.getReturnType(implementation.desc);
        if (result.getSort() != Type.OBJECT || result.getInternalName().equals(CONDITIONAL_FUNCTION)) return bytes;

        MethodNode bridge = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC,
                "deserialize", ERASED_METHOD, null, null);
        bridge.visitCode();
        bridge.visitVarInsn(Opcodes.ALOAD, 0);
        bridge.visitVarInsn(Opcodes.ALOAD, 1);
        bridge.visitVarInsn(Opcodes.ALOAD, 2);
        bridge.visitVarInsn(Opcodes.ALOAD, 3);
        bridge.visitMethodInsn(Opcodes.INVOKEVIRTUAL, node.name, "deserialize", implementation.desc, false);
        bridge.visitInsn(Opcodes.ARETURN);
        bridge.visitMaxs(4, 4);
        bridge.visitEnd();
        node.methods.add(bridge);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }
}
