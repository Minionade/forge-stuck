package net.neoforged.forgestuck.transformer.jar;

import java.nio.charset.StandardCharsets;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * 1.20 {@code new BufferBuilder(int)} no longer exists (1.21 builders wrap a {@code ByteBufferBuilder} and are bound to
 * a mode and format). Rewrites the {@code NEW/DUP/INVOKESPECIAL} sequence into
 * {@code LegacyBufferBuilders.create(int)} so classes holding such builders link; drawing with them is unsupported.
 */
public final class LegacyBufferBuilderRewriteStep implements ClassTransformStep {
    private static final String BUILDER = "com/mojang/blaze3d/vertex/BufferBuilder";
    private static final String FACTORY = "net/neoforged/forgestuck/compat/LegacyBufferBuilders";

    @Override
    public byte[] apply(String className, byte[] bytes) {
        if (bytes == null || className.startsWith("net.neoforged.forgestuck")) return bytes;
        if (!new String(bytes, StandardCharsets.ISO_8859_1).contains(BUILDER)) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        boolean modified = false;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (!(insn instanceof TypeInsnNode type) || type.getOpcode() != Opcodes.NEW
                        || !BUILDER.equals(type.desc)) continue;
                AbstractInsnNode dup = type.getNext();
                if (dup == null || dup.getOpcode() != Opcodes.DUP) continue;
                for (AbstractInsnNode n = dup.getNext(); n != null; n = n.getNext()) {
                    if (n instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL
                            && BUILDER.equals(call.owner) && call.name.equals("<init>")) {
                        if (call.desc.equals("(I)V")) {
                            method.instructions.remove(type);
                            method.instructions.remove(dup);
                            method.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, FACTORY, "create",
                                    "(I)L" + BUILDER + ";", false));
                            modified = true;
                        }
                        break;
                    }
                }
            }
        }
        if (!modified) return bytes;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }
}
