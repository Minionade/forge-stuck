package net.neoforged.forgestuck.transformer.jar;

import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Changes only calls whose placement argument still uses the removed Forge-era enum. */
public final class LegacySpawnPlacementStep implements ClassTransformStep {
    private static final String HELPER = "net/neoforged/forgestuck/compat/LegacySpawnPlacements";
    private static boolean supported(String name, String descriptor) {
        return name.equals("register") && descriptor.equals(SpawnPlacementMappings.FULL_REGISTER)
                || Set.of("isCancelable()Z", "hasResult()Z").contains(name + descriptor);
    }
    private static String receiver(String descriptor) { return "(L" + SpawnPlacementMappings.EVENT + ";" + descriptor.substring(1); }

    @Override
    public byte[] apply(String className, byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        if (mixin(node.visibleAnnotations) || mixin(node.invisibleAnnotations)) return bytes;
        boolean changed = false;
        for (var method : node.methods) {
            for (var instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals(SpawnPlacementMappings.EVENT)
                        && call.getOpcode() == Opcodes.INVOKEVIRTUAL && supported(call.name, call.desc)) {
                    call.desc = receiver(call.desc);
                    call.owner = HELPER;
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.itf = false;
                    changed = true;
                } else if (instruction instanceof InvokeDynamicInsnNode indy) {
                    for (int i = 0; i < indy.bsmArgs.length; i++) {
                        if (indy.bsmArgs[i] instanceof Handle h) {
                            Handle mapped = handle(h);
                            if (!mapped.equals(h)) { indy.bsmArgs[i] = mapped; changed = true; }
                        }
                    }
                } else if (instruction instanceof LdcInsnNode ldc && ldc.cst instanceof Handle h) {
                    Handle mapped = handle(h);
                    if (!mapped.equals(h)) { ldc.cst = mapped; changed = true; }
                }
            }
        }
        if (!changed) return bytes;
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }
    private static Handle handle(Handle h) {
        if (h.getOwner().equals(SpawnPlacementMappings.EVENT) && h.getTag() == Opcodes.H_INVOKEVIRTUAL
                && supported(h.getName(), h.getDesc())) {
            return new Handle(Opcodes.H_INVOKESTATIC, HELPER, h.getName(), receiver(h.getDesc()), false);
        }
        return h;
    }
    private static boolean mixin(java.util.List<AnnotationNode> annotations) {
        return annotations != null && annotations.stream().anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;"));
    }
}
