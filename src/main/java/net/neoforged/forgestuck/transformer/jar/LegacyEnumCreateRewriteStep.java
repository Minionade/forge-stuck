package net.neoforged.forgestuck.transformer.jar;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Redirects Forge's {@code ItemDisplayContext.create(...)} factory (absent in NeoForge 1.21.1) to
 * {@code LegacyEnumExtensions}, which looks up the constant generated from {@code enumextensions.json}; see
 * {@link LegacyEnumExtensionPlanner}.
 */
public final class LegacyEnumCreateRewriteStep implements ClassTransformStep {
    static final String ADAPTER = "net/neoforged/forgestuck/compat/LegacyEnumExtensions";

    @Override
    public byte[] apply(String className, byte[] bytes) {
        if (!LegacyEnumPrescan.mentionsItemDisplayContext(bytes)) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        boolean modified = false;
        for (MethodNode method : node.methods) {
            if (method.instructions == null) continue;
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC
                        && call.owner.equals(LegacyEnumExtensionPlanner.ITEM_DISPLAY_CONTEXT)
                        && call.name.equals("create") && call.desc.equals(LegacyEnumExtensionPlanner.CREATE_DESC)) {
                    call.owner = ADAPTER;
                    call.name = "itemDisplayContext";
                    modified = true;
                }
            }
        }
        if (!modified) return bytes;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }
}
