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
    private static final String INGREDIENT = "Lnet/minecraft/world/item/crafting/Ingredient;";
    private static final String BRIDGE = "net/neoforged/forgestuck/compat/LegacyIngredientBridge";

    @Override
    public byte[] apply(String className, byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        if (node.interfaces == null || !node.interfaces.contains(SERIALIZER)) {
            return bytes;
        }
        boolean modified = false;
        for (MethodNode method : node.methods) {
            if (!method.desc.endsWith(")" + INGREDIENT)) {
                continue;
            }
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction.getOpcode() == Opcodes.ARETURN) {
                    method.instructions.insertBefore(instruction, new MethodInsnNode(Opcodes.INVOKESTATIC,
                            BRIDGE, "asVanilla", "(Ljava/lang/Object;)" + INGREDIENT, false));
                    modified = true;
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
}
