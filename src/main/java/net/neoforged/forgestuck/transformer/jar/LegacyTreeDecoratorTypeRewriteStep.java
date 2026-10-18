package net.neoforged.forgestuck.transformer.jar;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Wraps legacy tree decorator codecs in the map codec required by Minecraft 1.21.1. */
final class LegacyTreeDecoratorTypeRewriteStep implements ClassTransformStep {
    private static final String TYPE = "net/minecraft/world/level/levelgen/feature/treedecorators/TreeDecoratorType";
    private static final String CODEC = "com/mojang/serialization/Codec";
    private static final String MAP_CODEC = "com/mojang/serialization/MapCodec";

    @Override
    public byte[] apply(String className, byte[] bytes) {
        if (bytes == null || className.startsWith("net.neoforged.forgestuck")) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int rewritten = 0;
        for (MethodNode method : node.methods) {
            for (var insn : method.instructions.toArray()) {
                if (!(insn instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESPECIAL
                        || !call.owner.equals(TYPE) || !call.name.equals("<init>")
                        || !call.desc.equals("(L" + CODEC + ";)V")) continue;
                InsnList conversion = new InsnList();
                conversion.add(new LdcInsnNode("data"));
                conversion.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, CODEC, "fieldOf",
                        "(Ljava/lang/String;)L" + MAP_CODEC + ";", true));
                method.instructions.insertBefore(call, conversion);
                call.desc = "(L" + MAP_CODEC + ";)V";
                rewritten++;
            }
        }
        if (rewritten == 0) return bytes;
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }
}
