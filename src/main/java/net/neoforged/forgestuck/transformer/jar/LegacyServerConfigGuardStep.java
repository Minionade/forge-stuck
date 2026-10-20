package net.neoforged.forgestuck.transformer.jar;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Guards Thermal and CoFH callbacks that may run before their server config is loaded or after it unloads. */
public final class LegacyServerConfigGuardStep implements ClassTransformStep {
    private static final String FOUNDATION_EVENTS =
            "cofh.thermal.foundation.common.event.TFndCommonSetupEvents";
    private static final String CULTIVATION_EVENTS =
            "cofh.thermal.cultivation.common.event.TCulCommonSetupEvents";
    private static final String CONFIG_MANAGER = "cofh.core.common.config.ConfigManager";
    private static final String CORE_ENCHANT_CONFIG = "cofh.core.common.config.CoreEnchantConfig";
    private static final String CONFIG_OWNER = "cofh/thermal/core/ThermalCore";
    private static final String MANAGER_OWNER = "cofh/core/common/config/ConfigManager";
    private static final String CORE_OWNER = "cofh/core/CoFHCore";
    private static final String CONFIG_SPEC = "net/neoforged/neoforge/common/ModConfigSpec";
    private static final String CONFIG_HELPER = "net/neoforged/forgestuck/compat/LegacyConfigSpec";

    @Override
    public byte[] apply(String className, byte[] bytes) {
        if (bytes == null
                || !(className.equals(FOUNDATION_EVENTS)
                        || className.equals(CULTIVATION_EVENTS)
                        || className.equals(CONFIG_MANAGER)
                        || className.equals(CORE_ENCHANT_CONFIG))) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        boolean changed = false;
        for (MethodNode method : node.methods) {
            if (isConfigUnloadHandler(className, method) && !alreadyUnloadGuarded(method)) {
                InsnList guard = new InsnList();
                guard.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 1));
                guard.add(new org.objectweb.asm.tree.TypeInsnNode(
                        Opcodes.INSTANCEOF, "net/neoforged/fml/event/config/ModConfigEvent$Unloading"));
                LabelNode notUnloading = new LabelNode();
                guard.add(new JumpInsnNode(Opcodes.IFEQ, notUnloading));
                guard.add(new org.objectweb.asm.tree.InsnNode(Opcodes.RETURN));
                guard.add(notUnloading);
                guard.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
                method.instructions.insert(guard);
                changed = true;
                continue;
            }
            if (!(isTradeCallback(method) || isServerConfigRefresh(className, method))
                    || !method.desc.endsWith(")V")
                    || (method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0
                    || alreadyGuarded(method)) {
                continue;
            }
            InsnList guard = new InsnList();
            String configOwner = className.equals(CORE_ENCHANT_CONFIG) ? CORE_OWNER : CONFIG_OWNER;
            guard.add(new FieldInsnNode(
                    Opcodes.GETSTATIC, configOwner, "CONFIG_MANAGER", "L" + MANAGER_OWNER + ";"));
            guard.add(new MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL, MANAGER_OWNER, "getServerSpec", "()L" + CONFIG_SPEC + ";", false));
            guard.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC, CONFIG_HELPER, "isLoaded", "(L" + CONFIG_SPEC + ";)Z", false));
            LabelNode configLoaded = new LabelNode();
            guard.add(new JumpInsnNode(Opcodes.IFNE, configLoaded));
            guard.add(new org.objectweb.asm.tree.InsnNode(Opcodes.RETURN));
            guard.add(configLoaded);
            guard.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
            method.instructions.insert(guard);
            changed = true;
        }
        if (!changed) return bytes;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static boolean isTradeCallback(MethodNode method) {
        return (method.name.equals("setupWandererTrades") && method.desc.contains("WandererTradesEvent"))
                || (method.name.equals("setupVillagerTrades") && method.desc.contains("VillagerTradesEvent"));
    }

    private static boolean isServerConfigRefresh(String className, MethodNode method) {
        return className.equals(CORE_ENCHANT_CONFIG)
                && method.name.equals("refresh") && method.desc.equals("()V");
    }

    private static boolean isConfigUnloadHandler(String className, MethodNode method) {
        return className.equals(CONFIG_MANAGER)
                && method.name.equals("configRefresh")
                && method.desc.equals("(Lnet/neoforged/fml/event/config/ModConfigEvent;)V");
    }

    private static boolean alreadyUnloadGuarded(MethodNode method) {
        for (var instruction : method.instructions) {
            if (instruction instanceof org.objectweb.asm.tree.TypeInsnNode type
                    && type.getOpcode() == Opcodes.INSTANCEOF
                    && type.desc.equals("net/neoforged/fml/event/config/ModConfigEvent$Unloading")) {
                return true;
            }
        }
        return false;
    }

    private static boolean alreadyGuarded(MethodNode method) {
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.getOpcode() == Opcodes.INVOKESTATIC
                    && call.owner.equals(CONFIG_HELPER)
                    && call.name.equals("isLoaded")
                    && call.desc.equals("(L" + CONFIG_SPEC + ";)Z")) {
                return true;
            }
        }
        return false;
    }
}
