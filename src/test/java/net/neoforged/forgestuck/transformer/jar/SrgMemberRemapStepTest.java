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
import org.objectweb.asm.tree.MethodNode;

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
    void bridgesBlockStateOverridesInheritedThroughModSuperclass() {
        String desc = "(Lnet/minecraft/world/level/block/state/StateDefinition$Builder;)V";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyPane", null,
                "sample/LegacyPaneBase", null);
        var method = writer.visitMethod(Opcodes.ACC_PROTECTED, "m_7926_", desc, null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "sample/LegacyPaneBase", "m_7926_", desc, false);
        method.visitInsn(Opcodes.RETURN);
        method.visitEnd();
        writer.visitEnd();

        ClassNode node = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyPane", writer.toByteArray()))
                .accept(node, 0);
        MethodNode bridge = node.methods.stream()
                .filter(candidate -> candidate.name.equals("createBlockStateDefinition"))
                .findFirst().orElseThrow();
        MethodInsnNode bridgeCall = java.util.Arrays.stream(bridge.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKEVIRTUAL, bridgeCall.getOpcode());
        assertEquals("sample/LegacyPane", bridgeCall.owner);
        assertEquals("m_7926_", bridgeCall.name);
    }

    @Test
    void remapsLegacyFluidStateDefinitionSuperCallsOnModClasses() {
        String desc = "(Lnet/minecraft/world/level/block/state/StateDefinition$Builder;)V";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyFlowingFluid", null,
                "sample/LegacyFluid", null);
        var method = writer.visitMethod(Opcodes.ACC_PROTECTED, "m_7180_", desc, null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "sample/LegacyFluid", "m_7180_", desc, false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(2, 2);
        method.visitEnd();
        writer.visitEnd();

        ClassNode node = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyFlowingFluid", writer.toByteArray()))
                .accept(node, 0);
        MethodNode bridge = node.methods.stream()
                .filter(candidate -> candidate.name.equals("createFluidStateDefinition"))
                .findFirst().orElseThrow();
        MethodInsnNode bridgeCall = java.util.Arrays.stream(bridge.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKEVIRTUAL, bridgeCall.getOpcode());
        assertEquals("sample/LegacyFlowingFluid", bridgeCall.owner);
        assertEquals("m_7180_", bridgeCall.name);

        MethodInsnNode call = java.util.Arrays.stream(node.methods.stream()
                        .filter(candidate -> candidate.name.equals("m_7180_"))
                        .findFirst().orElseThrow().instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESPECIAL, call.getOpcode());
        assertEquals("createFluidStateDefinition", call.name);
    }

    @Test
    void remapsLegacyBlockStateDefinitionSuperCallsOnModClasses() {
        String desc = "(Lnet/minecraft/world/level/block/state/StateDefinition$Builder;)V";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyGlassPane", null,
                "sample/LegacyPaneBase", null);
        var method = writer.visitMethod(Opcodes.ACC_PROTECTED, "createBlockStateDefinition", desc, null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "sample/LegacyPaneBase", "m_7926_", desc, false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(2, 2);
        method.visitEnd();
        writer.visitEnd();

        ClassNode node = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyGlassPane", writer.toByteArray()))
                .accept(node, 0);
        MethodInsnNode call = java.util.Arrays.stream(node.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESPECIAL, call.getOpcode());
        assertEquals("createBlockStateDefinition", call.name);
    }

    @Test
    void adaptsLegacyDamageSourceCreativePlayerCall() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyDamageCheck", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "m_1234_check",
                "(Lnet/minecraft/world/damagesource/DamageSource;)Z", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/damagesource/DamageSource",
                "m_269014_", "()Z", false);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();

        ClassNode node = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyDamageCheck", writer.toByteArray()))
                .accept(node, 0);
        MethodInsnNode call = java.util.Arrays.stream(node.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyDamageSource", call.owner);
        assertEquals("isCreativePlayer", call.name);
        assertEquals("(Lnet/minecraft/world/damagesource/DamageSource;)Z", call.desc);
    }

    @Test
    void adaptsLegacyDamageSourceCreativePlayerMethodHandle() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/DamagePredicateHandle", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "predicate",
                "()Ljava/util/function/Predicate;", null, null);
        method.visitCode();
        method.visitLdcInsn(new org.objectweb.asm.Handle(Opcodes.H_INVOKEVIRTUAL,
                "net/minecraft/world/damagesource/DamageSource", "m_269014_", "()Z", false));
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode node = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.DamagePredicateHandle", writer.toByteArray()))
                .accept(node, 0);
        org.objectweb.asm.tree.LdcInsnNode constant = java.util.Arrays.stream(node.methods.get(0).instructions.toArray())
                .filter(org.objectweb.asm.tree.LdcInsnNode.class::isInstance)
                .map(org.objectweb.asm.tree.LdcInsnNode.class::cast).findFirst().orElseThrow();
        org.objectweb.asm.Handle handle = (org.objectweb.asm.Handle) constant.cst;
        assertEquals(Opcodes.H_INVOKESTATIC, handle.getTag());
        assertEquals("net/neoforged/forgestuck/compat/LegacyDamageSource", handle.getOwner());
        assertEquals("isCreativePlayer", handle.getName());
        assertEquals("(Lnet/minecraft/world/damagesource/DamageSource;)Z", handle.getDesc());
    }

    @Test
    void adaptsLegacyDamageSourceCreativePlayerLambdaHandle() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/DamagePredicateLambda", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "predicate",
                "()Ljava/util/function/Predicate;", null, null);
        method.visitCode();
        org.objectweb.asm.Handle bootstrap = new org.objectweb.asm.Handle(Opcodes.H_INVOKESTATIC,
                "java/lang/invoke/LambdaMetafactory", "metafactory",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                        + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;"
                        + "Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);
        org.objectweb.asm.Handle legacy = new org.objectweb.asm.Handle(Opcodes.H_INVOKEVIRTUAL,
                "net/minecraft/world/damagesource/DamageSource", "m_269014_", "()Z", false);
        method.visitInvokeDynamicInsn("test", "()Ljava/util/function/Predicate;", bootstrap,
                org.objectweb.asm.Type.getMethodType("(Ljava/lang/Object;)Z"), legacy,
                org.objectweb.asm.Type.getMethodType("(Lnet/minecraft/world/damagesource/DamageSource;)Z"));
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode node = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.DamagePredicateLambda", writer.toByteArray()))
                .accept(node, 0);
        org.objectweb.asm.tree.InvokeDynamicInsnNode indy = java.util.Arrays.stream(
                        node.methods.get(0).instructions.toArray())
                .filter(org.objectweb.asm.tree.InvokeDynamicInsnNode.class::isInstance)
                .map(org.objectweb.asm.tree.InvokeDynamicInsnNode.class::cast).findFirst().orElseThrow();
        org.objectweb.asm.Handle handle = (org.objectweb.asm.Handle) indy.bsmArgs[1];
        assertEquals(Opcodes.H_INVOKESTATIC, handle.getTag());
        assertEquals("net/neoforged/forgestuck/compat/LegacyDamageSource", handle.getOwner());
        assertEquals("isCreativePlayer", handle.getName());
        assertEquals("(Lnet/minecraft/world/damagesource/DamageSource;)Z", handle.getDesc());
    }

    @Test
    void redirectsLegacyMobEffectInstanceConstructorToHolderAdapter() {
        String effect = "net/minecraft/world/effect/MobEffect";
        String instance = "net/minecraft/world/effect/MobEffectInstance";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyEffectUser", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "m_1234_create",
                "(L" + effect + ";)L" + instance + ";", null, null);
        method.visitCode();
        method.visitTypeInsn(Opcodes.NEW, instance);
        method.visitInsn(Opcodes.DUP);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.ICONST_0);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, instance, "<init>",
                "(L" + effect + ";II)V", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(4, 1);
        method.visitEnd();
        writer.visitEnd();

        ClassNode node = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyEffectUser", writer.toByteArray()))
                .accept(node, 0);
        var instructions = node.methods.get(0).instructions.toArray();
        assertTrue(java.util.Arrays.stream(instructions)
                .anyMatch(instruction -> instruction instanceof org.objectweb.asm.tree.TypeInsnNode type
                        && type.getOpcode() == Opcodes.NEW
                        && type.desc.equals("net/neoforged/forgestuck/compat/LegacyMobEffectInstance")));
        MethodInsnNode call = java.util.Arrays.stream(instructions).filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESPECIAL, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyMobEffectInstance", call.owner);
        assertEquals("<init>", call.name);
        assertEquals("(L" + effect + ";II)V", call.desc);
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
        init.visitFieldInsn(Opcodes.PUTFIELD, "sample/LegacyBlock", "f_60440_",
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
