package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

class SrgMemberRemapStepTest {
    @Test
    void redirectsRemovedItemStackTagAccessorToComponentBackedShim() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyStackUser", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "read",
                "(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/nbt/CompoundTag;", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/item/ItemStack", "m_41783_",
                "()Lnet/minecraft/nbt/CompoundTag;", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 2);
        method.visitEnd();
        writer.visitEnd();

        byte[] transformed = new SrgMemberRemapStep(new LegacyNameMappings(Map.of(), Set.of()))
                .apply("sample.LegacyStackUser", writer.toByteArray());
        ClassNode node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);
        var call = java.util.Arrays.stream(node.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyItemStackShim", call.owner);
        assertEquals("getTag", call.name);
        assertEquals("(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/nbt/CompoundTag;", call.desc);
    }

    @Test
    void redirectsLegacyItemStackTagMethodReferencesToTheShim() {
        String stack = "net/minecraft/world/item/ItemStack";
        String tag = "net/minecraft/nbt/CompoundTag";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyStackReference", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "factory",
                "()Ljava/util/function/Function;", null, null);
        method.visitCode();
        method.visitInvokeDynamicInsn("apply", "()Ljava/util/function/Function;",
                new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                                + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;"
                                + "Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false),
                Type.getMethodType("(Ljava/lang/Object;)Ljava/lang/Object;"),
                new Handle(Opcodes.H_INVOKEVIRTUAL, stack, "m_41783_", "()L" + tag + ";", false),
                Type.getMethodType("(L" + stack + ";)L" + tag + ";"));
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode node = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyStackReference", writer.toByteArray()))
                .accept(node, 0);
        var indy = java.util.Arrays.stream(node.methods.get(0).instructions.toArray())
                .filter(org.objectweb.asm.tree.InvokeDynamicInsnNode.class::isInstance)
                .map(org.objectweb.asm.tree.InvokeDynamicInsnNode.class::cast).findFirst().orElseThrow();
        Handle implementation = (Handle) indy.bsmArgs[1];
        assertEquals(Opcodes.H_INVOKESTATIC, implementation.getTag());
        assertEquals("net/neoforged/forgestuck/compat/LegacyItemStackShim", implementation.getOwner());
        assertEquals("getTag", implementation.getName());
        assertEquals("(L" + stack + ";)L" + tag + ";", implementation.getDesc());
    }

    @Test
    void bridgesIndirectLegacyFluidStateDefinitionOverrides() {
        String desc = "(Lnet/minecraft/world/level/block/state/StateDefinition$Builder;)V";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyFlowingFluid", null,
                "sample/LegacyFluid", null);
        var method = writer.visitMethod(Opcodes.ACC_PROTECTED, "m_7180_", desc, null, null);
        method.visitCode();
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 2);
        method.visitEnd();
        writer.visitEnd();

        ClassNode node = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyFlowingFluid", writer.toByteArray()))
                .accept(node, 0);
        var bridge = node.methods.stream().filter(candidate -> candidate.name.equals("createFluidStateDefinition"))
                .findFirst().orElseThrow();
        assertEquals(desc, bridge.desc);
        assertTrue((bridge.access & Opcodes.ACC_SYNTHETIC) != 0);
    }

    @Test
    void adaptsLegacyCriterionTriggerRegistrationToTheKeyedApi() {
        String owner = "net/minecraft/advancements/CriteriaTriggers";
        String desc = "(Lnet/minecraft/advancements/CriterionTrigger;)Lnet/minecraft/advancements/CriterionTrigger;";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyTriggerRegistration", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "register", desc, null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, owner, "m_10595_", desc, false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();

        ClassNode node = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyTriggerRegistration", writer.toByteArray()))
                .accept(node, 0);
        MethodInsnNode call = java.util.Arrays.stream(node.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyCriteriaTriggers", call.owner);
        assertEquals("register", call.name);
        assertEquals(desc, call.desc);
    }

    @Test
    void removesTheLegacyEmptyBlockLootFieldAssignment() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyBlock", null,
                "net/minecraft/world/level/block/Block", null);
        var init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                "(Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitVarInsn(Opcodes.ALOAD, 1);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/world/level/block/Block", "<init>",
                "(Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)V", false);
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/world/level/storage/loot/BuiltInLootTables",
                "f_78712_", "Lnet/minecraft/resources/ResourceLocation;");
        init.visitFieldInsn(Opcodes.PUTFIELD, "net/minecraft/world/level/block/Block", "f_60440_",
                "Lnet/minecraft/resources/ResourceLocation;");
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(2, 2);
        init.visitEnd();
        writer.visitEnd();

        ClassNode node = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyBlock", writer.toByteArray())).accept(node, 0);
        assertTrue(java.util.Arrays.stream(node.methods.get(0).instructions.toArray())
                .noneMatch(org.objectweb.asm.tree.FieldInsnNode.class::isInstance));
    }

    @Test
    void remapsInheritedGameMembersReferencedThroughLegacySubclasses() {
        String owner = "sample/LegacyBlock";
        String stateDefinition = "Lnet/minecraft/world/level/block/state/StateDefinition;";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, owner, null,
                "net/minecraft/world/level/block/Block", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "access", "()" + stateDefinition, null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitFieldInsn(Opcodes.GETFIELD, owner, "f_49792_", stateDefinition);
        method.visitInsn(Opcodes.POP);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, "m_76144_", "()" + stateDefinition, false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();

        byte[] transformed = new SrgMemberRemapStep(new LegacyNameMappings(
                Map.of("f_49792_", "stateDefinition", "m_76144_", "getStateDefinition"), Set.of()))
                .apply("sample.LegacyBlock", writer.toByteArray());
        ClassNode node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);

        var instructions = node.methods.get(0).instructions.toArray();
        var field = java.util.Arrays.stream(instructions).filter(FieldInsnNode.class::isInstance)
                .map(FieldInsnNode.class::cast).findFirst().orElseThrow();
        var call = java.util.Arrays.stream(instructions).filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        assertEquals("stateDefinition", field.name);
        assertEquals("getStateDefinition", call.name);
    }
}
