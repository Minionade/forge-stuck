package net.neoforged.forgestuck.transformer.jar;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

/** Constructor adaptations performed before exact-owner remapping. */
public final class LegacyAttackAndTreeStep implements ClassTransformStep {
    private static final String SAPLING = "net/minecraft/world/level/block/SaplingBlock";
    private static final String COMPAT_SAPLING = "net/neoforged/forgestuck/compat/LegacySaplingBlock";
    private static final String OLD_GROWER = "Lnet/minecraft/world/level/block/grower/AbstractTreeGrower;";
    private static final String OLD_CTOR = "(" + OLD_GROWER + "Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)V";
    private static final String ATTACK = "net/minecraftforge/event/entity/living/LivingAttackEvent";
    private static final String SOURCE_AMOUNT = "Lnet/minecraft/world/damagesource/DamageSource;F";
    private static final String CONTAINER = "Lnet/neoforged/neoforge/common/damagesource/DamageContainer;";

    @Override
    public byte[] apply(String className, byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        // Never rewrite a mixin's structural target assumptions.
        if (node.invisibleAnnotations != null && node.invisibleAnnotations.stream()
                .anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;"))) return bytes;
        boolean legacySapling = node.methods.stream().flatMap(m -> java.util.Arrays.stream(m.instructions.toArray()))
                .anyMatch(i -> i instanceof MethodInsnNode c && c.owner.equals(SAPLING)
                        && c.name.equals("<init>") && c.desc.equals(OLD_CTOR));
        boolean changed = false;
        if (legacySapling && SAPLING.equals(node.superName)) {
            node.superName = COMPAT_SAPLING;
            changed = true;
        }
        for (var method : node.methods) {
            for (var instruction : method.instructions.toArray()) {
                if (legacySapling && instruction instanceof TypeInsnNode t
                        && t.getOpcode() == Opcodes.NEW && t.desc.equals(SAPLING)) {
                    t.desc = COMPAT_SAPLING;
                    changed = true;
                }
                if (!(instruction instanceof MethodInsnNode call)) continue;
                if (legacySapling && call.owner.equals(SAPLING) && call.name.equals("<init>")) {
                    call.owner = COMPAT_SAPLING;
                    changed = true;
                }
                if (call.owner.equals(ATTACK) && call.name.equals("<init>")
                        && call.desc.equals("(Lnet/minecraft/world/entity/LivingEntity;" + SOURCE_AMOUNT + ")V")) {
                    method.instructions.insertBefore(call, new MethodInsnNode(Opcodes.INVOKESTATIC,
                            "net/neoforged/forgestuck/compat/LegacyLivingAttack", "container",
                            "(" + SOURCE_AMOUNT + ")" + CONTAINER, false));
                    call.desc = "(Lnet/minecraft/world/entity/LivingEntity;" + CONTAINER + ")V";
                    changed = true;
                }
            }
        }
        // Rename the removed methods consistently, including declarations, mod-owned calls,
        // super calls and method handles. A virtual alias would recurse when an override calls super.
        boolean[] renamed = {false};
        Remapper names = new Remapper() {
            @Override
            public String mapMethodName(String owner, String name, String descriptor) {
                String mapped = null;
                if (name.equals("m_213888_") && descriptor.equals(
                        "(Lnet/minecraft/util/RandomSource;Z)Lnet/minecraft/resources/ResourceKey;")) {
                    mapped = "getConfiguredFeature";
                } else if (name.equals("m_213817_") && descriptor.equals(
                        "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/util/RandomSource;)Z")) {
                    mapped = "growTree";
                } else if (name.equals("m_213566_") && descriptor.equals(
                        "(Lnet/minecraft/util/RandomSource;)Lnet/minecraft/resources/ResourceKey;")) {
                    mapped = "getConfiguredMegaFeature";
                } else if (name.equals("m_222896_") && descriptor.equals(
                        "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/util/RandomSource;II)Z")) {
                    mapped = "placeMega";
                } else if (name.equals("m_59998_") && descriptor.equals(
                        "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;II)Z")) {
                    mapped = "isTwoByTwoSapling";
                }
                if (mapped != null) { renamed[0] = true; return mapped; }
                return name;
            }
        };
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(new ClassRemapper(writer, names));
        return changed || renamed[0] ? writer.toByteArray() : bytes;
    }
}
