package net.neoforged.forgestuck.transformer;

import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * ASM Class Transformer operating at classloading time.
 * Redirects 1.20.1 ItemStack NBT methods to LegacyItemStackShim static equivalents. Production Forge 1.20.1 jars
 * reference the SRG names ({@code m_41783_} ...), source-level ones the Mojang names, so both are matched.
 */
public class ForgeClassTransformer {

    private static final String TARGET_ITEMSTACK = "net/minecraft/world/item/ItemStack";
    private static final String SHIM_CLASS = "net/neoforged/forgestuck/compat/LegacyItemStackShim";
    private static final String TAG = "Lnet/minecraft/nbt/CompoundTag;";
    private static final String STACK = "Lnet/minecraft/world/item/ItemStack;";

    private record Redirect(String srgName, String name, String desc, String shimDesc) {}

    /** SRG ids verified against Mojang 1.20.1 client mappings + MCPConfig 1.20.1 joined.tsrg. */
    static final List<Redirect> REDIRECTS = List.of(
            new Redirect("m_41783_", "getTag", "()" + TAG, "(" + STACK + ")" + TAG),
            new Redirect("m_41784_", "getOrCreateTag", "()" + TAG, "(" + STACK + ")" + TAG),
            new Redirect("m_41751_", "setTag", "(" + TAG + ")V", "(" + STACK + TAG + ")V"),
            new Redirect("m_41782_", "hasTag", "()Z", "(" + STACK + ")Z"));

    /** Every (shim method name, descriptor) the transformer can emit. */
    public static List<String[]> emittedShimCalls() {
        return REDIRECTS.stream().map(r -> new String[] {r.name(), r.shimDesc()}).toList();
    }

    public static byte[] transform(String className, byte[] basicClass) {
        if (basicClass == null || className.startsWith("net.neoforged.forgestuck")) {
            return basicClass;
        }

        ClassReader reader = new ClassReader(basicClass);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);

        boolean modified = false;

        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (!(insn instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKEVIRTUAL
                        || !TARGET_ITEMSTACK.equals(call.owner)) {
                    continue;
                }
                for (Redirect r : REDIRECTS) {
                    if ((r.name().equals(call.name) || r.srgName().equals(call.name)) && r.desc().equals(call.desc)) {
                        method.instructions.set(insn,
                                new MethodInsnNode(Opcodes.INVOKESTATIC, SHIM_CLASS, r.name(), r.shimDesc(), false));
                        modified = true;
                        break;
                    }
                }
            }
        }

        if (!modified) return basicClass;

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        return writer.toByteArray();
    }
}
