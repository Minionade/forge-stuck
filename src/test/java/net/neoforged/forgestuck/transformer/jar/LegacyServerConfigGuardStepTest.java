package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

class LegacyServerConfigGuardStepTest {
    private static final String FOUNDATION_TARGET =
            "cofh.thermal.foundation.common.event.TFndCommonSetupEvents";
    private static final String FOUNDATION_TARGET_INTERNAL =
            "cofh/thermal/foundation/common/event/TFndCommonSetupEvents";
    private static final String CULTIVATION_TARGET =
            "cofh.thermal.cultivation.common.event.TCulCommonSetupEvents";
    private static final String CULTIVATION_TARGET_INTERNAL =
            "cofh/thermal/cultivation/common/event/TCulCommonSetupEvents";
    private static final String CORE_ENCHANT_TARGET =
            "cofh.core.common.config.CoreEnchantConfig";
    private static final String CORE_ENCHANT_TARGET_INTERNAL =
            "cofh/core/common/config/CoreEnchantConfig";
    private static final String CONFIG_MANAGER_TARGET =
            "cofh.core.common.config.ConfigManager";
    private static final String CONFIG_MANAGER_TARGET_INTERNAL =
            "cofh/core/common/config/ConfigManager";

    private static byte[] thermalEventsClass(String internalName, String methodName, String eventName) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, methodName,
                "(L" + eventName + ";)V", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 1);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] coreEnchantConfigClass(String internalName) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "refresh", "()V", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 1);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] configManagerClass() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, CONFIG_MANAGER_TARGET_INTERNAL, null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "configRefresh",
                "(Lnet/neoforged/fml/event/config/ModConfigEvent;)V", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 2);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    @Test
    void guardsThermalWandererAndVillagerTradePreviewsUntilServerConfigIsLoaded() {
        var step = new LegacyServerConfigGuardStep();
        assertGuarded(step, FOUNDATION_TARGET, FOUNDATION_TARGET_INTERNAL,
                "setupWandererTrades", "net/neoforged/neoforge/event/village/WandererTradesEvent");
        assertGuarded(step, CULTIVATION_TARGET, CULTIVATION_TARGET_INTERNAL,
                "setupVillagerTrades", "net/neoforged/neoforge/event/village/VillagerTradesEvent");
    }

    @Test
    void guardsCoFHEnchantRefreshUntilServerConfigIsLoaded() {
        assertServerConfigRefreshGuarded(CORE_ENCHANT_TARGET, CORE_ENCHANT_TARGET_INTERNAL, "cofh/core/CoFHCore");
    }

    @Test
    void skipsCoFHConfigRefreshOnConfigUnload() {
        var step = new LegacyServerConfigGuardStep();
        byte[] input = configManagerClass();
        byte[] transformed = step.apply(CONFIG_MANAGER_TARGET, input);
        ClassNode node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);
        var method = node.methods.stream().filter(m -> m.name.equals("configRefresh")).findFirst().orElseThrow();
        assertTrue(java.util.Arrays.stream(method.instructions.toArray())
                .filter(org.objectweb.asm.tree.TypeInsnNode.class::isInstance)
                .map(org.objectweb.asm.tree.TypeInsnNode.class::cast)
                .anyMatch(type -> type.getOpcode() == Opcodes.INSTANCEOF
                        && type.desc.equals("net/neoforged/fml/event/config/ModConfigEvent$Unloading")));
        assertTrue(java.util.Arrays.stream(method.instructions.toArray()).anyMatch(JumpInsnNode.class::isInstance));
        assertTrue(java.util.Arrays.stream(method.instructions.toArray())
                .anyMatch(org.objectweb.asm.tree.FrameNode.class::isInstance));
        assertSame(transformed, step.apply(CONFIG_MANAGER_TARGET, transformed));
    }

    private static void assertServerConfigRefreshGuarded(String target, String internalName, String configOwner) {
        var step = new LegacyServerConfigGuardStep();
        byte[] input = coreEnchantConfigClass(internalName);
        byte[] transformed = step.apply(target, input);
        ClassNode node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);
        var method = node.methods.stream().filter(m -> m.name.equals("refresh")).findFirst().orElseThrow();
        assertTrue(java.util.Arrays.stream(method.instructions.toArray())
                .filter(FieldInsnNode.class::isInstance).map(FieldInsnNode.class::cast)
                .anyMatch(field -> field.owner.equals(configOwner)
                        && field.name.equals("CONFIG_MANAGER")));
        var guard = java.util.Arrays.stream(method.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .filter(call -> call.owner.equals("net/neoforged/forgestuck/compat/LegacyConfigSpec"))
                .findFirst().orElseThrow();
        assertEquals("isLoaded", guard.name);
        assertTrue(java.util.Arrays.stream(method.instructions.toArray()).anyMatch(JumpInsnNode.class::isInstance));
        assertSame(transformed, step.apply(target, transformed));
    }

    private static void assertGuarded(
            LegacyServerConfigGuardStep step, String target, String internalName, String methodName, String eventName) {
        byte[] input = thermalEventsClass(internalName, methodName, eventName);
        byte[] transformed = step.apply(target, input);
        ClassNode node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);
        var method = node.methods.stream().filter(m -> m.name.equals(methodName)).findFirst().orElseThrow();
        assertTrue(java.util.Arrays.stream(method.instructions.toArray())
                .anyMatch(FieldInsnNode.class::isInstance));
        var guard = java.util.Arrays.stream(method.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .filter(call -> call.owner.equals("net/neoforged/forgestuck/compat/LegacyConfigSpec"))
                .findFirst().orElseThrow();
        assertEquals("isLoaded", guard.name);
        assertEquals("(Lnet/neoforged/neoforge/common/ModConfigSpec;)Z", guard.desc);
        assertTrue(java.util.Arrays.stream(method.instructions.toArray()).anyMatch(JumpInsnNode.class::isInstance));
        assertSame(transformed, step.apply(target, transformed));
    }

    @Test
    void leavesOtherClassesAndMethodsUntouched() {
        byte[] input = thermalEventsClass(
                FOUNDATION_TARGET_INTERNAL, "setupWandererTrades",
                "net/neoforged/neoforge/event/village/WandererTradesEvent");
        assertSame(input, new LegacyServerConfigGuardStep().apply("other/Events", input));
        byte[] otherMethod = thermalEventsClass(
                FOUNDATION_TARGET_INTERNAL, "other", "net/neoforged/neoforge/event/village/WandererTradesEvent");
        assertSame(otherMethod, new LegacyServerConfigGuardStep().apply(FOUNDATION_TARGET, otherMethod));
    }
}
