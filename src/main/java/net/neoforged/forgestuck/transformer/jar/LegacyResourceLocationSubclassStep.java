package net.neoforged.forgestuck.transformer.jar;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * 1.20 {@code ResourceLocation} was extensible (protected {@code (String,String,Dummy)} constructor); 1.21 made it
 * final with a private {@code (String,String)} constructor. For classes that extend it, this step adapts their
 * {@code super(...)} calls to the 1.21 constructor and the jar gets an access transformer
 * ({@link #AT_LINES}) that opens the class and constructor; the subclass then genuinely is a ResourceLocation, so
 * identity/equality/registry use is unchanged.
 */
public final class LegacyResourceLocationSubclassStep implements ClassTransformStep {
    static final String AT_PATH = "META-INF/accesstransformer.cfg";
    static final String AT_LINES = "\n# Forge Stuck: legacy ResourceLocation subclasses\n"
            + "public-f net.minecraft.resources.ResourceLocation\n"
            + "public net.minecraft.resources.ResourceLocation <init>(Ljava/lang/String;Ljava/lang/String;)V\n";
    private static final String RL = "net/minecraft/resources/ResourceLocation";
    private static final String HELPERS = "net/neoforged/forgestuck/compat/LegacyResourceLocations";
    private static final String S = "Ljava/lang/String;";

    static boolean extendsResourceLocation(byte[] bytes) {
        try {
            return RL.equals(new ClassReader(bytes).getSuperName());
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public byte[] apply(String className, byte[] bytes) {
        if (!extendsResourceLocation(bytes)) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        boolean modified = false;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("<init>")) continue;
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (!(insn instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESPECIAL
                        || !RL.equals(call.owner) || !call.name.equals("<init>")) continue;
                var list = method.instructions;
                switch (call.desc) {
                    case "(" + S + S + "Lnet/minecraft/resources/ResourceLocation$Dummy;)V" -> {
                        list.insertBefore(call, new InsnNode(Opcodes.POP));
                        call.desc = "(" + S + S + ")V";
                    }
                    case "(" + S + S + ")V" -> {
                        list.insertBefore(call, new InsnNode(Opcodes.DUP2));
                        list.insertBefore(call, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPERS, "validate",
                                "(" + S + S + ")V", false));
                    }
                    case "(" + S + ")V" -> {
                        list.insertBefore(call, new InsnNode(Opcodes.DUP));
                        list.insertBefore(call, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPERS, "namespace",
                                "(" + S + ")" + S, false));
                        list.insertBefore(call, new InsnNode(Opcodes.SWAP));
                        list.insertBefore(call, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPERS, "path",
                                "(" + S + ")" + S, false));
                        call.desc = "(" + S + S + ")V";
                    }
                    default -> {
                        continue;
                    }
                }
                modified = true;
            }
        }
        if (!modified) return bytes;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }
}
