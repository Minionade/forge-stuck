package net.neoforged.forgestuck.transformer.jar;

import java.util.LinkedHashSet;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/**
 * Guards only literal one-argument Class.forName dev-test lookups in legacy RegisterGameTestsEvent
 * subscribers. Dynamic lookups and unrelated registration failures intentionally retain their behavior.
 */
public final class LegacyGameTestGuardStep implements ClassTransformStep {
    private static final Set<String> EVENTS = Set.of(
            "(Lnet/neoforged/neoforge/event/RegisterGameTestsEvent;)V",
            "(Lnet/minecraftforge/event/RegisterGameTestsEvent;)V");
    private static final Set<String> SUBSCRIBE = Set.of(
            "Lnet/neoforged/bus/api/SubscribeEvent;", "Lnet/minecraftforge/eventbus/api/SubscribeEvent;");

    @Override
    public byte[] apply(String className, byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        boolean modified = false;
        for (MethodNode method : node.methods) {
            if (!EVENTS.contains(method.desc) || method.visibleAnnotations == null
                    || method.visibleAnnotations.stream().noneMatch(a -> SUBSCRIBE.contains(a.desc))
                    || (method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) continue;
            Set<String> testClasses = new LinkedHashSet<>();
            for (AbstractInsnNode insn : method.instructions) {
                if (!(insn instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESTATIC
                        || !"java/lang/Class".equals(call.owner) || !"forName".equals(call.name)
                        || !"(Ljava/lang/String;)Ljava/lang/Class;".equals(call.desc)) continue;
                AbstractInsnNode previous = call.getPrevious();
                while (previous != null && previous.getOpcode() == -1) previous = previous.getPrevious();
                // A literal gametest package is deliberate evidence, not an arbitrary missing mod dependency.
                if (previous instanceof LdcInsnNode ldc && ldc.cst instanceof String name
                        && name.contains(".gametest.")) testClasses.add(name);
            }
            if (testClasses.isEmpty()) continue;
            InsnList guard = new InsnList();
            for (String testClass : testClasses) {
                LabelNode proceed = new LabelNode();
                guard.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/neoforged/fml/loading/FMLEnvironment",
                        "production", "Z"));
                guard.add(new LdcInsnNode(Type.getObjectType(node.name)));
                guard.add(new LdcInsnNode(testClass));
                guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                        "net/neoforged/forgestuck/compat/LegacyGameTestSupport", "shouldSkip",
                        "(ZLjava/lang/Class;Ljava/lang/String;)Z", false));
                guard.add(new JumpInsnNode(Opcodes.IFEQ, proceed));
                guard.add(new InsnNode(Opcodes.RETURN));
                guard.add(proceed);
                guard.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
            }
            method.instructions.insert(guard);
            modified = true;
        }
        if (!modified) return bytes;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }
}
