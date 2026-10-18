package net.neoforged.forgestuck.transformer.jar;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Reparents Forge particle types onto the adapter that supplies NeoForge's codec and stream codec. */
public final class LegacyParticleTypeRewriteStep implements ClassTransformStep {
    private static final String PARTICLE_TYPE = "net/minecraft/core/particles/ParticleType";
    private static final String LEGACY_TYPE = "net/neoforged/forgestuck/compat/LegacyParticleType";
    private static final String LEGACY_DESERIALIZER =
            "net/minecraft/core/particles/ParticleOptions$Deserializer";
    private static final String LEGACY_CODEC_DESC = "()Lcom/mojang/serialization/Codec;";
    private static final String LEGACY_CONSTRUCTOR_DESC = "(ZL" + LEGACY_DESERIALIZER + ";)V";

    @Override
    public byte[] apply(String className, byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        if (!PARTICLE_TYPE.equals(node.superName)) return bytes;

        MethodInsnNode constructor = null;
        for (MethodNode method : node.methods) {
            if (!"<init>".equals(method.name)) continue;
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL
                        && PARTICLE_TYPE.equals(call.owner) && "<init>".equals(call.name)
                        && LEGACY_CONSTRUCTOR_DESC.equals(call.desc)) {
                    constructor = call;
                }
            }
        }
        if (constructor == null) return bytes;

        node.superName = LEGACY_TYPE;
        if (node.signature != null) node.signature = node.signature.replace("L" + PARTICLE_TYPE + "<", "L" + LEGACY_TYPE + "<");
        constructor.owner = LEGACY_TYPE;
        for (MethodNode method : node.methods) {
            if ("codec".equals(method.name) && LEGACY_CODEC_DESC.equals(method.desc)) {
                method.name = "legacyCodec";
            } else if (method.name.startsWith("m_") && LEGACY_CODEC_DESC.equals(method.desc)) {
                method.name = "legacyCodec";
            }
        }

        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }
}
