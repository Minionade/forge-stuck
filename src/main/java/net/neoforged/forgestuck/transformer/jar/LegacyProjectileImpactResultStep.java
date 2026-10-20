package net.neoforged.forgestuck.transformer.jar;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Maps Forge's used SKIP_ENTITY result to NeoForge's cancellable projectile-impact event. */
public final class LegacyProjectileImpactResultStep implements ClassTransformStep {
    private static final String EVENT = "net/minecraftforge/event/entity/ProjectileImpactEvent";
    private static final String RESULT = EVENT + "$ImpactResult";
    private static final String RESULT_DESC = "L" + RESULT + ";";

    @Override
    public byte[] apply(String className, byte[] bytes) {
        if (bytes == null) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        boolean modified = false;
        for (MethodNode method : node.methods) {
            for (var instruction : method.instructions.toArray()) {
                if (instruction instanceof FieldInsnNode field
                        && field.getOpcode() == Opcodes.GETSTATIC
                        && field.owner.equals(RESULT)
                        && field.name.equals("SKIP_ENTITY")
                        && field.desc.equals(RESULT_DESC)) {
                    method.instructions.set(field, new InsnNode(Opcodes.ICONST_1));
                    modified = true;
                } else if (instruction instanceof MethodInsnNode call
                        && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && call.owner.equals(EVENT)
                        && call.name.equals("setImpactResult")
                        && call.desc.equals("(" + RESULT_DESC + ")V")) {
                    call.name = "setCanceled";
                    call.desc = "(Z)V";
                    modified = true;
                }
            }
        }
        if (!modified) return bytes;
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }
}
