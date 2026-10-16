package net.neoforged.forgestuck.transformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * ASM Class Transformer operating at classloading time.
 * Redirects 1.20.1 ItemStack NBT methods to LegacyItemStackShim static equivalents.
 */
public class ForgeClassTransformer {

    private static final String TARGET_ITEMSTACK = "net/minecraft/world/item/ItemStack";
    private static final String SHIM_CLASS = "net/neoforged/forgestuck/shim/LegacyItemStackShim";

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
                if (insn instanceof MethodInsnNode methodInsn) {
                    if (methodInsn.getOpcode() == Opcodes.INVOKEVIRTUAL &&
                        TARGET_ITEMSTACK.equals(methodInsn.owner) &&
                        "getTag".equals(methodInsn.name) &&
                        "()Lnet/minecraft/nbt/CompoundTag;".equals(methodInsn.desc)) {

                        method.instructions.set(insn, new MethodInsnNode(
                            Opcodes.INVOKESTATIC, SHIM_CLASS, "getTag",
                            "(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/nbt/CompoundTag;", false
                        ));
                        modified = true;
                    } else if (methodInsn.getOpcode() == Opcodes.INVOKEVIRTUAL &&
                               TARGET_ITEMSTACK.equals(methodInsn.owner) &&
                               "getOrCreateTag".equals(methodInsn.name) &&
                               "()Lnet/minecraft/nbt/CompoundTag;".equals(methodInsn.desc)) {

                        method.instructions.set(insn, new MethodInsnNode(
                            Opcodes.INVOKESTATIC, SHIM_CLASS, "getOrCreateTag",
                            "(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/nbt/CompoundTag;", false
                        ));
                        modified = true;
                    } else if (methodInsn.getOpcode() == Opcodes.INVOKEVIRTUAL &&
                               TARGET_ITEMSTACK.equals(methodInsn.owner) &&
                               "setTag".equals(methodInsn.name) &&
                               "(Lnet/minecraft/nbt/CompoundTag;)V".equals(methodInsn.desc)) {

                        method.instructions.set(insn, new MethodInsnNode(
                            Opcodes.INVOKESTATIC, SHIM_CLASS, "setTag",
                            "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/nbt/CompoundTag;)V", false
                        ));
                        modified = true;
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
