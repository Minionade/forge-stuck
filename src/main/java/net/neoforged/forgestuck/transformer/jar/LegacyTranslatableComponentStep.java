package net.neoforged.forgestuck.transformer.jar;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Preserves the legacy ability to pass ResourceLocations as translation arguments. */
final class LegacyTranslatableComponentStep implements ClassTransformStep {
    private static final String COMPONENT = "net/minecraft/network/chat/Component";
    private static final String SHIM =
            "net/neoforged/forgestuck/compat/LegacyTranslatableComponentShim";
    private static final String DESCRIPTOR =
            "(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;";

    @Override
    public byte[] apply(String className, byte[] bytes) {
        if (bytes == null) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        boolean modified = false;
        for (var method : node.methods) {
            if (method.instructions == null) continue;
            for (var instruction : method.instructions) {
                if (!(instruction instanceof MethodInsnNode call) || call.getOpcode() != org.objectweb.asm.Opcodes.INVOKESTATIC
                        || !call.owner.equals(COMPONENT) || !call.name.equals("translatable")
                        || !call.desc.equals(DESCRIPTOR)) continue;
                call.owner = SHIM;
                call.itf = false;
                modified = true;
            }
        }
        if (!modified) return bytes;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }
}
