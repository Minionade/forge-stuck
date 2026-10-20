package net.neoforged.forgestuck.transformer.jar;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Guards one-shot Forge setup handlers against duplicate event-bus registration. */
public final class LegacyLifecycleGuardStep implements ClassTransformStep {
    private static final String[] SETUP_EVENTS = {
        "Lnet/minecraftforge/fml/event/lifecycle/FMLCommonSetupEvent;",
        "Lnet/neoforged/fml/event/lifecycle/FMLCommonSetupEvent;",
        "Lnet/minecraftforge/fml/event/lifecycle/FMLClientSetupEvent;",
        "Lnet/neoforged/fml/event/lifecycle/FMLClientSetupEvent;",
        "Lnet/minecraftforge/fml/event/lifecycle/FMLDedicatedServerSetupEvent;",
        "Lnet/neoforged/fml/event/lifecycle/FMLDedicatedServerSetupEvent;"
    };
    private static final String GUARD =
            "net/neoforged/forgestuck/compat/LegacyLifecycleGuards";
    private static final String GUARD_METHOD = "firstInvocation";
    private static final String GUARD_DESC = "(Ljava/lang/Object;Ljava/lang/String;)Z";

    @Override
    public byte[] apply(String className, byte[] bytes) {
        if (bytes == null || className.startsWith("net.neoforged.forgestuck")) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        boolean changed = false;
        for (MethodNode method : node.methods) {
            if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0
                    || !method.desc.startsWith("(")
                    || !isSetupCallback(method.desc)
                    || Type.getReturnType(method.desc).getSort() != Type.VOID) {
                continue;
            }
            if (alreadyGuarded(method)) continue;
            boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
            InsnList guard = new InsnList();
            if (isStatic) {
                guard.add(new LdcInsnNode(Type.getObjectType(node.name)));
            } else {
                guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
            }
            guard.add(new LdcInsnNode(method.name + method.desc));
            guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, GUARD, GUARD_METHOD, GUARD_DESC, false));
            LabelNode continueCallback = new LabelNode();
            guard.add(new JumpInsnNode(Opcodes.IFNE, continueCallback));
            guard.add(new org.objectweb.asm.tree.InsnNode(Opcodes.RETURN));
            guard.add(continueCallback);
            guard.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
            method.instructions.insert(guard);
            changed = true;
        }
        if (!changed) return bytes;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static boolean isSetupCallback(String descriptor) {
        for (String event : SETUP_EVENTS) {
            if (descriptor.contains(event)) return true;
        }
        return false;
    }

    private static boolean alreadyGuarded(MethodNode method) {
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC
                    && call.owner.equals(GUARD) && call.name.equals(GUARD_METHOD) && call.desc.equals(GUARD_DESC)) {
                return true;
            }
        }
        return false;
    }
}
