package net.neoforged.forgestuck.transformer.jar;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * Rewrites 1.20.1 {@code new ResourceLocation(String[, String])} call sites to the 1.21 static factories
 * ({@code parse} / {@code fromNamespaceAndPath}), because the constructors became private.
 */
public class ResourceLocationClassTransformStep implements ClassTransformStep {
    private static final String RL = "net/minecraft/resources/ResourceLocation";
    private static final String RL_DESC = "Lnet/minecraft/resources/ResourceLocation;";
    private static final String CTOR_ONE = "(Ljava/lang/String;)V";
    private static final String CTOR_TWO = "(Ljava/lang/String;Ljava/lang/String;)V";

    @Override
    public byte[] apply(String className, byte[] bytes) {
        if (bytes == null || className.startsWith("net.neoforged.forgestuck") || className.equals(RL.replace('/', '.'))) {
            return bytes;
        }
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);

        boolean modified = false;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL
                        && RL.equals(call.owner) && "<init>".equals(call.name)
                        && (CTOR_ONE.equals(call.desc) || CTOR_TWO.equals(call.desc))) {
                    modified |= rewrite(method, call);
                } else if (insn instanceof InvokeDynamicInsnNode indy) {
                    for (int i = 0; i < indy.bsmArgs.length; i++) {
                        if (indy.bsmArgs[i] instanceof Handle handle) {
                            Handle replacement = rewriteConstructorHandle(handle);
                            if (replacement != null) {
                                indy.bsmArgs[i] = replacement;
                                modified = true;
                            }
                        }
                    }
                } else if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof Handle handle) {
                    Handle replacement = rewriteConstructorHandle(handle);
                    if (replacement != null) {
                        ldc.cst = replacement;
                        modified = true;
                    }
                }
            }
        }
        if (!modified) return bytes;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static boolean rewrite(MethodNode method, MethodInsnNode init) {
        int depth = 0;
        TypeInsnNode neu = null;
        for (AbstractInsnNode cur = init.getPrevious(); cur != null; cur = cur.getPrevious()) {
            // A stack map frame in between may describe the uninitialized object; stay conservative.
            if (cur instanceof FrameNode) return false;
            if (cur instanceof MethodInsnNode m && m.getOpcode() == Opcodes.INVOKESPECIAL
                    && RL.equals(m.owner) && "<init>".equals(m.name)) {
                depth++;
            } else if (cur instanceof TypeInsnNode t && t.getOpcode() == Opcodes.NEW && RL.equals(t.desc)) {
                if (depth == 0) {
                    neu = t;
                    break;
                }
                depth--;
            }
        }
        if (neu == null) return false;
        AbstractInsnNode dup = neu.getNext();
        while (dup != null && dup.getOpcode() == -1) dup = dup.getNext();
        if (dup == null || dup.getOpcode() != Opcodes.DUP) return false;

        method.instructions.remove(neu);
        method.instructions.remove(dup);
        boolean one = CTOR_ONE.equals(init.desc);
        method.instructions.set(init, new MethodInsnNode(Opcodes.INVOKESTATIC, RL,
                one ? "parse" : "fromNamespaceAndPath",
                one ? "(Ljava/lang/String;)" + RL_DESC : "(Ljava/lang/String;Ljava/lang/String;)" + RL_DESC, false));
        return true;
    }

    private static Handle rewriteConstructorHandle(Handle handle) {
        if (handle.getTag() != Opcodes.H_NEWINVOKESPECIAL
                || !RL.equals(handle.getOwner())
                || !"<init>".equals(handle.getName())) {
            return null;
        }
        boolean one = CTOR_ONE.equals(handle.getDesc());
        if (!one && !CTOR_TWO.equals(handle.getDesc())) return null;
        return new Handle(Opcodes.H_INVOKESTATIC, RL, one ? "parse" : "fromNamespaceAndPath",
                one ? "(Ljava/lang/String;)" + RL_DESC
                        : "(Ljava/lang/String;Ljava/lang/String;)" + RL_DESC,
                false);
    }
}
