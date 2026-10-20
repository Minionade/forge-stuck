package net.neoforged.forgestuck.transformer.jar;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Adapts legacy ingredient serializer parse results to NeoForge's {@code Ingredient} wrapper. */
public final class LegacyIngredientRewriteStep implements ClassTransformStep {
    private static final String SERIALIZER = "net/neoforged/forgestuck/compat/LegacyIngredientSerializer";
    private static final String COUNT_INGREDIENT = "cofh/lib/util/crafting/IngredientWithCount";
    private static final String INGREDIENT = "Lnet/minecraft/world/item/crafting/Ingredient;";
    private static final String BRIDGE = "net/neoforged/forgestuck/compat/LegacyIngredientBridge";

    @Override
    public byte[] apply(String className, byte[] bytes) {
        if (bytes == null) return bytes;
        boolean hasCountIngredient = contains(bytes, COUNT_INGREDIENT);
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        boolean legacySerializer = node.interfaces != null && node.interfaces.contains(SERIALIZER);
        if (!legacySerializer && !hasCountIngredient) {
            return bytes;
        }
        boolean modified = false;
        for (MethodNode method : node.methods) {
            if (!method.desc.endsWith(")" + INGREDIENT) || method.instructions == null) {
                continue;
            }
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction.getOpcode() == Opcodes.ARETURN) {
                    method.instructions.insertBefore(instruction, new MethodInsnNode(Opcodes.INVOKESTATIC,
                            BRIDGE, "asVanilla", "(Ljava/lang/Object;)" + INGREDIENT, false));
                    modified |= legacySerializer || hasCountIngredient;
                }
            }
        }
        if (!modified) {
            return bytes;
        }
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static boolean contains(byte[] bytes, String needle) {
        byte[] n = needle.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        outer:
        for (int i = 0; i <= bytes.length - n.length; i++) {
            for (int j = 0; j < n.length; j++) {
                if (bytes[i + j] != n[j]) continue outer;
            }
            return true;
        }
        return false;
    }
}
