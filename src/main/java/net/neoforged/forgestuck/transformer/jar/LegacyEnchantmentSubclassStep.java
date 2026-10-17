package net.neoforged.forgestuck.transformer.jar;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * NeoForge 1.21.1's {@code Enchantment} is a final record, so 1.20.1 classes that extend it cannot load. This step
 * re-parents them onto the non-final {@code compat.LegacyEnchantment} and redirects their {@code super(...)}
 * constructor call; {@code Enchantment$Rarity}/{@code EnchantmentCategory} in the descriptor are then mapped to the
 * compat tokens by the owner remap step, so it must run before {@link ForgeOwnerRemapStep}. The re-parented class is
 * a plain object, not a game enchantment.
 */
public final class LegacyEnchantmentSubclassStep implements ClassTransformStep {
    private static final String ENCHANTMENT = "net/minecraft/world/item/enchantment/Enchantment";
    private static final String LEGACY = "net/neoforged/forgestuck/compat/LegacyEnchantment";

    @Override
    public byte[] apply(String className, byte[] bytes) {
        if (bytes == null || className.startsWith("net.neoforged.forgestuck")) return bytes;
        try {
            if (!ENCHANTMENT.equals(new ClassReader(bytes).getSuperName())) return bytes;
        } catch (RuntimeException e) {
            return bytes;
        }
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        node.superName = LEGACY;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("<init>")) continue;
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL
                        && ENCHANTMENT.equals(call.owner) && call.name.equals("<init>")) {
                    call.owner = LEGACY;
                }
            }
        }
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }
}
