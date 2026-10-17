package net.neoforged.forgestuck.transformer.jar;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * NeoForge's {@code ICondition} requires {@code codec()} while Forge 1.20.1 conditions only have {@code getID()}.
 * For classes implementing the legacy interface directly, generates
 * {@code codec() { return LegacyConditions.codecFor(getID()); }}, which resolves the codec registered through the
 * legacy {@code CraftingHelper.register} (a precise {@code UnsupportedOperationException} if none was).
 */
public final class LegacyConditionCodecStep implements ClassTransformStep {
    static final String LEGACY_ICONDITION = "net/minecraftforge/common/crafting/conditions/ICondition";
    private static final String CODEC_DESC = "()Lcom/mojang/serialization/MapCodec;";
    private static final String RL = "Lnet/minecraft/resources/ResourceLocation;";

    @Override
    public byte[] apply(String className, byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_FRAMES);
        if ((node.access & Opcodes.ACC_INTERFACE) != 0 || node.interfaces == null
                || !node.interfaces.contains(LEGACY_ICONDITION)) {
            return bytes;
        }
        for (MethodNode m : node.methods) {
            if (m.name.equals("codec") && m.desc.equals(CODEC_DESC)) return bytes;
        }
        MethodNode codec = new MethodNode(Opcodes.ACC_PUBLIC, "codec", CODEC_DESC, null, null);
        codec.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        codec.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, node.name, "getID", "()" + RL, false));
        codec.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/neoforged/forgestuck/compat/LegacyConditions",
                "codecFor", "(" + RL + ")Lcom/mojang/serialization/MapCodec;", false));
        codec.instructions.add(new InsnNode(Opcodes.ARETURN));
        codec.maxStack = 1;
        codec.maxLocals = 1;
        node.methods.add(codec);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }
}