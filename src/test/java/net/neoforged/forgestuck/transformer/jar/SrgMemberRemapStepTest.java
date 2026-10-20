package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

class SrgMemberRemapStepTest {
    @Test
    void remapsLegacyMatrixVertexCallToAddVertex() {
        String vertexConsumer = "com/mojang/blaze3d/vertex/VertexConsumer";
        String matrix4f = "org/joml/Matrix4f";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyVertexCall", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "vertex",
                "(L" + vertexConsumer + ";L" + matrix4f + ";FFF)L" + vertexConsumer + ";",
                null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitVarInsn(Opcodes.FLOAD, 3);
        method.visitVarInsn(Opcodes.FLOAD, 4);
        method.visitVarInsn(Opcodes.FLOAD, 5);
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, vertexConsumer, "m_252986_",
                "(L" + matrix4f + ";FFF)L" + vertexConsumer + ";", true);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 6);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyVertexCall", writer.toByteArray())).accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals("addVertex", call.name);
        assertEquals("(L" + matrix4f + ";FFF)L" + vertexConsumer + ";", call.desc);
    }

    @Test
    void remapsLegacyPotionEffectLookupToCompatibilityShim() {
        String potionUtils = "net/minecraft/world/item/alchemy/PotionUtils";
        String compoundTag = "net/minecraft/nbt/CompoundTag";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyPotionEffects", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "effects",
                "(L" + compoundTag + ";)Ljava/util/List;", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, potionUtils, "m_43566_",
                "(L" + compoundTag + ";)Ljava/util/List;", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 1);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyPotionEffects", writer.toByteArray())).accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyPotion", call.owner);
        assertEquals("getAllEffects", call.name);
        assertEquals("(L" + compoundTag + ";)Ljava/util/List;", call.desc);
    }

    @Test
    void remapsLegacyPotionItemColorLookupToCompatibilityShim() {
        String itemStack = "net/minecraft/world/item/ItemStack";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyPotionColor", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "color",
                "(L" + itemStack + ";)I", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESTATIC,
                "net/minecraft/world/item/alchemy/PotionUtils", "m_43575_",
                "(L" + itemStack + ";)I", false);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 1);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyPotionColor", writer.toByteArray())).accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyPotion", call.owner);
        assertEquals("getColor", call.name);
        assertEquals("(L" + itemStack + ";)I", call.desc);
    }

    @Test
    void remapsLegacyCustomPotionEffectLookupToCompatibilityShim() {
        String potionUtils = "net/minecraft/world/item/alchemy/PotionUtils";
        String compoundTag = "net/minecraft/nbt/CompoundTag";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyCustomPotionEffects", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "effects",
                "(L" + compoundTag + ";)Ljava/util/List;", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, potionUtils, "m_43573_",
                "(L" + compoundTag + ";)Ljava/util/List;", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 1);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyCustomPotionEffects", writer.toByteArray())).accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyPotion", call.owner);
        assertEquals("getCustomEffects", call.name);
        assertEquals("(L" + compoundTag + ";)Ljava/util/List;", call.desc);
    }

    @Test
    void adaptsLegacyRecipeContainerMatchCalls() {
        String recipe = "slimeknights/tconstruct/library/recipe/melting/IMeltingRecipe";
        String container = "net/minecraft/world/Container";
        String level = "net/minecraft/world/level/Level";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyRecipeMatch", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "matches",
                "(L" + recipe + ";L" + container + ";L" + level + ";)Z", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, recipe, "m_5818_",
                "(L" + container + ";L" + level + ";)Z", true);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 3);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyRecipeMatch", writer.toByteArray())).accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyRecipeManager", call.owner);
        assertEquals("matchesLegacy", call.name);
        assertEquals("(Lnet/minecraft/world/item/crafting/Recipe;L" + container + ";L" + level + ";)Z",
                call.desc);
    }

    @Test
    void restoresParticleOptionsParameterForLevelAccessorCalls() {
        String levelAccessor = "net/minecraft/world/level/LevelAccessor";
        String legacyParticle = "net/neoforged/forgestuck/gamecompat/LegacyParticleOptions";
        String particleOptions = "net/minecraft/core/particles/ParticleOptions";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyParticleCall_m_12345_", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "spawn",
                "(L" + levelAccessor + ";L" + legacyParticle + ";)V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitInsn(Opcodes.DCONST_0);
        method.visitInsn(Opcodes.DCONST_0);
        method.visitInsn(Opcodes.DCONST_0);
        method.visitInsn(Opcodes.DCONST_0);
        method.visitInsn(Opcodes.DCONST_0);
        method.visitInsn(Opcodes.DCONST_0);
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, levelAccessor, "m_7106_",
                "(L" + legacyParticle + ";DDDDDD)V", true);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 2);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyParticleCall_m_12345_", writer.toByteArray())).accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(levelAccessor, call.owner);
        assertEquals("addParticle", call.name);
        assertEquals("(L" + particleOptions + ";DDDDDD)V", call.desc);
    }

    @Test
    void redirectsLegacyLiquidBlockFluidAccessorToCompatibilityShim() {
        String liquidBlock = "net/minecraft/world/level/block/LiquidBlock";
        String flowingFluid = "net/minecraft/world/level/material/FlowingFluid";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyLiquidBlock_m_12345_", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "getFluid",
                "(L" + liquidBlock + ";)L" + flowingFluid + ";", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, liquidBlock, "getFluid",
                "()L" + flowingFluid + ";", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 1);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyLiquidBlock_m_12345_", writer.toByteArray())).accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyLiquidBlock", call.owner);
        assertEquals("getFluid", call.name);
        assertEquals("(L" + liquidBlock + ";)L" + flowingFluid + ";", call.desc);
    }

    @Test
    void rewritesLegacyPotionNameCallToRegistryAwareShim() {
        String potion = "net/minecraft/world/item/alchemy/Potion";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyPotionName", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "name",
                "(L" + potion + ";Ljava/lang/String;)Ljava/lang/String;", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, potion, "m_43492_",
                "(Ljava/lang/String;)Ljava/lang/String;", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 2);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyPotionName", writer.toByteArray())).accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyPotion", call.owner);
        assertEquals("getName", call.name);
        assertEquals("(L" + potion + ";Ljava/lang/String;)Ljava/lang/String;", call.desc);
    }

    @Test
    void remapsLegacyItemStackSameTagsComparisonToComponents() {
        String itemStack = "net/minecraft/world/item/ItemStack";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyStackComparison", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "same",
                "(L" + itemStack + ";L" + itemStack + ";)Z", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, itemStack, "m_150942_",
                "(L" + itemStack + ";L" + itemStack + ";)Z", false);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 2);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyStackComparison", writer.toByteArray())).accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals(itemStack, call.owner);
        assertEquals("isSameItemSameComponents", call.name);
        assertEquals("(L" + itemStack + ";L" + itemStack + ";)Z", call.desc);
    }

    @Test
    void resendsTConstructStructureBeforeOpeningHeatingMenu() {
        String blockEntity = "slimeknights/tconstruct/smeltery/block/entity/controller/HeatingStructureBlockEntity";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, blockEntity, null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "m_7208_",
                "(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/entity/player/Player;)"
                        + "Lnet/minecraft/world/inventory/AbstractContainerMenu;",
                null, null);
        method.visitCode();
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 4);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(blockEntity.replace('/', '.'), writer.toByteArray()))
                .accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyTConstructStructureSync", call.owner);
        assertEquals("resendStructure", call.name);
        assertEquals("(Ljava/lang/Object;)V", call.desc);
    }

    @Test
    void bridgesLegacyBlockEntityNbtHooksToRegistryAwareHooks() {
        String blockEntity = "net/minecraft/world/level/block/entity/BlockEntity";
        String legacySuperclass = "cofh/core/common/block/entity/BlockEntityCoFH";
        String compoundTag = "net/minecraft/nbt/CompoundTag";
        String legacyDesc = "(L" + compoundTag + ";)V";
        String providerDesc = "(L" + compoundTag + ";Lnet/minecraft/core/HolderLookup$Provider;)V";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "slimeknights/mantle/block/entity/MantleBlockEntity",
                null, blockEntity, null);
        var legacySaveMethod = writer.visitMethod(Opcodes.ACC_PUBLIC, "m_183515_", legacyDesc, null, null);
        legacySaveMethod.visitCode();
        legacySaveMethod.visitVarInsn(Opcodes.ALOAD, 0);
        legacySaveMethod.visitVarInsn(Opcodes.ALOAD, 1);
        legacySaveMethod.visitMethodInsn(Opcodes.INVOKESPECIAL, legacySuperclass, "m_183515_", legacyDesc, false);
        legacySaveMethod.visitInsn(Opcodes.RETURN);
        legacySaveMethod.visitMaxs(0, 2);
        legacySaveMethod.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "slimeknights.mantle.block.entity.MantleBlockEntity", writer.toByteArray()))
                .accept(transformed, 0);

        for (String[] hook : new String[][] {
                {"m_142466_", "loadAdditional"},
                {"m_183515_", "saveAdditional"}
        }) {
            MethodNode bridge = transformed.methods.stream()
                    .filter(method -> method.name.equals(hook[1]) && method.desc.equals(providerDesc))
                    .findFirst().orElseThrow();
            MethodInsnNode legacyCall = java.util.Arrays.stream(bridge.instructions.toArray())
                    .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                    .filter(call -> call.owner.equals("slimeknights/mantle/block/entity/MantleBlockEntity"))
                    .findFirst().orElseThrow();
            assertEquals(Opcodes.INVOKESPECIAL, legacyCall.getOpcode());
            assertEquals(hook[0], legacyCall.name);
            assertEquals(legacyDesc, legacyCall.desc);
            assertTrue(java.util.Arrays.stream(bridge.instructions.toArray())
                    .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                    .anyMatch(call -> call.owner.equals(
                            "net/neoforged/forgestuck/compat/LegacyItemStackShim")
                            && call.name.equals("pushRegistryProvider")));
            assertTrue(java.util.Arrays.stream(bridge.instructions.toArray())
                    .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                    .anyMatch(call -> call.owner.equals(
                            "net/neoforged/forgestuck/compat/LegacyItemStackShim")
                            && call.name.equals("restoreRegistryProvider")));
        }
        MethodNode legacySave = transformed.methods.stream()
                .filter(method -> method.name.equals("m_183515_") && method.desc.equals(legacyDesc))
                .findFirst().orElseThrow();
        MethodInsnNode superSave = java.util.Arrays.stream(legacySave.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .filter(call -> call.owner.equals(legacySuperclass))
                .findFirst().orElseThrow();
        assertEquals("saveAdditional", superSave.name);
        assertEquals(providerDesc, superSave.desc);
        assertTrue(java.util.Arrays.stream(legacySave.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .anyMatch(call -> call.owner.equals(
                        "net/neoforged/forgestuck/compat/LegacyItemStackShim")
                        && call.name.equals("getRegistryProvider")));
        assertTrue(legacySave.maxStack >= 3);
        assertTrue(transformed.methods.stream().anyMatch(candidate ->
                candidate.name.equals("m_142466_") && candidate.desc.equals(legacyDesc)
                        && (candidate.access & Opcodes.ACC_SYNTHETIC) != 0));
    }

    @Test
    void rewritesLegacyItemStackNbtSaveAndParseCalls() {
        String itemStack = "net/minecraft/world/item/ItemStack";
        String compoundTag = "net/minecraft/nbt/CompoundTag";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyItemStackNbt", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "roundTrip",
                "(L" + itemStack + ";L" + compoundTag + ";)L" + itemStack + ";", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, itemStack, "m_41739_",
                "(L" + compoundTag + ";)L" + compoundTag + ";", false);
        method.visitInsn(Opcodes.POP);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, itemStack, "m_41712_",
                "(L" + compoundTag + ";)L" + itemStack + ";", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyItemStackNbt", writer.toByteArray()))
                .accept(transformed, 0);
        List<MethodInsnNode> calls = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        assertEquals(2, calls.size());
        assertTrue(calls.stream().allMatch(call -> call.getOpcode() == Opcodes.INVOKESTATIC
                && call.owner.equals("net/neoforged/forgestuck/compat/LegacyItemStackShim")));
        assertEquals("save", calls.get(0).name);
        assertEquals("(L" + itemStack + ";L" + compoundTag + ";)L" + compoundTag + ";", calls.get(0).desc);
        assertEquals("parse", calls.get(1).name);
        assertEquals("(L" + compoundTag + ";)L" + itemStack + ";", calls.get(1).desc);
    }

    @Test
    void rewritesTConstructJeiItemAndDyeCalls() {
        String item = "net/minecraft/world/item/Item";
        String itemStack = "net/minecraft/world/item/ItemStack";
        String dyeColor = "net/minecraft/world/item/DyeColor";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/TConstructJeiCalls", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run",
                "(L" + item + ";L" + itemStack + ";L" + dyeColor + ";)I", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, item, "m_41459_", "()I", false);
        method.visitVarInsn(Opcodes.ISTORE, 3);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/world/item/BlockItem", "m_186336_",
                "(L" + itemStack + ";)Lnet/minecraft/nbt/CompoundTag;", false);
        method.visitInsn(Opcodes.POP);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, dyeColor, "m_41068_", "()[F", false);
        method.visitInsn(Opcodes.POP);
        method.visitVarInsn(Opcodes.ILOAD, 3);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.TConstructJeiCalls", writer.toByteArray()))
                .accept(transformed, 0);
        List<MethodInsnNode> calls = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        assertEquals(3, calls.size());
        assertEquals(Opcodes.INVOKESTATIC, calls.get(0).getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyItemStackShim", calls.get(0).owner);
        assertEquals("getMaxStackSize", calls.get(0).name);
        assertEquals("(L" + item + ";)I", calls.get(0).desc);
        assertEquals(Opcodes.INVOKESTATIC, calls.get(1).getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyItemStackShim", calls.get(1).owner);
        assertEquals("getBlockEntityData", calls.get(1).name);
        assertEquals("(L" + itemStack + ";)Lnet/minecraft/nbt/CompoundTag;", calls.get(1).desc);
        assertEquals(Opcodes.INVOKESTATIC, calls.get(2).getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyDyeColorShim", calls.get(2).owner);
        assertEquals("getTextureDiffuseColors", calls.get(2).name);
        assertEquals("(L" + dyeColor + ";)[F", calls.get(2).desc);
    }

    @Test
    void rewritesLegacyEnchantmentListDeserialization() {
        String listTag = "net/minecraft/nbt/ListTag";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyEnchantmentList", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "read",
                "(L" + listTag + ";)Ljava/util/Map;", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/world/item/enchantment/EnchantmentHelper",
                "m_44882_", "(L" + listTag + ";)Ljava/util/Map;", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 1);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyEnchantmentList", writer.toByteArray()))
                .accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyEnchantmentShim", call.owner);
        assertEquals("deserializeEnchantments", call.name);
        assertEquals("(L" + listTag + ";)Ljava/util/Map;", call.desc);
    }

    @Test
    void remapsLegacyBlockPositionNbtHelpersToCompatibilityShim() {
        String blockPos = "net/minecraft/core/BlockPos";
        String compoundTag = "net/minecraft/nbt/CompoundTag";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyBlockPosNbt", null,
                "java/lang/Object", null);
        var read = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "read",
                "(L" + compoundTag + ";)L" + blockPos + ";", null, null);
        read.visitCode();
        read.visitVarInsn(Opcodes.ALOAD, 0);
        read.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/nbt/NbtUtils", "m_129239_",
                "(L" + compoundTag + ";)L" + blockPos + ";", false);
        read.visitInsn(Opcodes.ARETURN);
        read.visitMaxs(0, 1);
        read.visitEnd();
        var write = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "write",
                "(L" + blockPos + ";)L" + compoundTag + ";", null, null);
        write.visitCode();
        write.visitVarInsn(Opcodes.ALOAD, 0);
        write.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/nbt/NbtUtils", "m_129224_",
                "(L" + blockPos + ";)L" + compoundTag + ";", false);
        write.visitInsn(Opcodes.ARETURN);
        write.visitMaxs(0, 1);
        write.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyBlockPosNbt", writer.toByteArray())).accept(transformed, 0);
        List<MethodInsnNode> calls = transformed.methods.stream()
                .flatMap(method -> java.util.Arrays.stream(method.instructions.toArray()))
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .toList();
        assertEquals(2, calls.size());
        assertTrue(calls.stream().allMatch(call ->
                call.owner.equals("net/neoforged/forgestuck/compat/LegacyBlockPosNbt")));
        assertTrue(calls.stream().anyMatch(call -> call.name.equals("read")));
        assertTrue(calls.stream().anyMatch(call -> call.name.equals("write")));
    }

    @Test
    void remapsLegacyItemStackCustomNameCheck() {
        String itemStack = "net/minecraft/world/item/ItemStack";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyNameCheck", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "hasCustomName",
                "(L" + itemStack + ";)Z", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, itemStack, "m_41788_", "()Z", false);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 1);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyNameCheck", writer.toByteArray()))
                .accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyItemStackShim", call.owner);
        assertEquals("hasCustomHoverName", call.name);
        assertEquals("(L" + itemStack + ";)Z", call.desc);
    }

    @Test
    void remapsInheritedLegacySlotIndexFieldToAccessor() {
        String slotSubclass = "slimeknights/mantle/inventory/WrapperSlot";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacySlotIndex", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "getIndex",
                "(L" + slotSubclass + ";)I", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitFieldInsn(Opcodes.GETFIELD, slotSubclass, "f_40219_", "I");
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 1);
        method.visitEnd();
        var handleMethod = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "indexHandle",
                "()Ljava/lang/invoke/MethodHandle;", null, null);
        handleMethod.visitCode();
        handleMethod.visitLdcInsn(new Handle(Opcodes.H_GETFIELD, slotSubclass, "f_40219_", "I", false));
        handleMethod.visitInsn(Opcodes.ARETURN);
        handleMethod.visitMaxs(0, 0);
        handleMethod.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacySlotIndex", writer.toByteArray()))
                .accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKEVIRTUAL, call.getOpcode());
        assertEquals("net/minecraft/world/inventory/Slot", call.owner);
        assertEquals("getSlotIndex", call.name);
        assertEquals("()I", call.desc);
        assertFalse(java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .anyMatch(FieldInsnNode.class::isInstance));
        Handle handle = java.util.Arrays.stream(transformed.methods.get(1).instructions.toArray())
                .filter(org.objectweb.asm.tree.LdcInsnNode.class::isInstance)
                .map(org.objectweb.asm.tree.LdcInsnNode.class::cast)
                .map(ldc -> (Handle) ldc.cst)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.H_INVOKEVIRTUAL, handle.getTag());
        assertEquals("net/minecraft/world/inventory/Slot", handle.getOwner());
        assertEquals("getSlotIndex", handle.getName());
        assertEquals("()I", handle.getDesc());
    }

    @Test
    void remapsLegacyFieldsReferencedThroughModSubclassOwners() {
        String moduleScreen = "slimeknights/mantle/client/screen/ModuleScreen";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyModuleScreenFields", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "left",
                "(L" + moduleScreen + ";)I", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitFieldInsn(Opcodes.GETFIELD, moduleScreen, "f_97735_", "I");
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 1);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyModuleScreenFields", writer.toByteArray())).accept(transformed, 0);

        FieldInsnNode field = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(FieldInsnNode.class::isInstance).map(FieldInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(moduleScreen, field.owner);
        assertEquals("leftPos", field.name);
    }

    @Test
    void upgradesLegacyScreenBackgroundCallWithRenderArguments() {
        String guiGraphics = "net/minecraft/client/gui/GuiGraphics";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyModuleScreen", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "m_88315_",
                "(L" + guiGraphics + ";IIF)V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "sample/LegacyModuleScreen", "m_280273_",
                "(L" + guiGraphics + ";)V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 5);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyModuleScreen", writer.toByteArray())).accept(transformed, 0);

        MethodNode render = transformed.methods.stream()
                .filter(candidate -> candidate.desc.equals("(L" + guiGraphics + ";IIF)V"))
                .findFirst().orElseThrow();
        List<AbstractInsnNode> instructions = java.util.Arrays.stream(render.instructions.toArray())
                .filter(instruction -> instruction.getOpcode() >= 0).toList();
        MethodInsnNode call = instructions.stream().filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        assertEquals("renderBackground", call.name);
        assertEquals("(L" + guiGraphics + ";IIF)V", call.desc);
        int callIndex = instructions.indexOf(call);
        assertEquals(Opcodes.ILOAD, instructions.get(callIndex - 3).getOpcode());
        assertEquals(2, ((VarInsnNode) instructions.get(callIndex - 3)).var);
        assertEquals(Opcodes.ILOAD, instructions.get(callIndex - 2).getOpcode());
        assertEquals(3, ((VarInsnNode) instructions.get(callIndex - 2)).var);
        assertEquals(Opcodes.FLOAD, instructions.get(callIndex - 1).getOpcode());
        assertEquals(4, ((VarInsnNode) instructions.get(callIndex - 1)).var);
    }

    @Test
    void removesMantleMultiModuleScreenBackgroundCallToPreventDuplicateRendering() {
        String guiGraphics = "net/minecraft/client/gui/GuiGraphics";
        String multiModuleScreen = "slimeknights/mantle/client/screen/MultiModuleScreen";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, multiModuleScreen, null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "m_88315_",
                "(L" + guiGraphics + ";IIF)V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, multiModuleScreen, "m_280273_",
                "(L" + guiGraphics + ";)V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 5);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "slimeknights.mantle.client.screen.MultiModuleScreen", writer.toByteArray()))
                .accept(transformed, 0);

        MethodNode render = transformed.methods.stream()
                .filter(candidate -> candidate.desc.equals("(L" + guiGraphics + ";IIF)V"))
                .findFirst().orElseThrow();
        List<AbstractInsnNode> instructions = java.util.Arrays.stream(render.instructions.toArray())
                .filter(instruction -> instruction.getOpcode() >= 0).toList();
        assertTrue(instructions.stream().anyMatch(instruction -> instruction.getOpcode() == Opcodes.POP2));
        assertFalse(instructions.stream().anyMatch(MethodInsnNode.class::isInstance));
    }

    @Test
    void remapsLegacyRecipeIdAndPotionTagCallsAndHandles() {
        String recipe = "net/minecraft/world/item/crafting/Recipe";
        String recipeSubclass = "slimeknights/tconstruct/library/recipe/casting/ICastingRecipe";
        String recipeId = "net/minecraft/resources/ResourceLocation";
        String compoundTag = "net/minecraft/nbt/CompoundTag";
        String potion = "net/minecraft/world/item/alchemy/Potion";
        String potionUtils = "net/minecraft/world/item/alchemy/PotionUtils";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyJeiCalls", null,
                "java/lang/Object", null);
        var recipeIdMethod = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "recipeId",
                "(L" + recipeSubclass + ";)L" + recipeId + ";", null, null);
        recipeIdMethod.visitCode();
        recipeIdMethod.visitVarInsn(Opcodes.ALOAD, 0);
        recipeIdMethod.visitMethodInsn(Opcodes.INVOKEINTERFACE, recipeSubclass, "m_6423_",
                "()L" + recipeId + ";", true);
        recipeIdMethod.visitInsn(Opcodes.ARETURN);
        recipeIdMethod.visitMaxs(0, 1);
        recipeIdMethod.visitEnd();
        var recipeIdHandleMethod = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "recipeIdHandle",
                "()Ljava/lang/invoke/MethodHandle;", null, null);
        recipeIdHandleMethod.visitCode();
        recipeIdHandleMethod.visitLdcInsn(new Handle(Opcodes.H_INVOKEINTERFACE, recipeSubclass,
                "m_6423_", "()L" + recipeId + ";", true));
        recipeIdHandleMethod.visitInsn(Opcodes.ARETURN);
        recipeIdHandleMethod.visitMaxs(0, 0);
        recipeIdHandleMethod.visitEnd();
        var potionMethod = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "potion",
                "(L" + compoundTag + ";)L" + potion + ";", null, null);
        potionMethod.visitCode();
        potionMethod.visitVarInsn(Opcodes.ALOAD, 0);
        potionMethod.visitMethodInsn(Opcodes.INVOKESTATIC, potionUtils, "m_43577_",
                "(L" + compoundTag + ";)L" + potion + ";", false);
        potionMethod.visitInsn(Opcodes.ARETURN);
        potionMethod.visitMaxs(0, 1);
        potionMethod.visitEnd();
        var potionHandleMethod = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "potionHandle",
                "()Ljava/lang/invoke/MethodHandle;", null, null);
        potionHandleMethod.visitCode();
        potionHandleMethod.visitLdcInsn(new Handle(Opcodes.H_INVOKESTATIC, potionUtils, "m_43577_",
                "(L" + compoundTag + ";)L" + potion + ";", false));
        potionHandleMethod.visitInsn(Opcodes.ARETURN);
        potionHandleMethod.visitMaxs(0, 0);
        potionHandleMethod.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyJeiCalls", writer.toByteArray()))
                .accept(transformed, 0);

        MethodInsnNode recipeIdCall = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, recipeIdCall.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyRecipeManager", recipeIdCall.owner);
        assertEquals("getRecipeId", recipeIdCall.name);
        assertEquals("(L" + recipe + ";)L" + recipeId + ";", recipeIdCall.desc);
        Handle recipeHandle = java.util.Arrays.stream(transformed.methods.get(1).instructions.toArray())
                .filter(org.objectweb.asm.tree.LdcInsnNode.class::isInstance)
                .map(org.objectweb.asm.tree.LdcInsnNode.class::cast)
                .map(ldc -> (Handle) ldc.cst)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.H_INVOKESTATIC, recipeHandle.getTag());
        assertEquals("net/neoforged/forgestuck/compat/LegacyRecipeManager", recipeHandle.getOwner());
        assertEquals("getRecipeId", recipeHandle.getName());
        assertEquals("(L" + recipe + ";)L" + recipeId + ";", recipeHandle.getDesc());
        MethodInsnNode potionCall = java.util.Arrays.stream(transformed.methods.get(2).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, potionCall.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyPotion", potionCall.owner);
        assertEquals("getPotion", potionCall.name);
        assertEquals("(L" + compoundTag + ";)L" + potion + ";", potionCall.desc);
        Handle potionHandle = java.util.Arrays.stream(transformed.methods.get(3).instructions.toArray())
                .filter(org.objectweb.asm.tree.LdcInsnNode.class::isInstance)
                .map(org.objectweb.asm.tree.LdcInsnNode.class::cast)
                .map(ldc -> (Handle) ldc.cst)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.H_INVOKESTATIC, potionHandle.getTag());
        assertEquals("net/neoforged/forgestuck/compat/LegacyPotion", potionHandle.getOwner());
        assertEquals("getPotion", potionHandle.getName());
        assertEquals("(L" + compoundTag + ";)L" + potion + ";", potionHandle.getDesc());
    }

    @Test
    void remapsLegacyDefaultVertexFormatElementFieldsAndHandles() {
        String legacyOwner = "com/mojang/blaze3d/vertex/DefaultVertexFormat";
        String elementOwner = "com/mojang/blaze3d/vertex/VertexFormatElement";
        String vertexFormat = "com/mojang/blaze3d/vertex/VertexFormat";
        String descriptor = "L" + elementOwner + ";";
        List<Map.Entry<String, String>> fields = List.of(
                Map.entry("f_85804_", "POSITION"),
                Map.entry("ELEMENT_POSITION", "POSITION"),
                Map.entry("f_85805_", "COLOR"),
                Map.entry("f_85806_", "UV0"),
                Map.entry("f_85807_", "UV1"),
                Map.entry("f_85808_", "UV2"),
                Map.entry("f_85809_", "NORMAL"),
                Map.entry("f_85810_", "PADDING"),
                Map.entry("ELEMENT_PADDING", "PADDING"));
        List<Map.Entry<String, String>> mappedFields = fields.stream()
                .filter(entry -> !entry.getValue().equals("PADDING")).toList();
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyVertexFormatElements", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "elements", "()V", null, null);
        method.visitCode();
        fields.forEach(entry -> {
            method.visitFieldInsn(Opcodes.GETSTATIC, legacyOwner, entry.getKey(), descriptor);
            method.visitInsn(Opcodes.POP);
        });
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        var handleMethod = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "positionHandle",
                "()Ljava/lang/invoke/MethodHandle;", null, null);
        handleMethod.visitCode();
        handleMethod.visitLdcInsn(new Handle(Opcodes.H_GETSTATIC, legacyOwner, "f_85804_", descriptor, false));
        handleMethod.visitInsn(Opcodes.ARETURN);
        handleMethod.visitMaxs(0, 0);
        handleMethod.visitEnd();
        var constructorMethod = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "createFormat",
                "()L" + vertexFormat + ";", null, null);
        constructorMethod.visitCode();
        constructorMethod.visitTypeInsn(Opcodes.NEW, vertexFormat);
        constructorMethod.visitInsn(Opcodes.DUP);
        constructorMethod.visitMethodInsn(Opcodes.INVOKESTATIC, "com/google/common/collect/ImmutableMap", "of",
                "()Lcom/google/common/collect/ImmutableMap;", false);
        constructorMethod.visitMethodInsn(Opcodes.INVOKESPECIAL, vertexFormat, "<init>",
                "(Lcom/google/common/collect/ImmutableMap;)V", false);
        constructorMethod.visitInsn(Opcodes.ARETURN);
        constructorMethod.visitMaxs(0, 0);
        constructorMethod.visitEnd();
        var constructorHandleMethod = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "formatHandle",
                "()Ljava/lang/invoke/MethodHandle;", null, null);
        constructorHandleMethod.visitCode();
        constructorHandleMethod.visitLdcInsn(new Handle(Opcodes.H_NEWINVOKESPECIAL, vertexFormat, "<init>",
                "(Lcom/google/common/collect/ImmutableMap;)V", false));
        constructorHandleMethod.visitInsn(Opcodes.ARETURN);
        constructorHandleMethod.visitMaxs(0, 0);
        constructorHandleMethod.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyVertexFormatElements", writer.toByteArray())).accept(transformed, 0);

        List<FieldInsnNode> remappedFields = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(FieldInsnNode.class::isInstance).map(FieldInsnNode.class::cast).toList();
        assertEquals(mappedFields.size(), remappedFields.size());
        for (int i = 0; i < remappedFields.size(); i++) {
            assertEquals(elementOwner, remappedFields.get(i).owner);
            assertEquals(mappedFields.get(i).getValue(), remappedFields.get(i).name);
        }
        List<MethodInsnNode> paddingCalls = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .filter(call -> call.name.equals("paddingElementPlaceholder")).toList();
        assertEquals(2, paddingCalls.size());
        assertTrue(paddingCalls.stream().allMatch(call -> call.owner.equals(
                "net/neoforged/forgestuck/compat/LegacyVertexFormatShim")));
        Handle handle = java.util.Arrays.stream(transformed.methods.get(1).instructions.toArray())
                .filter(org.objectweb.asm.tree.LdcInsnNode.class::isInstance)
                .map(org.objectweb.asm.tree.LdcInsnNode.class::cast)
                .map(ldc -> (Handle) ldc.cst)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.H_GETSTATIC, handle.getTag());
        assertEquals(elementOwner, handle.getOwner());
        assertEquals("POSITION", handle.getName());
        assertEquals(descriptor, handle.getDesc());

        MethodInsnNode constructor = java.util.Arrays.stream(transformed.methods.get(2).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .filter(call -> call.name.equals("create")).findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, constructor.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyVertexFormatShim", constructor.owner);
        assertEquals("(Lcom/google/common/collect/ImmutableMap;)L" + vertexFormat + ";", constructor.desc);
        assertTrue(java.util.Arrays.stream(transformed.methods.get(2).instructions.toArray())
                .noneMatch(instruction -> instruction instanceof TypeInsnNode type
                        && type.getOpcode() == Opcodes.NEW && type.desc.equals(vertexFormat)));
        Handle constructorHandle = java.util.Arrays.stream(transformed.methods.get(3).instructions.toArray())
                .filter(org.objectweb.asm.tree.LdcInsnNode.class::isInstance)
                .map(org.objectweb.asm.tree.LdcInsnNode.class::cast)
                .map(ldc -> (Handle) ldc.cst)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.H_INVOKESTATIC, constructorHandle.getTag());
        assertEquals("net/neoforged/forgestuck/compat/LegacyVertexFormatShim", constructorHandle.getOwner());
        assertEquals("create", constructorHandle.getName());
        assertEquals("(Lcom/google/common/collect/ImmutableMap;)L" + vertexFormat + ";",
                constructorHandle.getDesc());
    }

    @Test
    void rebuildsLegacyVertexFormatAndPreservesPadding() {
        var format = net.neoforged.forgestuck.compat.LegacyVertexFormatShim.create(
                com.google.common.collect.ImmutableMap.of(
                        "Position", com.mojang.blaze3d.vertex.VertexFormatElement.POSITION,
                        "Padding", net.neoforged.forgestuck.compat.LegacyVertexFormatShim.paddingElementPlaceholder()));

        assertEquals(1, format.getElements().size());
        assertEquals(List.of("Position"), format.getElementAttributeNames());
        assertEquals(13, format.getVertexSize());
    }

    @Test
    void remapsLegacyAabbBlockPosConstructorAndHandle() {
        String aabb = "net/minecraft/world/phys/AABB";
        String blockPos = "net/minecraft/core/BlockPos";
        String legacyDescriptor = "(L" + blockPos + ";L" + blockPos + ";)V";
        String currentDescriptor = "(L" + blockPos + ";L" + blockPos + ";)L" + aabb + ";";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyAabbBounds", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "bounds",
                "(L" + blockPos + ";L" + blockPos + ";)L" + aabb + ";", null, null);
        method.visitCode();
        method.visitTypeInsn(Opcodes.NEW, aabb);
        method.visitInsn(Opcodes.DUP);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, aabb, "<init>", legacyDescriptor, false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        var handleMethod = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "constructorHandle",
                "()Ljava/lang/invoke/MethodHandle;", null, null);
        handleMethod.visitCode();
        handleMethod.visitLdcInsn(new Handle(Opcodes.H_NEWINVOKESPECIAL, aabb, "<init>", legacyDescriptor, false));
        handleMethod.visitInsn(Opcodes.ARETURN);
        handleMethod.visitMaxs(0, 0);
        handleMethod.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyAabbBounds", writer.toByteArray())).accept(transformed, 0);
        MethodInsnNode factory = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .filter(call -> call.owner.equals(aabb))
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, factory.getOpcode());
        assertEquals(aabb, factory.owner);
        assertEquals("encapsulatingFullBlocks", factory.name);
        assertEquals(currentDescriptor, factory.desc);
        assertTrue(java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .noneMatch(instruction -> instruction instanceof TypeInsnNode type
                        && type.getOpcode() == Opcodes.NEW && type.desc.equals(aabb)));
        Handle handle = java.util.Arrays.stream(transformed.methods.get(1).instructions.toArray())
                .filter(org.objectweb.asm.tree.LdcInsnNode.class::isInstance)
                .map(org.objectweb.asm.tree.LdcInsnNode.class::cast)
                .map(ldc -> (Handle) ldc.cst)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.H_INVOKESTATIC, handle.getTag());
        assertEquals(aabb, handle.getOwner());
        assertEquals("encapsulatingFullBlocks", handle.getName());
        assertEquals(currentDescriptor, handle.getDesc());
    }

    @Test
    void addsCurrentBlockInteractionBridgesForLegacyUseMethod() {
        String block = "net/minecraft/world/level/block/Block";
        String oldUseDescriptor = "(Lnet/minecraft/world/level/block/state/BlockState;"
                + "Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                + "Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;"
                + "Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;";
        String useWithoutItemDescriptor = "(Lnet/minecraft/world/level/block/state/BlockState;"
                + "Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                + "Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/BlockHitResult;)"
                + "Lnet/minecraft/world/InteractionResult;";
        String useItemOnDescriptor = "(Lnet/minecraft/world/item/ItemStack;"
                + "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;"
                + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;"
                + "Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)"
                + "Lnet/minecraft/world/ItemInteractionResult;";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyInventoryBlock", null, block, null);
        var use = writer.visitMethod(Opcodes.ACC_PUBLIC, "m_6227_", oldUseDescriptor, null, null);
        use.visitCode();
        use.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/world/InteractionResult", "CONSUME",
                "Lnet/minecraft/world/InteractionResult;");
        use.visitInsn(Opcodes.ARETURN);
        use.visitMaxs(1, 7);
        use.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyInventoryBlock", writer.toByteArray())).accept(transformed, 0);
        MethodNode useWithoutItem = transformed.methods.stream()
                .filter(method -> method.name.equals("useWithoutItem")
                        && method.desc.equals(useWithoutItemDescriptor))
                .findFirst().orElseThrow();
        MethodNode useItemOn = transformed.methods.stream()
                .filter(method -> method.name.equals("useItemOn") && method.desc.equals(useItemOnDescriptor))
                .findFirst().orElseThrow();
        assertTrue(java.util.Arrays.stream(useWithoutItem.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .anyMatch(call -> call.owner.equals("sample/LegacyInventoryBlock") && call.name.equals("m_6227_")
                        && call.desc.equals(oldUseDescriptor)));
        assertTrue(java.util.Arrays.stream(useItemOn.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .anyMatch(call -> call.owner.equals("net/neoforged/forgestuck/compat/LegacyBlockInteractionShim")
                        && call.name.equals("toItemInteractionResult")));
    }

    @Test
    void addsLegacyPassFallbackToDirectBlockSubclassWithoutUseOverride() {
        String block = "net/minecraft/world/level/block/Block";
        String oldUseDescriptor = "(Lnet/minecraft/world/level/block/state/BlockState;"
                + "Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                + "Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;"
                + "Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyBaseBlock", null, block, null);
        var legacyStateDefinition = writer.visitMethod(Opcodes.ACC_PROTECTED, "m_7926_",
                "(Lnet/minecraft/world/level/block/state/StateDefinition$Builder;)V", null, null);
        legacyStateDefinition.visitCode();
        legacyStateDefinition.visitInsn(Opcodes.RETURN);
        legacyStateDefinition.visitMaxs(0, 2);
        legacyStateDefinition.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyBaseBlock", writer.toByteArray())).accept(transformed, 0);
        MethodNode fallback = transformed.methods.stream()
                .filter(method -> method.name.equals("m_6227_") && method.desc.equals(oldUseDescriptor))
                .findFirst().orElseThrow();
        assertTrue((fallback.access & Opcodes.ACC_PUBLIC) != 0);
        assertTrue(java.util.Arrays.stream(fallback.instructions.toArray())
                .anyMatch(instruction -> instruction instanceof FieldInsnNode field
                        && field.owner.equals("net/minecraft/world/InteractionResult")
                        && field.name.equals("PASS")));
        assertFalse(transformed.methods.stream().anyMatch(method ->
                method.name.equals("useItemOn") || method.name.equals("useWithoutItem")));
    }

    @Test
    void remapsLegacyDyeableLeatherColorMethodBeforeOwnerReplacement() {
        String dyeable = "net/minecraft/world/item/DyeableLeatherItem";
        String stack = "net/minecraft/world/item/ItemStack";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyDyeableColor", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "color",
                "(L" + dyeable + ";L" + stack + ";)I", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, dyeable, "m_41121_",
                "(L" + stack + ";)I", true);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(2, 2);
        method.visitEnd();
        writer.visitEnd();

        byte[] srgRemapped = new SrgMemberRemapStep().apply("sample.LegacyDyeableColor", writer.toByteArray());
        ClassNode transformed = new ClassNode();
        new ClassReader(new ForgeOwnerRemapStep().apply("sample.LegacyDyeableColor", srgRemapped))
                .accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals("net/neoforged/forgestuck/compat/LegacyDyeableLeatherItem", call.owner);
        assertEquals("getColor", call.name);
    }

    @Test
    void remapsInheritedItemIsFoilCallThroughSubclassOwner() {
        String item = "net/minecraft/world/item/Item";
        String itemStack = "net/minecraft/world/item/ItemStack";
        String potionInfuser = "cofh/thermal/innovation/common/item/PotionInfuserItem";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyItemFoilCheck", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "isFoil",
                "(L" + potionInfuser + ";L" + itemStack + ";)Z", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, potionInfuser, "m_41113_",
                "(L" + itemStack + ";)Z", false);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 2);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyItemFoilCheck", writer.toByteArray())).accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKEVIRTUAL, call.getOpcode());
        assertEquals(potionInfuser, call.owner);
        assertEquals("isFoil", call.name);
        assertEquals("(L" + itemStack + ";)Z", call.desc);
    }

    @Test
    void adaptsLegacyItemStackEnchantmentTagAccessorToComponentShim() {
        String itemStack = "net/minecraft/world/item/ItemStack";
        String listTag = "net/minecraft/nbt/ListTag";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "cofh/thermal/lib/common/block/entity/AugmentableBlockEntity",
                null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "readEnchantments",
                "(L" + itemStack + ";)L" + listTag + ";", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, itemStack, "m_41785_",
                "()L" + listTag + ";", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 1);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "cofh.thermal.lib.common.block.entity.AugmentableBlockEntity", writer.toByteArray()))
                .accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyItemStackShim", call.owner);
        assertEquals("getEnchantmentTags", call.name);
        assertEquals("(L" + itemStack + ";)L" + listTag + ";", call.desc);
    }

    @Test
    void adaptsLegacyServerListenerConnectionFieldToAccessor() {
        String listener = "net/minecraft/server/network/ServerGamePacketListenerImpl";
        String connection = "net/minecraft/network/Connection";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyConnectionCheck", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "connection",
                "(L" + listener + ";)L" + connection + ";", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitFieldInsn(Opcodes.GETFIELD, listener, "f_9742_", "L" + connection + ";");
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyConnectionCheck", writer.toByteArray())).accept(transformed, 0);
        MethodInsnNode accessor = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKEVIRTUAL, accessor.getOpcode());
        assertEquals(listener, accessor.owner);
        assertEquals("getConnection", accessor.name);
        assertEquals("()L" + connection + ";", accessor.desc);
    }

    @Test
    void adaptsCoFHParticleRenderingToCurrentBufferLifecycle() {
        String tesselator = "com/mojang/blaze3d/vertex/Tesselator";
        String bufferBuilder = "com/mojang/blaze3d/vertex/BufferBuilder";
        String particleRenderType = "net/minecraft/client/particle/ParticleRenderType";
        String textureManager = "net/minecraft/client/renderer/texture/TextureManager";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "cofh/core/client/event/CoreClientEvents", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "renderTranslucent",
                "()V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 6);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, tesselator, "m_85915_",
                "()L" + bufferBuilder + ";", false);
        method.visitVarInsn(Opcodes.ASTORE, 7);
        method.visitVarInsn(Opcodes.ALOAD, 11);
        method.visitVarInsn(Opcodes.ALOAD, 7);
        method.visitVarInsn(Opcodes.ALOAD, 5);
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, particleRenderType, "m_6505_",
                "(L" + bufferBuilder + ";L" + textureManager + ";)V", true);
        method.visitVarInsn(Opcodes.ALOAD, 11);
        method.visitVarInsn(Opcodes.ALOAD, 6);
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, particleRenderType, "m_6294_",
                "(L" + tesselator + ";)V", true);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 12);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "cofh.core.client.event.CoreClientEvents", writer.toByteArray())).accept(transformed, 0);
        var instructions = transformed.methods.get(0).instructions.toArray();
        var calls = java.util.Arrays.stream(instructions)
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        assertTrue(calls.stream().noneMatch(call -> call.name.equals("m_85915_")
                || call.name.equals("m_6505_") || call.name.equals("m_6294_")));
        assertTrue(calls.stream().anyMatch(call -> call.owner.equals(particleRenderType)
                && call.name.equals("begin") && call.getOpcode() == Opcodes.INVOKEINTERFACE
                && call.desc.equals("(L" + tesselator + ";L" + textureManager + ";)L" + bufferBuilder + ";")));
        assertTrue(calls.stream().anyMatch(call -> call.owner.equals(bufferBuilder)
                && call.name.equals("build") && call.desc.equals("()Lcom/mojang/blaze3d/vertex/MeshData;")));
        assertTrue(calls.stream().anyMatch(call -> call.owner.equals("com/mojang/blaze3d/vertex/BufferUploader")
                && call.name.equals("drawWithShader")));
    }

    @Test
    void adaptsLegacyTesselatorBufferLifecycleForAnyMod() {
        String tesselator = "com/mojang/blaze3d/vertex/Tesselator";
        String bufferBuilder = "com/mojang/blaze3d/vertex/BufferBuilder";
        String vertexFormat = "com/mojang/blaze3d/vertex/VertexFormat";
        String mode = "com/mojang/blaze3d/vertex/VertexFormat$Mode";
        String renderedBuffer = "com/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer";
        String meshData = "com/mojang/blaze3d/vertex/MeshData";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyFluidGui", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "render",
                "(L" + tesselator + ";)V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, tesselator, "m_85915_",
                "()L" + bufferBuilder + ";", false);
        method.visitVarInsn(Opcodes.ASTORE, 1);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitFieldInsn(Opcodes.GETSTATIC, mode, "QUADS", "L" + mode + ";");
        method.visitFieldInsn(Opcodes.GETSTATIC, "com/mojang/blaze3d/vertex/DefaultVertexFormat",
                "POSITION_TEX", "L" + vertexFormat + ";");
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, bufferBuilder, "m_166779_",
                "(L" + mode + ";L" + vertexFormat + ";)V", false);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, bufferBuilder, "m_231175_",
                "()L" + renderedBuffer + ";", false);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "com/mojang/blaze3d/vertex/BufferUploader",
                "m_231202_", "(L" + renderedBuffer + ";)V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 2);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyFluidGui", writer.toByteArray())).accept(transformed, 0);
        var calls = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        assertTrue(java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .anyMatch(instruction -> instruction.getOpcode() == Opcodes.ACONST_NULL));
        assertTrue(calls.stream().noneMatch(call -> call.name.equals("m_85915_")
                || call.name.equals("m_166779_") || call.name.equals("m_231175_")
                || call.name.equals("m_231202_")));
        assertTrue(calls.stream().anyMatch(call -> call.owner.equals(tesselator)
                && call.name.equals("begin") && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                && call.desc.equals("(L" + mode + ";L" + vertexFormat + ";)L" + bufferBuilder + ";")));
        assertTrue(calls.stream().anyMatch(call -> call.owner.equals(bufferBuilder)
                && call.name.equals("build") && call.desc.equals("()L" + meshData + ";")));
        assertTrue(calls.stream().anyMatch(call -> call.owner.equals("com/mojang/blaze3d/vertex/BufferUploader")
                && call.name.equals("drawWithShader") && call.desc.equals("(L" + meshData + ";)V")));
    }

    @Test
    void adaptsLegacyTesselatorEndLifecycle() {
        String tesselator = "com/mojang/blaze3d/vertex/Tesselator";
        String bufferBuilder = "com/mojang/blaze3d/vertex/BufferBuilder";
        String vertexFormat = "com/mojang/blaze3d/vertex/VertexFormat";
        String mode = "com/mojang/blaze3d/vertex/VertexFormat$Mode";
        String meshData = "com/mojang/blaze3d/vertex/MeshData";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyTesselatorEnd", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "render", "()V", null, null);
        method.visitCode();
        method.visitMethodInsn(Opcodes.INVOKESTATIC, tesselator, "getInstance",
                "()L" + tesselator + ";", false);
        method.visitVarInsn(Opcodes.ASTORE, 0);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, tesselator, "m_85915_",
                "()L" + bufferBuilder + ";", false);
        method.visitVarInsn(Opcodes.ASTORE, 1);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitFieldInsn(Opcodes.GETSTATIC, mode, "QUADS", "L" + mode + ";");
        method.visitFieldInsn(Opcodes.GETSTATIC, "com/mojang/blaze3d/vertex/DefaultVertexFormat",
                "POSITION_TEX", "L" + vertexFormat + ";");
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, bufferBuilder, "m_166779_",
                "(L" + mode + ";L" + vertexFormat + ";)V", false);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, tesselator, "m_85914_", "()V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 2);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyTesselatorEnd", writer.toByteArray())).accept(transformed, 0);
        var calls = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        assertTrue(calls.stream().noneMatch(call -> call.name.equals("m_85915_")
                || call.name.equals("m_166779_") || call.name.equals("m_85914_")));
        assertTrue(calls.stream().anyMatch(call -> call.owner.equals(tesselator)
                && call.name.equals("begin")
                && call.desc.equals("(L" + mode + ";L" + vertexFormat + ";)L" + bufferBuilder + ";")));
        assertTrue(calls.stream().anyMatch(call -> call.owner.equals(bufferBuilder)
                && call.name.equals("build") && call.desc.equals("()L" + meshData + ";")));
        assertTrue(calls.stream().anyMatch(call -> call.owner.equals("com/mojang/blaze3d/vertex/BufferUploader")
                && call.name.equals("drawWithShader") && call.desc.equals("(L" + meshData + ";)V")));
    }

    @Test
    void adaptsLegacyTesselatorLifecycleWhenReceiverComesFromInterfaceAccessor() {
        String tesselator = "com/mojang/blaze3d/vertex/Tesselator";
        String bufferBuilder = "com/mojang/blaze3d/vertex/BufferBuilder";
        String vertexFormat = "com/mojang/blaze3d/vertex/VertexFormat";
        String mode = "com/mojang/blaze3d/vertex/VertexFormat$Mode";
        String renderedBuffer = "com/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer";
        String meshData = "com/mojang/blaze3d/vertex/MeshData";
        String target = "sample/DrawTarget";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyDrawTarget", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "render",
                "(L" + target + ";)V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, target, "tesselator",
                "()L" + tesselator + ";", true);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, tesselator, "m_85915_",
                "()L" + bufferBuilder + ";", false);
        method.visitVarInsn(Opcodes.ASTORE, 1);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitFieldInsn(Opcodes.GETSTATIC, mode, "QUADS", "L" + mode + ";");
        method.visitFieldInsn(Opcodes.GETSTATIC, "com/mojang/blaze3d/vertex/DefaultVertexFormat",
                "POSITION_TEX", "L" + vertexFormat + ";");
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, bufferBuilder, "m_166779_",
                "(L" + mode + ";L" + vertexFormat + ";)V", false);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, bufferBuilder, "m_231175_",
                "()" + "L" + renderedBuffer + ";", false);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "com/mojang/blaze3d/vertex/BufferUploader",
                "m_231202_", "(L" + renderedBuffer + ";)V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 2);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyDrawTarget", writer.toByteArray())).accept(transformed, 0);
        var calls = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        assertTrue(calls.stream().noneMatch(call -> call.name.equals("m_85915_")
                || call.name.equals("m_166779_") || call.name.equals("m_231175_")
                || call.name.equals("m_231202_")));
        assertTrue(calls.stream().anyMatch(call -> call.owner.equals(tesselator)
                && call.name.equals("getInstance") && call.getOpcode() == Opcodes.INVOKESTATIC));
        assertTrue(calls.stream().anyMatch(call -> call.owner.equals(tesselator)
                && call.name.equals("begin") && call.getOpcode() == Opcodes.INVOKEVIRTUAL));
        assertTrue(calls.stream().anyMatch(call -> call.owner.equals(bufferBuilder)
                && call.name.equals("build") && call.desc.equals("()L" + meshData + ";")));
    }

    @Test
    void adaptsLegacyFriendlyByteBufIdWrite() {
        String friendlyByteBuf = "net/minecraft/network/FriendlyByteBuf";
        String idMap = "net/minecraft/core/IdMap";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyIdMapWriter", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "write",
                "(L" + friendlyByteBuf + ";L" + idMap + ";Ljava/lang/Object;)V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, friendlyByteBuf, "m_236818_",
                "(L" + idMap + ";Ljava/lang/Object;)V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 3);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyIdMapWriter", writer.toByteArray())).accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyFriendlyByteBuf", call.owner);
        assertEquals("writeId", call.name);
        assertEquals("(L" + friendlyByteBuf + ";L" + idMap + ";Ljava/lang/Object;)V", call.desc);
    }

    @Test
    void adaptsLegacyFriendlyByteBufItemStackPacketMethods() {
        String friendlyByteBuf = "net/minecraft/network/FriendlyByteBuf";
        String itemStack = "net/minecraft/world/item/ItemStack";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyItemStackBuffer", null,
                "java/lang/Object", null);
        var write = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "write",
                "(L" + friendlyByteBuf + ";L" + itemStack + ";)L" + friendlyByteBuf + ";", null, null);
        write.visitCode();
        write.visitVarInsn(Opcodes.ALOAD, 0);
        write.visitVarInsn(Opcodes.ALOAD, 1);
        write.visitMethodInsn(Opcodes.INVOKEVIRTUAL, friendlyByteBuf, "m_130055_",
                "(L" + itemStack + ";)L" + friendlyByteBuf + ";", false);
        write.visitInsn(Opcodes.ARETURN);
        write.visitMaxs(0, 2);
        write.visitEnd();
        var read = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "read",
                "(L" + friendlyByteBuf + ";)L" + itemStack + ";", null, null);
        read.visitCode();
        read.visitVarInsn(Opcodes.ALOAD, 0);
        read.visitMethodInsn(Opcodes.INVOKEVIRTUAL, friendlyByteBuf, "m_130267_",
                "()L" + itemStack + ";", false);
        read.visitInsn(Opcodes.ARETURN);
        read.visitMaxs(0, 1);
        read.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyItemStackBuffer", writer.toByteArray())).accept(transformed, 0);
        var calls = transformed.methods.stream()
                .flatMap(method -> java.util.Arrays.stream(method.instructions.toArray()))
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        assertEquals(2, calls.size());
        assertTrue(calls.stream().allMatch(call -> call.getOpcode() == Opcodes.INVOKESTATIC
                && call.owner.equals("net/neoforged/forgestuck/compat/LegacyFriendlyByteBuf")));
        assertTrue(calls.stream().anyMatch(call -> call.name.equals("writeItem")
                && call.desc.equals("(L" + friendlyByteBuf + ";L" + itemStack + ";)L" + friendlyByteBuf + ";")));
        assertTrue(calls.stream().anyMatch(call -> call.name.equals("readItem")
                && call.desc.equals("(L" + friendlyByteBuf + ";)L" + itemStack + ";")));
    }

    @Test
    void adaptsLegacyMobEffectLookupToHolderAwareCompatMethod() {
        String player = "net/minecraft/world/entity/player/Player";
        String livingEntity = "net/minecraft/world/entity/LivingEntity";
        String mobEffect = "net/minecraft/world/effect/MobEffect";
        String instance = "net/minecraft/world/effect/MobEffectInstance";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyMobEffectLookup", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "get",
                "(L" + player + ";L" + mobEffect + ";)L" + instance + ";", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, player, "getEffect",
                "(L" + mobEffect + ";)L" + instance + ";", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 2);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyMobEffectLookup", writer.toByteArray())).accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyMobEffect", call.owner);
        assertEquals("getEffect", call.name);
        assertEquals("(L" + livingEntity + ";L" + mobEffect + ";)L" + instance + ";", call.desc);
    }

    @Test
    void adaptsLegacyMobEffectRemovalToHolderAwareCompatMethod() {
        String player = "net/minecraft/world/entity/player/Player";
        String livingEntity = "net/minecraft/world/entity/LivingEntity";
        String mobEffect = "net/minecraft/world/effect/MobEffect";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyMobEffectRemoval", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "remove",
                "(L" + player + ";L" + mobEffect + ";)Z", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, player, "removeEffect",
                "(L" + mobEffect + ";)Z", false);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 2);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyMobEffectRemoval", writer.toByteArray())).accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyMobEffect", call.owner);
        assertEquals("removeEffect", call.name);
        assertEquals("(L" + livingEntity + ";L" + mobEffect + ";)Z", call.desc);
    }

    @Test
    void expandsTConstructEquipmentContextCachesForTheBodySlot() {
        String context = "slimeknights/tconstruct/library/tools/context/EquipmentContext";
        String toolView = "slimeknights/tconstruct/library/tools/nbt/IToolStackView";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, context, null, "java/lang/Object", null);
        var constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitIntInsn(Opcodes.BIPUSH, 6);
        constructor.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BOOLEAN);
        constructor.visitInsn(Opcodes.POP);
        constructor.visitIntInsn(Opcodes.BIPUSH, 6);
        constructor.visitTypeInsn(Opcodes.ANEWARRAY, toolView);
        constructor.visitInsn(Opcodes.POP);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 1);
        constructor.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(context, writer.toByteArray())).accept(transformed, 0);
        var sizes = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(IntInsnNode.class::isInstance).map(IntInsnNode.class::cast)
                .filter(instruction -> instruction.getOpcode() == Opcodes.BIPUSH)
                .map(instruction -> instruction.operand).toList();
        assertEquals(List.of(7, 7), sizes);
    }

    @Test
    void adaptsLegacyAttributeValueLookupToHolderAwareCompatMethod() {
        String livingEntity = "net/minecraft/world/entity/LivingEntity";
        String attribute = "net/minecraft/world/entity/ai/attributes/Attribute";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyAttributeLookup", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "value",
                "(L" + livingEntity + ";L" + attribute + ";)D", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, livingEntity, "getAttributeValue",
                "(L" + attribute + ";)D", false);
        method.visitInsn(Opcodes.DRETURN);
        method.visitMaxs(0, 2);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyAttributeLookup", writer.toByteArray())).accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyAttribute", call.owner);
        assertEquals("getValue", call.name);
        assertEquals("(L" + livingEntity + ";L" + attribute + ";)D", call.desc);
    }

    @Test
    void adaptsLegacyEntityArmorSlotsToLivingEntityAccessor() {
        String entity = "net/minecraft/world/entity/Entity";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyArmorSlots", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "armor",
                "(L" + entity + ";)Ljava/lang/Iterable;", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, entity, "getArmorSlots",
                "()Ljava/lang/Iterable;", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 1);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyArmorSlots", writer.toByteArray())).accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyEntityEquipment", call.owner);
        assertEquals("getArmorSlots", call.name);
        assertEquals("(L" + entity + ";)Ljava/lang/Iterable;", call.desc);
    }

    @Test
    void adaptsHolderReferenceContainsTagToIs() {
        String holderReference = "net/minecraft/core/Holder$Reference";
        String tagKey = "net/minecraft/tags/TagKey";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyHolderTagCheck", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "hasTag",
                "(L" + holderReference + ";L" + tagKey + ";)Z", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, holderReference, "containsTag",
                "(L" + tagKey + ";)Z", false);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 2);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyHolderTagCheck", writer.toByteArray())).accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKEVIRTUAL, call.getOpcode());
        assertEquals(holderReference, call.owner);
        assertEquals("is", call.name);
        assertEquals("(L" + tagKey + ";)Z", call.desc);
    }

    @Test
    void adaptsHolderInterfaceContainsTagToIs() {
        String holder = "net/minecraft/core/Holder";
        String tagKey = "net/minecraft/tags/TagKey";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyHolderInterfaceTagCheck", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "hasTag",
                "(L" + holder + ";L" + tagKey + ";)Z", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, holder, "containsTag",
                "(L" + tagKey + ";)Z", true);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 2);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyHolderInterfaceTagCheck", writer.toByteArray())).accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKEINTERFACE, call.getOpcode());
        assertEquals(holder, call.owner);
        assertTrue(call.itf);
        assertEquals("is", call.name);
        assertEquals("(L" + tagKey + ";)Z", call.desc);
    }

    @Test
    void adaptsLegacyTConstructSpawnEquipmentCalls() {
        String mob = "net/minecraft/world/entity/Mob";
        String event = "net/neoforged/neoforge/event/entity/living/FinalizeSpawnEvent";
        String randomSource = "net/minecraft/util/RandomSource";
        String serverLevel = "net/minecraft/world/level/ServerLevelAccessor";
        String difficulty = "net/minecraft/world/DifficultyInstance";
        String spawnType = "net/minecraft/world/entity/MobSpawnType";
        String spawnData = "net/minecraft/world/entity/SpawnGroupData";
        String compoundTag = "net/minecraft/nbt/CompoundTag";
        String legacyFinalize = "(L" + serverLevel + ";L" + difficulty + ";L" + spawnType
                + ";L" + spawnData + ";L" + compoundTag + ";)L" + spawnData + ";";
        String finalize = "(L" + serverLevel + ";L" + difficulty + ";L" + spawnType
                + ";L" + spawnData + ";)L" + spawnData + ";";
        String equipment = "slimeknights/tconstruct/library/json/loot/equipment/MobEquipment";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, equipment, null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "spawn",
                "(L" + mob + ";L" + event + ";L" + serverLevel + ";L" + difficulty
                        + ";L" + spawnType + ";L" + spawnData + ";)V",
                null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, mob, "m_217043_",
                "()L" + randomSource + ";", false);
        method.visitInsn(Opcodes.POP);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitVarInsn(Opcodes.ALOAD, 3);
        method.visitVarInsn(Opcodes.ALOAD, 4);
        method.visitVarInsn(Opcodes.ALOAD, 5);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, event, "getSpawnTag",
                "()L" + compoundTag + ";", false);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, mob, "m_6518_", legacyFinalize, false);
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 6);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(equipment, writer.toByteArray())).accept(transformed, 0);
        var calls = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        assertEquals(2, calls.size());
        assertTrue(calls.stream().anyMatch(call -> call.owner.equals("net/minecraft/world/entity/Entity")
                && call.name.equals("getRandom") && call.desc.equals("()L" + randomSource + ";")));
        assertTrue(calls.stream().anyMatch(call -> call.owner.equals(mob)
                && call.name.equals("finalizeSpawn") && call.desc.equals(finalize)));
    }

    @Test
    void adaptsLegacyItemStackGetTagElementWithoutCreation() {
        String itemStack = "net/minecraft/world/item/ItemStack";
        String compoundTag = "net/minecraft/nbt/CompoundTag";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyStackTagElement", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "get",
                "(L" + itemStack + ";Ljava/lang/String;)L" + compoundTag + ";", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, itemStack, "m_41737_",
                "(Ljava/lang/String;)L" + compoundTag + ";", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 2);
        method.visitEnd();
        var namedMethod = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "getNamed",
                "(L" + itemStack + ";Ljava/lang/String;)L" + compoundTag + ";", null, null);
        namedMethod.visitCode();
        namedMethod.visitVarInsn(Opcodes.ALOAD, 0);
        namedMethod.visitVarInsn(Opcodes.ALOAD, 1);
        namedMethod.visitMethodInsn(Opcodes.INVOKEVIRTUAL, itemStack, "getTagElement",
                "(Ljava/lang/String;)L" + compoundTag + ";", false);
        namedMethod.visitInsn(Opcodes.ARETURN);
        namedMethod.visitMaxs(0, 2);
        namedMethod.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyStackTagElement", writer.toByteArray())).accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyItemStackShim", call.owner);
        assertEquals("getTagElement", call.name);
        assertEquals("(L" + itemStack + ";Ljava/lang/String;)L" + compoundTag + ";", call.desc);
        MethodInsnNode namedCall = java.util.Arrays.stream(transformed.methods.get(1).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, namedCall.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyItemStackShim", namedCall.owner);
        assertEquals("getTagElement", namedCall.name);
        assertEquals("(L" + itemStack + ";Ljava/lang/String;)L" + compoundTag + ";", namedCall.desc);
    }

    @Test
    void preservesLegacyItemStackGetOrCreateTagElement() {
        String itemStack = "net/minecraft/world/item/ItemStack";
        String compoundTag = "net/minecraft/nbt/CompoundTag";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyCreateTagElement", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "get",
                "(L" + itemStack + ";Ljava/lang/String;)L" + compoundTag + ";", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, itemStack, "getOrCreateTagElement",
                "(Ljava/lang/String;)L" + compoundTag + ";", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 2);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyCreateTagElement", writer.toByteArray())).accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyItemStackShim", call.owner);
        assertEquals("getOrCreateTagElement", call.name);
        assertEquals("(L" + itemStack + ";Ljava/lang/String;)L" + compoundTag + ";", call.desc);
    }

    @Test
    void adaptsLegacyItemStackSetHoverNameToCustomNameComponent() {
        String itemStack = "net/minecraft/world/item/ItemStack";
        String component = "net/minecraft/network/chat/Component";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyStackHoverName", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "setName",
                "(L" + itemStack + ";L" + component + ";)L" + itemStack + ";", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, itemStack, "m_41714_",
                "(L" + component + ";)L" + itemStack + ";", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 2);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyStackHoverName", writer.toByteArray())).accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyItemStackShim", call.owner);
        assertEquals("setHoverName", call.name);
        assertEquals("(L" + itemStack + ";L" + component + ";)L" + itemStack + ";", call.desc);
    }

    @Test
    void adaptsLegacyPatchouliVariableLookupWithTheLevelRegistryAccess() {
        String level = "net/minecraft/world/level/Level";
        String provider = "vazkii/patchouli/api/IVariableProvider";
        String variable = "vazkii/patchouli/api/IVariable";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyPatchouliProcessor", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "setup",
                "(L" + level + ";L" + provider + ";)V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitLdcInsn("output");
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, provider, "get",
                "(Ljava/lang/String;)L" + variable + ";", true);
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 3);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyPatchouliProcessor", writer.toByteArray())).accept(transformed, 0);
        assertTrue(transformed.methods.get(0).maxStack >= 3);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .filter(access -> access.owner.equals(provider))
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKEINTERFACE, call.getOpcode());
        assertEquals(provider, call.owner);
        assertEquals("(Ljava/lang/String;Lnet/minecraft/core/HolderLookup$Provider;)L" + variable + ";", call.desc);
        assertTrue(java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .anyMatch(access -> access.owner.equals(level) && access.name.equals("registryAccess")
                        && access.desc.equals("()Lnet/minecraft/core/RegistryAccess;")));
    }

    @Test
    void adaptsLegacyFriendlyByteBufCompoundTagWrite() {
        String friendlyByteBuf = "net/minecraft/network/FriendlyByteBuf";
        String compoundTag = "net/minecraft/nbt/CompoundTag";
        String tag = "net/minecraft/nbt/Tag";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyCompoundTagWriter", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "write",
                "(L" + friendlyByteBuf + ";L" + compoundTag + ";)L" + friendlyByteBuf + ";", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, friendlyByteBuf, "m_130079_",
                "(L" + compoundTag + ";)L" + friendlyByteBuf + ";", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 2);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyCompoundTagWriter", writer.toByteArray())).accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKEVIRTUAL, call.getOpcode());
        assertEquals(friendlyByteBuf, call.owner);
        assertEquals("writeNbt", call.name);
        assertEquals("(L" + tag + ";)L" + friendlyByteBuf + ";", call.desc);
    }

    @Test
    void adaptsLegacyIngredientIsVanillaCall() {
        String ingredient = "net/minecraft/world/item/crafting/Ingredient";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyIngredientCheck", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "check",
                "(L" + ingredient + ";)Z", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, ingredient, "isVanilla", "()Z", false);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 1);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyIngredientCheck", writer.toByteArray())).accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyIngredientBridge", call.owner);
        assertEquals("isVanilla", call.name);
        assertEquals("(L" + ingredient + ";)Z", call.desc);
    }

    @Test
    void adaptsMantleItemStackShareTagCallsToComponentPatchHelpers() {
        String friendlyByteBuf = "net/minecraft/network/FriendlyByteBuf";
        String itemStack = "net/minecraft/world/item/ItemStack";
        String compoundTag = "net/minecraft/nbt/CompoundTag";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC,
                "slimeknights/mantle/data/loadable/common/ItemStackLoadable$NBTStack", null,
                "java/lang/Object", null);
        var encode = writer.visitMethod(Opcodes.ACC_PUBLIC, "encode",
                "(L" + friendlyByteBuf + ";L" + itemStack + ";)V", null, null);
        encode.visitCode();
        encode.visitVarInsn(Opcodes.ALOAD, 2);
        encode.visitMethodInsn(Opcodes.INVOKEVIRTUAL, itemStack, "getShareTag",
                "()L" + compoundTag + ";", false);
        encode.visitInsn(Opcodes.POP);
        encode.visitInsn(Opcodes.RETURN);
        encode.visitMaxs(0, 3);
        encode.visitEnd();
        var decode = writer.visitMethod(Opcodes.ACC_PUBLIC, "decode",
                "(L" + friendlyByteBuf + ";Lsample/TypedMap;L" + itemStack + ";L" + compoundTag + ";)V",
                null, null);
        decode.visitCode();
        decode.visitVarInsn(Opcodes.ALOAD, 3);
        decode.visitVarInsn(Opcodes.ALOAD, 4);
        decode.visitMethodInsn(Opcodes.INVOKEVIRTUAL, itemStack, "readShareTag",
                "(L" + compoundTag + ";)V", false);
        decode.visitInsn(Opcodes.RETURN);
        decode.visitMaxs(0, 5);
        decode.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "slimeknights.mantle.data.loadable.common.ItemStackLoadable$NBTStack",
                writer.toByteArray())).accept(transformed, 0);
        var calls = transformed.methods.stream()
                .flatMap(method -> java.util.Arrays.stream(method.instructions.toArray()))
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        assertEquals(2, calls.size());
        assertTrue(calls.stream().allMatch(call -> call.getOpcode() == Opcodes.INVOKESTATIC
                && call.owner.equals("net/neoforged/forgestuck/compat/LegacyItemStackShim")));
        assertTrue(calls.stream().anyMatch(call -> call.name.equals("getShareTag")
                && call.desc.equals("(L" + friendlyByteBuf + ";L" + itemStack + ";)L" + compoundTag + ";")));
        assertTrue(calls.stream().anyMatch(call -> call.name.equals("readShareTag")
                && call.desc.equals("(L" + friendlyByteBuf + ";L" + itemStack + ";L" + compoundTag + ";)V")));
    }

    @Test
    void adaptsLegacyLivingEntityHasEffectCallToHolderApi() {
        String livingEntity = "net/minecraft/world/entity/LivingEntity";
        String mobEffect = "net/minecraft/world/effect/MobEffect";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyEffectCheck", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "check",
                "(L" + livingEntity + ";L" + mobEffect + ";)Z", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, livingEntity, "m_21023_",
                "(L" + mobEffect + ";)Z", false);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyEffectCheck", writer.toByteArray())).accept(transformed, 0);
        var call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyMobEffect", call.owner);
        assertEquals("hasEffect", call.name);
        assertEquals("(L" + livingEntity + ";L" + mobEffect + ";)Z", call.desc);
    }

    @Test
    void adaptsLegacyPlayerHasEffectCallToHolderApi() {
        String player = "net/minecraft/world/entity/player/Player";
        String mobEffect = "net/minecraft/world/effect/MobEffect";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyPlayerEffectCheck", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "check",
                "(L" + player + ";L" + mobEffect + ";)Z", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, player, "m_21023_",
                "(L" + mobEffect + ";)Z", false);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 2);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyPlayerEffectCheck", writer.toByteArray())).accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyMobEffect", call.owner);
        assertEquals("(Lnet/minecraft/world/entity/LivingEntity;L" + mobEffect + ";)Z", call.desc);
    }

    @Test
    void adaptsLegacyUnbakedModelBakeSignature() {
        String unbakedModel = "net/minecraft/client/resources/model/UnbakedModel";
        String modelBaker = "net/minecraft/client/resources/model/ModelBaker";
        String modelState = "net/minecraft/client/resources/model/ModelState";
        String resourceLocation = "net/minecraft/resources/ResourceLocation";
        String bakedModel = "net/minecraft/client/resources/model/BakedModel";
        String legacyDescriptor = "(L" + modelBaker + ";Ljava/util/function/Function;L" + modelState
                + ";L" + resourceLocation + ";)L" + bakedModel + ";";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyModelBake", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "bake",
                "(L" + unbakedModel + ";L" + modelBaker + ";Ljava/util/function/Function;L" + modelState
                        + ";L" + resourceLocation + ";)L" + bakedModel + ";",
                null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitVarInsn(Opcodes.ALOAD, 3);
        method.visitVarInsn(Opcodes.ALOAD, 4);
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, unbakedModel, "m_7611_", legacyDescriptor, true);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyModelBake", writer.toByteArray())).accept(transformed, 0);
        var instructions = transformed.methods.get(0).instructions.toArray();
        MethodInsnNode bakeCall = java.util.Arrays.stream(instructions)
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals("bake", bakeCall.name);
        assertEquals("(L" + modelBaker + ";Ljava/util/function/Function;L" + modelState + ";)L" + bakedModel + ";",
                bakeCall.desc);
        int callIndex = java.util.Arrays.asList(instructions).indexOf(bakeCall);
        assertEquals(Opcodes.POP, instructions[callIndex - 1].getOpcode());
    }

    @Test
    void bridgesLegacyUnbakedModelImplementationsToCurrentBakeSignature() {
        String unbakedModel = "net/minecraft/client/resources/model/UnbakedModel";
        String modelBaker = "net/minecraft/client/resources/model/ModelBaker";
        String modelState = "net/minecraft/client/resources/model/ModelState";
        String bakedModel = "net/minecraft/client/resources/model/BakedModel";
        String legacyDescriptor = "(L" + modelBaker
                + ";Ljava/util/function/Function;L" + modelState
                + ";Lnet/minecraft/resources/ResourceLocation;)L" + bakedModel + ";";
        String targetDescriptor = "(L" + modelBaker
                + ";Ljava/util/function/Function;L" + modelState + ";)L" + bakedModel + ";";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyUnbakedModel", null,
                "java/lang/Object", new String[] {unbakedModel});
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "m_7611_", legacyDescriptor, null, null);
        method.visitCode();
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 5);
        method.visitEnd();
        writer.visitEnd();

        SrgMemberRemapStep step = new SrgMemberRemapStep();
        step.scan("sample.LegacyUnbakedModel", writer.toByteArray(), null);
        ClassNode transformed = new ClassNode();
        new ClassReader(step.apply("sample.LegacyUnbakedModel", writer.toByteArray()))
                .accept(transformed, 0);

        MethodNode bridge = transformed.methods.stream()
                .filter(candidate -> candidate.name.equals("bake") && candidate.desc.equals(targetDescriptor))
                .findFirst().orElseThrow();
        var calls = java.util.Arrays.stream(bridge.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        assertTrue(calls.stream().anyMatch(call -> call.owner.equals(
                "net/neoforged/forgestuck/compat/LegacyModelResourceLocations")
                && call.name.equals("missingModelLocation")));
        assertTrue(calls.stream().anyMatch(call -> call.owner.equals("sample/LegacyUnbakedModel")
                && (call.name.equals("bake") || call.name.equals("m_7611_"))
                && call.desc.equals(legacyDescriptor)));
    }

    @Test
    void bridgesLegacyContainerMethodsThroughAddonInterfaces() {
        ClassWriter interfaceWriter = new ClassWriter(0);
        interfaceWriter.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT,
                "sample/AddonInventory", null, "java/lang/Object",
                new String[] {"net/minecraft/world/Container"});
        interfaceWriter.visitEnd();

        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classWriter.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyInventory", null,
                "java/lang/Object", new String[] {"sample/AddonInventory"});
        var size = classWriter.visitMethod(Opcodes.ACC_PUBLIC, "m_6643_", "()I", null, null);
        size.visitCode();
        size.visitIntInsn(Opcodes.BIPUSH, 4);
        size.visitInsn(Opcodes.IRETURN);
        size.visitMaxs(1, 1);
        size.visitEnd();
        classWriter.visitEnd();

        SrgMemberRemapStep step = new SrgMemberRemapStep();
        step.scan("sample.AddonInventory", interfaceWriter.toByteArray(), null);
        step.scan("sample.LegacyInventory", classWriter.toByteArray(), null);
        ClassNode transformed = new ClassNode();
        new ClassReader(step.apply("sample.LegacyInventory", classWriter.toByteArray()))
                .accept(transformed, 0);

        MethodNode bridge = transformed.methods.stream()
                .filter(method -> method.name.equals("getContainerSize") && method.desc.equals("()I"))
                .findFirst().orElseThrow();
        assertTrue(java.util.Arrays.stream(bridge.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .anyMatch(call -> call.name.equals("m_6643_") && call.owner.equals("sample/LegacyInventory")));
    }

    @Test
    void bridgesLegacyIngredientJsonAndNetworkMethods() {
        String ingredient = "net/minecraft/world/item/crafting/Ingredient";
        String json = "com/google/gson/JsonElement";
        String buffer = "net/minecraft/network/FriendlyByteBuf";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyIngredientCodec", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "calls", "()V", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, ingredient, "m_43917_",
                "(L" + json + ";)L" + ingredient + ";", false);
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ICONST_0);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, ingredient, "m_288218_",
                "(L" + json + ";Z)L" + ingredient + ";", false);
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, ingredient, "m_43923_", "(L" + buffer + ";)V", false);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, ingredient, "m_43940_",
                "(L" + buffer + ";)L" + ingredient + ";", false);
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, ingredient, "m_43942_", "()L" + json + ";", false);
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyIngredientCodec", writer.toByteArray())).accept(transformed, 0);
        var calls = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        assertEquals(List.of("fromJson", "fromJson", "toNetwork", "fromNetwork", "toJson"),
                calls.stream().map(call -> call.name).toList());
        assertTrue(calls.stream().allMatch(call -> call.owner.equals(
                "net/neoforged/forgestuck/compat/LegacyIngredientBridge")
                && call.getOpcode() == Opcodes.INVOKESTATIC));
        assertEquals("(L" + json + ";)L" + ingredient + ";", calls.get(0).desc);
        assertEquals("(L" + json + ";Z)L" + ingredient + ";", calls.get(1).desc);
        assertEquals("(L" + ingredient + ";L" + buffer + ";)V", calls.get(2).desc);
        assertEquals("(L" + buffer + ";)L" + ingredient + ";", calls.get(3).desc);
        assertEquals("(L" + ingredient + ";)L" + json + ";", calls.get(4).desc);
    }

    @Test
    void bridgesLegacyForestryRecipeMethodsToTheCurrentRecipeApi() {
        String recipeInterface = "forestry/api/recipes/IForestryRecipe";
        String recipeType = "net/minecraft/world/item/crafting/RecipeType";
        String serializer = "net/minecraft/world/item/crafting/RecipeSerializer";
        String registryAccess = "net/minecraft/core/RegistryAccess";
        String holderProvider = "net/minecraft/core/HolderLookup$Provider";
        String itemStack = "net/minecraft/world/item/ItemStack";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/GendustryProcessorRecipe", null,
                "java/lang/Object", new String[] {recipeInterface});
        var typeMethod = writer.visitMethod(Opcodes.ACC_PUBLIC, "m_6671_", "()L" + recipeType + ";", null, null);
        typeMethod.visitCode();
        typeMethod.visitInsn(Opcodes.ACONST_NULL);
        typeMethod.visitInsn(Opcodes.ARETURN);
        typeMethod.visitMaxs(1, 1);
        typeMethod.visitEnd();
        var serializerMethod = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "m_7707_", "()L" + serializer + ";", null, null);
        serializerMethod.visitCode();
        serializerMethod.visitInsn(Opcodes.ACONST_NULL);
        serializerMethod.visitInsn(Opcodes.ARETURN);
        serializerMethod.visitMaxs(1, 1);
        serializerMethod.visitEnd();
        var resultMethod = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "m_8043_", "(L" + registryAccess + ";)L" + itemStack + ";", null, null);
        resultMethod.visitCode();
        resultMethod.visitInsn(Opcodes.ACONST_NULL);
        resultMethod.visitInsn(Opcodes.ARETURN);
        resultMethod.visitMaxs(1, 2);
        resultMethod.visitEnd();
        writer.visitEnd();

        SrgMemberRemapStep step = new SrgMemberRemapStep();
        step.scan("sample.GendustryProcessorRecipe", writer.toByteArray(), null);
        ClassNode transformed = new ClassNode();
        new ClassReader(step.apply("sample.GendustryProcessorRecipe", writer.toByteArray()))
                .accept(transformed, 0);
        assertTrue(transformed.methods.stream().anyMatch(method ->
                method.name.equals("getType") && method.desc.equals("()L" + recipeType + ";")));
        assertTrue(transformed.methods.stream().anyMatch(method ->
                method.name.equals("getSerializer") && method.desc.equals("()L" + serializer + ";")));
        MethodNode resultBridge = transformed.methods.stream()
                .filter(method -> method.name.equals("getResultItem")
                        && method.desc.equals("(L" + holderProvider + ";)L" + itemStack + ";")
                        && (method.access & Opcodes.ACC_SYNTHETIC) != 0)
                .findFirst().orElseThrow();
        var instructions = resultBridge.instructions.toArray();
        assertTrue(java.util.Arrays.stream(instructions)
                .anyMatch(instruction -> instruction.getOpcode() == Opcodes.CHECKCAST));
        assertTrue(java.util.Arrays.stream(instructions)
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .anyMatch(call -> call.name.equals("m_8043_")
                        && call.desc.equals("(L" + registryAccess + ";)L" + itemStack + ";")));
    }

    @Test
    void bridgesRecipeMethodsDeclaredThroughAnIndirectSuperclass() {
        String recipe = "net/minecraft/world/item/crafting/Recipe";
        String recipeType = "net/minecraft/world/item/crafting/RecipeType";
        ClassWriter baseWriter = new ClassWriter(0);
        baseWriter.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/RecipeBase", null, "java/lang/Object",
                new String[] {recipe});
        baseWriter.visitEnd();
        ClassWriter recipeWriter = new ClassWriter(0);
        recipeWriter.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/PulverizerRecipe", null,
                "sample/RecipeBase", null);
        var legacyMethod = recipeWriter.visitMethod(Opcodes.ACC_PUBLIC, "m_6671_",
                "()L" + recipeType + ";", null, null);
        legacyMethod.visitCode();
        legacyMethod.visitInsn(Opcodes.ACONST_NULL);
        legacyMethod.visitInsn(Opcodes.ARETURN);
        legacyMethod.visitMaxs(1, 1);
        legacyMethod.visitEnd();
        recipeWriter.visitEnd();

        SrgMemberRemapStep step = new SrgMemberRemapStep();
        step.scan("sample.RecipeBase", baseWriter.toByteArray(), null);
        step.scan("sample.PulverizerRecipe", recipeWriter.toByteArray(), null);
        ClassNode transformed = new ClassNode();
        new ClassReader(step.apply("sample.PulverizerRecipe", recipeWriter.toByteArray())).accept(transformed, 0);
        MethodNode bridge = transformed.methods.stream()
                .filter(method -> method.name.equals("getType") && method.desc.equals("()L" + recipeType + ";"))
                .findFirst().orElseThrow();
        MethodInsnNode delegate = java.util.Arrays.stream(bridge.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals("m_6671_", delegate.name);
        assertEquals("sample/PulverizerRecipe", delegate.owner);
    }

    @Test
    void bridgesLegacyMenuProviderMethodInheritedThroughModSuperclass() {
        String descriptor = "(ILnet/minecraft/world/entity/player/Inventory;"
                + "Lnet/minecraft/world/entity/player/Player;)"
                + "Lnet/minecraft/world/inventory/AbstractContainerMenu;";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/IndustrialApiary", null,
                "forestry/core/tiles/TileBase", null);
        var createMenu = writer.visitMethod(Opcodes.ACC_PUBLIC, "m_7208_", descriptor, null, null);
        createMenu.visitCode();
        createMenu.visitInsn(Opcodes.ACONST_NULL);
        createMenu.visitInsn(Opcodes.ARETURN);
        createMenu.visitMaxs(1, 4);
        createMenu.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.IndustrialApiary", writer.toByteArray()))
                .accept(transformed, 0);
        MethodNode bridge = transformed.methods.stream()
                .filter(method -> method.name.equals("createMenu") && method.desc.equals(descriptor))
                .findFirst().orElseThrow();
        MethodInsnNode delegate = java.util.Arrays.stream(bridge.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals("m_7208_", delegate.name);
        assertEquals("sample/IndustrialApiary", delegate.owner);
    }

    @Test
    void bridgesLegacyEntityBlockMethodsInheritedThroughModSuperclass() {
        String entityBlock = "net/minecraft/world/level/block/EntityBlock";
        String factoryDescriptor = "(Lnet/minecraft/core/BlockPos;"
                + "Lnet/minecraft/world/level/block/state/BlockState;)"
                + "Lnet/minecraft/world/level/block/entity/BlockEntity;";
        String tickerDescriptor = "(Lnet/minecraft/world/level/Level;"
                + "Lnet/minecraft/world/level/block/state/BlockState;"
                + "Lnet/minecraft/world/level/block/entity/BlockEntityType;)"
                + "Lnet/minecraft/world/level/block/entity/BlockEntityTicker;";

        ClassWriter baseWriter = new ClassWriter(0);
        baseWriter.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
                "sample/EntityBlockBase", null, "java/lang/Object", new String[] {entityBlock});
        baseWriter.visitEnd();
        ClassWriter childWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        childWriter.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyController", null,
                "sample/EntityBlockBase", null);
        var factory = childWriter.visitMethod(Opcodes.ACC_PUBLIC, "m_142194_", factoryDescriptor, null, null);
        factory.visitCode();
        factory.visitInsn(Opcodes.ACONST_NULL);
        factory.visitInsn(Opcodes.ARETURN);
        factory.visitMaxs(0, 0);
        factory.visitEnd();
        var ticker = childWriter.visitMethod(Opcodes.ACC_PUBLIC, "m_142354_", tickerDescriptor, null, null);
        ticker.visitCode();
        ticker.visitInsn(Opcodes.ACONST_NULL);
        ticker.visitInsn(Opcodes.ARETURN);
        ticker.visitMaxs(0, 0);
        ticker.visitEnd();
        childWriter.visitEnd();

        SrgMemberRemapStep step = new SrgMemberRemapStep();
        step.scan("sample.EntityBlockBase", baseWriter.toByteArray(), null);
        step.scan("sample.LegacyController", childWriter.toByteArray(), null);
        ClassNode transformed = new ClassNode();
        new ClassReader(step.apply("sample.LegacyController", childWriter.toByteArray()))
                .accept(transformed, 0);

        assertTrue(transformed.methods.stream().anyMatch(method ->
                method.name.equals("newBlockEntity") && method.desc.equals(factoryDescriptor)));
        assertTrue(transformed.methods.stream().anyMatch(method ->
                method.name.equals("getTicker") && method.desc.equals(tickerDescriptor)));
        for (String legacyName : List.of("m_142194_", "m_142354_")) {
            assertTrue(transformed.methods.stream().anyMatch(method -> method.name.equals(legacyName)));
        }
    }

    @Test
    void remapsCallsToLegacyMethodsInheritedFromUnscannedModSuperclass() {
        String apiary = "sample/IndustrialApiary";
        String level = "net/minecraft/world/level/Level";
        ClassWriter apiaryWriter = new ClassWriter(0);
        apiaryWriter.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, apiary, null,
                "forestry/core/tiles/TileBase", null);
        apiaryWriter.visitEnd();

        ClassWriter menuWriter = new ClassWriter(0);
        menuWriter.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/IndustrialApiaryMenu", null,
                "java/lang/Object", null);
        var method = menuWriter.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "getLevel",
                "(L" + apiary + ";)L" + level + ";", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, apiary, "m_58904_", "()L" + level + ";", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        menuWriter.visitEnd();

        SrgMemberRemapStep step = new SrgMemberRemapStep();
        step.scan(apiary, apiaryWriter.toByteArray(), null);
        ClassNode transformed = new ClassNode();
        new ClassReader(step.apply("sample.IndustrialApiaryMenu", menuWriter.toByteArray()))
                .accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals("getLevel", call.name);
    }

    @Test
    void remapsItemDefaultInstanceOnExternalItemSubclass() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/DnaExtractorRecipeCategory", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "labware",
                "(Lforestry/core/platform/item/ItemForestry;)Lnet/minecraft/world/item/ItemStack;",
                null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "forestry/core/platform/item/ItemForestry",
                "m_7968_", "()Lnet/minecraft/world/item/ItemStack;", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.DnaExtractorRecipeCategory", writer.toByteArray())).accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals("forestry/core/platform/item/ItemForestry", call.owner);
        assertEquals("getDefaultInstance", call.name);
        assertEquals("()Lnet/minecraft/world/item/ItemStack;", call.desc);
    }

    @Test
    void recreatesLegacyTypedCraftingBookCategoryCodec() {
        String category = "net/minecraft/world/item/crafting/CraftingBookCategory";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/RecipeCategoryCodec", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "codec",
                "()Lnet/minecraft/util/StringRepresentable$EnumCodec;", null, null);
        method.visitCode();
        method.visitFieldInsn(Opcodes.GETSTATIC, category, "f_244644_",
                "Lnet/minecraft/util/StringRepresentable$EnumCodec;");
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.RecipeCategoryCodec", writer.toByteArray()))
                .accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals("net/neoforged/forgestuck/compat/LegacyEnumCodecs", call.owner);
        assertEquals("craftingBookCategoryCodec", call.name);
        assertEquals("()Lnet/minecraft/util/StringRepresentable$EnumCodec;", call.desc);
    }

    @Test
    void bridgesLegacySimpleGeometryAddQuadsModelNameParameter() {
        String simpleGeometry = "net/neoforged/neoforge/client/model/geometry/SimpleUnbakedGeometry";
        String context = "net/neoforged/neoforge/client/model/geometry/IGeometryBakingContext";
        String modelBuilder = "net/neoforged/neoforge/client/model/IModelBuilder";
        String baker = "net/minecraft/client/resources/model/ModelBaker";
        String modelState = "net/minecraft/client/resources/model/ModelState";
        String resourceLocation = "net/minecraft/resources/ResourceLocation";
        String legacyDescriptor = "(L" + context + ";L" + modelBuilder + ";L" + baker
                + ";Ljava/util/function/Function;L" + modelState + ";L" + resourceLocation + ";)V";
        String targetDescriptor = "(L" + context + ";L" + modelBuilder + ";L" + baker
                + ";Ljava/util/function/Function;L" + modelState + ";)V";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacySimpleGeometry", null,
                simpleGeometry, null);
        var legacy = writer.visitMethod(Opcodes.ACC_PUBLIC, "addQuads", legacyDescriptor, null, null);
        legacy.visitCode();
        legacy.visitInsn(Opcodes.RETURN);
        legacy.visitMaxs(0, 7);
        legacy.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacySimpleGeometry", writer.toByteArray())).accept(transformed, 0);
        MethodNode bridge = transformed.methods.stream()
                .filter(method -> method.name.equals("addQuads") && method.desc.equals(targetDescriptor))
                .findFirst().orElseThrow();
        var calls = java.util.Arrays.stream(bridge.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        assertEquals(3, calls.size());
        assertEquals("getModelName", calls.get(0).name);
        assertEquals("parse", calls.get(1).name);
        assertEquals("addQuads", calls.get(2).name);
        assertEquals(legacyDescriptor, calls.get(2).desc);
    }

    @Test
    void remapsLegacyBlockElementFaceDataAccessor() {
        String face = "net/minecraft/client/renderer/block/model/BlockElementFace";
        String data = "net/neoforged/neoforge/client/model/ExtraFaceData";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyFaceData", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "blockLight",
                "(L" + face + ";)I", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, face, "getFaceData", "()L" + data + ";", false);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, data, "blockLight", "()I", false);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyFaceData", writer.toByteArray()))
                .accept(transformed, 0);
        var calls = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        assertEquals(2, calls.size());
        assertEquals(face, calls.get(0).owner);
        assertEquals("faceData", calls.get(0).name);
        assertEquals("()L" + data + ";", calls.get(0).desc);
        assertEquals("blockLight", calls.get(1).name);
    }

    @Test
    void normalizesLegacyTextureAtlasSpriteUvArguments() {
        String sprite = "net/minecraft/client/renderer/texture/TextureAtlasSprite";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacySpriteUv", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "uv",
                "(L" + sprite + ";D)F", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.DLOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, sprite, "m_111392_", "(D)F", false);
        method.visitInsn(Opcodes.POP);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.DLOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, sprite, "getV", "(D)F", false);
        method.visitInsn(Opcodes.FRETURN);
        method.visitMaxs(0, 3);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        LegacyNameMappings mappings = new LegacyNameMappings(Map.of("m_111392_", "getU"), Set.of());
        new ClassReader(new SrgMemberRemapStep(mappings).apply("sample.LegacySpriteUv", writer.toByteArray()))
                .accept(transformed, 0);
        var instructions = transformed.methods.get(0).instructions.toArray();
        var calls = java.util.Arrays.stream(instructions)
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        assertEquals(2, calls.size());
        assertEquals("getU", calls.get(0).name);
        assertEquals("(F)F", calls.get(0).desc);
        assertEquals("getV", calls.get(1).name);
        assertEquals("(F)F", calls.get(1).desc);
        for (MethodInsnNode call : calls) {
            int index = java.util.Arrays.asList(instructions).indexOf(call);
            assertEquals(Opcodes.FMUL, instructions[index - 1].getOpcode());
            assertEquals(Float.valueOf(0.0625f),
                    ((org.objectweb.asm.tree.LdcInsnNode) instructions[index - 2]).cst);
            assertEquals(Opcodes.D2F, instructions[index - 3].getOpcode());
        }
    }

    @Test
    void bridgesLegacyBlockModelBakeFaceSignature() {
        String blockModel = "net/minecraft/client/renderer/block/model/BlockModel";
        String element = "net/minecraft/client/renderer/block/model/BlockElement";
        String face = "net/minecraft/client/renderer/block/model/BlockElementFace";
        String sprite = "net/minecraft/client/renderer/texture/TextureAtlasSprite";
        String direction = "net/minecraft/core/Direction";
        String modelState = "net/minecraft/client/resources/model/ModelState";
        String resourceLocation = "net/minecraft/resources/ResourceLocation";
        String quad = "net/minecraft/client/renderer/block/model/BakedQuad";
        String legacyDescriptor = "(L" + element + ";L" + face + ";L" + sprite + ";L" + direction
                + ";L" + modelState + ";L" + resourceLocation + ";)L" + quad + ";";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyBakeFace", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "bake", legacyDescriptor, null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitVarInsn(Opcodes.ALOAD, 3);
        method.visitVarInsn(Opcodes.ALOAD, 4);
        method.visitVarInsn(Opcodes.ALOAD, 5);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, blockModel, "m_111437_", legacyDescriptor, false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 6);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyBakeFace", writer.toByteArray()))
                .accept(transformed, 0);
        var instructions = transformed.methods.get(0).instructions.toArray();
        var call = java.util.Arrays.stream(instructions)
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(blockModel, call.owner);
        assertEquals("bakeFace", call.name);
        assertEquals("(L" + element + ";L" + face + ";L" + sprite + ";L" + direction
                + ";L" + modelState + ";)L" + quad + ";", call.desc);
        assertEquals(1, java.util.Arrays.stream(instructions)
                .filter(instruction -> instruction.getOpcode() == Opcodes.POP).count());
    }

    @Test
    void bridgesLegacyFaceBakeryUvLockHelper() {
        String faceBakery = "net/minecraft/client/renderer/block/model/FaceBakery";
        String uv = "net/minecraft/client/renderer/block/model/BlockFaceUV";
        String direction = "net/minecraft/core/Direction";
        String transformation = "com/mojang/math/Transformation";
        String resourceLocation = "net/minecraft/resources/ResourceLocation";
        String legacyDescriptor = "(L" + uv + ";L" + direction + ";L" + transformation
                + ";L" + resourceLocation + ";)L" + uv + ";";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyUvLock", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "recompute",
                legacyDescriptor, null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitVarInsn(Opcodes.ALOAD, 3);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, faceBakery, "m_111581_", legacyDescriptor, false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 4);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyUvLock", writer.toByteArray()))
                .accept(transformed, 0);
        var instructions = transformed.methods.get(0).instructions.toArray();
        var call = java.util.Arrays.stream(instructions)
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(faceBakery, call.owner);
        assertEquals("recomputeUVs", call.name);
        assertEquals("(L" + uv + ";L" + direction + ";L" + transformation + ";)L" + uv + ";", call.desc);
        assertEquals(1, java.util.Arrays.stream(instructions)
                .filter(instruction -> instruction.getOpcode() == Opcodes.POP).count());
    }

    @Test
    void bridgesLegacyPostChainResourceManagerConstructor() {
        String postChain = "net/minecraft/client/renderer/PostChain";
        String textureManager = "net/minecraft/client/renderer/texture/TextureManager";
        String resourceManager = "net/minecraft/server/packs/resources/ResourceManager";
        String resourceProvider = "net/minecraft/server/packs/resources/ResourceProvider";
        String renderTarget = "com/mojang/blaze3d/pipeline/RenderTarget";
        String resourceLocation = "net/minecraft/resources/ResourceLocation";
        String legacyDescriptor = "(L" + textureManager + ";L" + resourceManager + ";L" + renderTarget
                + ";L" + resourceLocation + ";)V";
        String targetDescriptor = "(L" + textureManager + ";L" + resourceProvider + ";L" + renderTarget
                + ";L" + resourceLocation + ";)V";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyPostChain", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "m_6213_",
                "(L" + textureManager + ";L" + resourceManager + ";L" + renderTarget + ";L"
                        + resourceLocation + ";)L" + postChain + ";",
                null, null);
        method.visitCode();
        method.visitTypeInsn(Opcodes.NEW, postChain);
        method.visitInsn(Opcodes.DUP);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitVarInsn(Opcodes.ALOAD, 3);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, postChain, "<init>", legacyDescriptor, false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 4);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyPostChain", writer.toByteArray()))
                .accept(transformed, 0);
        var call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESPECIAL, call.getOpcode());
        assertEquals(postChain, call.owner);
        assertEquals("<init>", call.name);
        assertEquals(targetDescriptor, call.desc);
    }

    @Test
    void bridgesLegacyTextColorParseReturnType() {
        String textColor = "net/minecraft/network/chat/TextColor";
        String legacyDescriptor = "(Ljava/lang/String;)L" + textColor + ";";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyTextColor", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "m_131268_",
                "(Ljava/lang/String;)L" + textColor + ";", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, textColor, "parseColor", legacyDescriptor, false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyTextColor", writer.toByteArray()))
                .accept(transformed, 0);
        var call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyTextColorShim", call.owner);
        assertEquals("parseColor", call.name);
        assertEquals(legacyDescriptor, call.desc);
    }

    @Test
    void adaptsLegacyPlacementModifierCodecLambda() {
        String placementModifierType = "net/minecraft/world/level/levelgen/placement/PlacementModifierType";
        String codecDescriptor = "()Lcom/mojang/serialization/Codec;";
        String typeDescriptor = "()L" + placementModifierType + ";";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyPlacementType", null, "java/lang/Object", null);
        var codec = writer.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                "lambda$codec", codecDescriptor, null, null);
        codec.visitCode();
        codec.visitInsn(Opcodes.ACONST_NULL);
        codec.visitInsn(Opcodes.ARETURN);
        codec.visitMaxs(1, 0);
        codec.visitEnd();
        var type = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "m_191869_", typeDescriptor, null, null);
        type.visitCode();
        type.visitInvokeDynamicInsn("m_191869_", typeDescriptor,
                new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                                + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;"
                                + "Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                        false),
                Type.getMethodType(codecDescriptor),
                new Handle(Opcodes.H_INVOKESTATIC, "sample/LegacyPlacementType", "lambda$codec",
                        codecDescriptor, false),
                Type.getMethodType(codecDescriptor));
        type.visitInsn(Opcodes.ARETURN);
        type.visitMaxs(1, 0);
        type.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyPlacementType", writer.toByteArray())).accept(transformed, 0);
        var calls = java.util.Arrays.stream(transformed.methods.stream()
                .filter(method -> method.name.equals("m_191869_")).findFirst().orElseThrow()
                .instructions.toArray()).filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast).toList();
        assertEquals(2, calls.size());
        assertEquals("sample/LegacyPlacementType", calls.get(0).owner);
        assertEquals("lambda$codec", calls.get(0).name);
        assertEquals(codecDescriptor, calls.get(0).desc);
        assertEquals("net/neoforged/forgestuck/compat/LegacyPlacementModifierType", calls.get(1).owner);
        assertEquals("adapt", calls.get(1).name);
        assertEquals("(Lcom/mojang/serialization/Codec;)L" + placementModifierType + ";", calls.get(1).desc);
        assertFalse(java.util.Arrays.stream(transformed.methods.stream()
                .filter(method -> method.name.equals("m_191869_")).findFirst().orElseThrow()
                .instructions.toArray()).anyMatch(org.objectweb.asm.tree.InvokeDynamicInsnNode.class::isInstance));
    }

    @Test
    void adaptsLegacyStructureTypeCodecLambda() {
        String structureType = "net/minecraft/world/level/levelgen/structure/StructureType";
        String codecDescriptor = "()Lcom/mojang/serialization/Codec;";
        String typeDescriptor = "()L" + structureType + ";";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyStructureTypeFactory", null,
                "java/lang/Object", null);
        var codec = writer.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                "lambda$codec", codecDescriptor, null, null);
        codec.visitCode();
        codec.visitInsn(Opcodes.ACONST_NULL);
        codec.visitInsn(Opcodes.ARETURN);
        codec.visitMaxs(1, 0);
        codec.visitEnd();
        var type = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "m_191869_", typeDescriptor, null,
                null);
        type.visitCode();
        type.visitInvokeDynamicInsn("m_191869_", typeDescriptor,
                new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                                + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;"
                                + "Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                        false),
                Type.getMethodType(codecDescriptor),
                new Handle(Opcodes.H_INVOKESTATIC, "sample/LegacyStructureTypeFactory", "lambda$codec",
                        codecDescriptor, false),
                Type.getMethodType(codecDescriptor));
        type.visitInsn(Opcodes.ARETURN);
        type.visitMaxs(1, 0);
        type.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyStructureTypeFactory", writer.toByteArray())).accept(transformed, 0);
        var calls = java.util.Arrays.stream(transformed.methods.stream()
                .filter(method -> method.name.equals("m_191869_")).findFirst().orElseThrow()
                .instructions.toArray()).filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast).toList();
        assertEquals(2, calls.size());
        assertEquals("sample/LegacyStructureTypeFactory", calls.get(0).owner);
        assertEquals("lambda$codec", calls.get(0).name);
        assertEquals(codecDescriptor, calls.get(0).desc);
        assertEquals("net/neoforged/forgestuck/compat/LegacyStructureType", calls.get(1).owner);
        assertEquals("adapt", calls.get(1).name);
        assertEquals("(Lcom/mojang/serialization/Codec;)L" + structureType + ";", calls.get(1).desc);
        assertFalse(java.util.Arrays.stream(transformed.methods.stream()
                .filter(method -> method.name.equals("m_191869_")).findFirst().orElseThrow()
                .instructions.toArray()).anyMatch(org.objectweb.asm.tree.InvokeDynamicInsnNode.class::isInstance));
    }

    @Test
    void preservesLegacyImmutableVertexFormatElementsReturnType() {
        String vertexFormat = "com/mojang/blaze3d/vertex/VertexFormat";
        String immutableList = "com/google/common/collect/ImmutableList";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyVertexFormat", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "elements",
                "(L" + vertexFormat + ";)L" + immutableList + ";", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, vertexFormat, "m_111436_",
                "()L" + immutableList + ";", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        LegacyNameMappings mappings = new LegacyNameMappings(Map.of("m_111436_", "getElements"), Set.of());
        new ClassReader(new SrgMemberRemapStep(mappings).apply("sample.LegacyVertexFormat", writer.toByteArray()))
                .accept(transformed, 0);
        var call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyVertexFormatShim", call.owner);
        assertEquals("getElements", call.name);
        assertEquals("(L" + vertexFormat + ";)L" + immutableList + ";", call.desc);
    }

    @Test
    void remapsLegacyVertexFormatElementRecordAccessors() {
        String element = "com/mojang/blaze3d/vertex/VertexFormatElement";
        String type = element + "$Type";
        String usage = element + "$Usage";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyVertexFormatElement", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "usage",
                "(L" + element + ";)L" + usage + ";", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, element, "m_86048_", "()L" + usage + ";", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        var count = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "count",
                "(L" + element + ";)I", null, null);
        count.visitCode();
        count.visitVarInsn(Opcodes.ALOAD, 0);
        count.visitMethodInsn(Opcodes.INVOKEVIRTUAL, element, "getElementCount", "()I", false);
        count.visitInsn(Opcodes.IRETURN);
        count.visitMaxs(1, 1);
        count.visitEnd();
        var typeMethod = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "type",
                "(L" + element + ";)L" + type + ";", null, null);
        typeMethod.visitCode();
        typeMethod.visitVarInsn(Opcodes.ALOAD, 0);
        typeMethod.visitMethodInsn(Opcodes.INVOKEVIRTUAL, element, "m_86041_", "()L" + type + ";", false);
        typeMethod.visitInsn(Opcodes.ARETURN);
        typeMethod.visitMaxs(1, 1);
        typeMethod.visitEnd();
        writer.visitEnd();

        LegacyNameMappings mappings = new LegacyNameMappings(Map.of(
                "m_86041_", "type",
                "m_86048_", "usage"), Set.of());
        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep(mappings)
                .apply("sample.LegacyVertexFormatElement", writer.toByteArray())).accept(transformed, 0);
        var calls = transformed.methods.stream()
                .flatMap(methodNode -> java.util.Arrays.stream(methodNode.instructions.toArray()))
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        assertEquals(List.of("usage", "count", "type"), calls.stream().map(call -> call.name).toList());
    }

    @Test
    void bridgesLegacyUnbakedGeometryBakeSignature() {
        String geometry = "net/neoforged/neoforge/client/model/geometry/IUnbakedGeometry";
        String context = "net/neoforged/neoforge/client/model/geometry/IGeometryBakingContext";
        String baker = "net/minecraft/client/resources/model/ModelBaker";
        String modelState = "net/minecraft/client/resources/model/ModelState";
        String overrides = "net/minecraft/client/renderer/block/model/ItemOverrides";
        String bakedModel = "net/minecraft/client/resources/model/BakedModel";
        String resourceLocation = "net/minecraft/resources/ResourceLocation";
        String targetDescriptor = "(L" + context + ";L" + baker + ";Ljava/util/function/Function;"
                + "L" + modelState + ";L" + overrides + ";)L" + bakedModel + ";";
        String legacyDescriptor = "(L" + context + ";L" + baker + ";Ljava/util/function/Function;"
                + "L" + modelState + ";L" + overrides + ";L" + resourceLocation + ";)L" + bakedModel + ";";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyGeometry", null,
                "java/lang/Object", new String[] {geometry});
        var legacyBake = writer.visitMethod(Opcodes.ACC_PUBLIC, "bake", legacyDescriptor, null, null);
        legacyBake.visitCode();
        legacyBake.visitInsn(Opcodes.ACONST_NULL);
        legacyBake.visitInsn(Opcodes.ARETURN);
        legacyBake.visitMaxs(1, 7);
        legacyBake.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyGeometry", writer.toByteArray())).accept(transformed, 0);
        MethodNode bridge = transformed.methods.stream()
                .filter(method -> method.name.equals("bake") && method.desc.equals(targetDescriptor))
                .findFirst().orElseThrow();
        var calls = java.util.Arrays.stream(bridge.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        assertEquals(3, calls.size());
        assertEquals(Opcodes.INVOKEINTERFACE, calls.get(0).getOpcode());
        assertEquals(context, calls.get(0).owner);
        assertEquals("getModelName", calls.get(0).name);
        assertEquals("()Ljava/lang/String;", calls.get(0).desc);
        assertEquals(Opcodes.INVOKESTATIC, calls.get(1).getOpcode());
        assertEquals(resourceLocation, calls.get(1).owner);
        assertEquals("parse", calls.get(1).name);
        assertEquals(Opcodes.INVOKEVIRTUAL, calls.get(2).getOpcode());
        assertEquals("bake", calls.get(2).name);
        assertEquals(legacyDescriptor, calls.get(2).desc);
    }

    @Test
    void adaptsThermalDynamicsDebugBuffersToSequencedByteBuffers() {
        String byteBufferBuilder = "com/mojang/blaze3d/vertex/ByteBufferBuilder";
        String multiBufferSource = "net/minecraft/client/renderer/MultiBufferSource";
        String bufferSource = multiBufferSource + "$BufferSource";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "cofh/thermal/dynamics/client/DebugRenderer", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "buffers",
                "(Ljava/util/Map;)L" + bufferSource + ";", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitIntInsn(Opcodes.SIPUSH, 256);
        method.visitMethodInsn(Opcodes.INVOKESTATIC,
                "net/neoforged/forgestuck/compat/LegacyBufferBuilders", "create",
                "(I)Lcom/mojang/blaze3d/vertex/BufferBuilder;", false);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, multiBufferSource, "m_109900_",
                "(Ljava/util/Map;Lcom/mojang/blaze3d/vertex/BufferBuilder;)L" + bufferSource + ";", true);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(4, 1);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "cofh.thermal.dynamics.client.DebugRenderer", writer.toByteArray())).accept(transformed, 0);
        var calls = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        assertEquals("net/neoforged/forgestuck/compat/LegacyMultiBufferSource", calls.get(0).owner);
        assertEquals("createByteBufferBuilder", calls.get(0).name);
        assertEquals("(I)L" + byteBufferBuilder + ";", calls.get(0).desc);
        assertEquals(Opcodes.INVOKESTATIC, calls.get(1).getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyMultiBufferSource", calls.get(1).owner);
        assertEquals("(Ljava/util/Map;L" + byteBufferBuilder + ";)L" + bufferSource + ";", calls.get(1).desc);
    }

    @Test
    void marksDataResultStaticCallsAsInterfaceReferences() {
        String dataResult = "com/mojang/serialization/DataResult";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyDataResultCall", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "success",
                "()L" + dataResult + ";", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, dataResult, "success",
                "(Ljava/lang/Object;)L" + dataResult + ";", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyDataResultCall", writer.toByteArray())).accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertTrue(call.itf);
    }

    @Test
    void rewritesLegacyVertexConsumerCallsAndHandles() {
        String vertexConsumer = "com/mojang/blaze3d/vertex/VertexConsumer";
        String shim = "net/neoforged/forgestuck/compat/LegacyVertexConsumerShim";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "example/LegacyVertexCalls", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "emit",
                "(L" + vertexConsumer + ";)V", null, null);
        method.visitCode();
        addVertexCall(method, vertexConsumer, "m_5483_", "(DDD)L" + vertexConsumer + ";",
                Opcodes.DCONST_0, Opcodes.DCONST_0, Opcodes.DCONST_0);
        addVertexCall(method, vertexConsumer, "m_193479_", "(I)L" + vertexConsumer + ";",
                Opcodes.ICONST_0);
        addVertexCall(method, vertexConsumer, "m_6122_", "(IIII)L" + vertexConsumer + ";",
                Opcodes.ICONST_0, Opcodes.ICONST_0, Opcodes.ICONST_0, Opcodes.ICONST_0);
        addVertexCall(method, vertexConsumer, "m_5601_", "(FFF)L" + vertexConsumer + ";",
                Opcodes.FCONST_0, Opcodes.FCONST_0, Opcodes.FCONST_0);
        addVertexCall(method, vertexConsumer, "m_7421_", "(FF)L" + vertexConsumer + ";",
                Opcodes.FCONST_0, Opcodes.FCONST_0);
        addVertexCall(method, vertexConsumer, "m_7122_", "(II)L" + vertexConsumer + ";",
                Opcodes.ICONST_0, Opcodes.ICONST_0);
        addVertexCall(method, vertexConsumer, "m_7120_", "(II)L" + vertexConsumer + ";",
                Opcodes.ICONST_0, Opcodes.ICONST_0);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, vertexConsumer, "m_5752_", "()V", true);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(8, 1);
        method.visitEnd();
        var handleMethod = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "handle",
                "()Ljava/lang/invoke/MethodHandle;", null, null);
        handleMethod.visitCode();
        handleMethod.visitLdcInsn(new Handle(Opcodes.H_INVOKEINTERFACE, vertexConsumer,
                "m_5483_", "(DDD)L" + vertexConsumer + ";", true));
        handleMethod.visitInsn(Opcodes.ARETURN);
        handleMethod.visitMaxs(1, 0);
        handleMethod.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("example.LegacyVertexCalls", writer.toByteArray()))
                .accept(transformed, 0);
        var calls = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        assertEquals(8, calls.size());
        assertEquals(java.util.List.of(
                        "vertex", "color", "color", "normal", "uv", "overlayCoords", "uv2", "endVertex"),
                calls.stream().map(call -> call.name).toList());
        assertTrue(calls.stream().allMatch(call -> call.getOpcode() == Opcodes.INVOKESTATIC
                && call.owner.equals(shim) && !call.itf));
        assertEquals("(L" + vertexConsumer + ";DDD)L" + vertexConsumer + ";", calls.get(0).desc);
        Handle handle = (Handle) ((org.objectweb.asm.tree.LdcInsnNode)
                transformed.methods.get(1).instructions.getFirst()).cst;
        assertEquals(Opcodes.H_INVOKESTATIC, handle.getTag());
        assertEquals(shim, handle.getOwner());
        assertEquals("(L" + vertexConsumer + ";DDD)L" + vertexConsumer + ";", handle.getDesc());
    }

    @Test
    void bridgesTConstructTintedVertexBuilderToModernVertexConsumer() {
        String vertexConsumer = "com/mojang/blaze3d/vertex/VertexConsumer";
        String builder = "slimeknights/tconstruct/smeltery/client/util/TintedVertexBuilder";
        String returnType = "L" + vertexConsumer + ";";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, builder, null, "java/lang/Object",
                new String[] {vertexConsumer});
        String[][] legacyMethods = {
                {"m_5483_", "(DDD)" + returnType},
                {"m_6122_", "(IIII)" + returnType},
                {"m_7421_", "(FF)" + returnType},
                {"m_7122_", "(II)" + returnType},
                {"m_7120_", "(II)" + returnType},
                {"m_5601_", "(FFF)" + returnType},
                {"m_7404_", "(IIII)V"},
                {"m_141991_", "()V"}
        };
        for (String[] legacyMethod : legacyMethods) {
            var method = writer.visitMethod(Opcodes.ACC_PUBLIC, legacyMethod[0], legacyMethod[1], null, null);
            method.visitCode();
            if (legacyMethod[1].endsWith("V")) {
                method.visitInsn(Opcodes.RETURN);
                method.visitMaxs(0, 5);
            } else {
                method.visitInsn(Opcodes.ACONST_NULL);
                method.visitInsn(Opcodes.ARETURN);
                method.visitMaxs(1, 5);
            }
            method.visitEnd();
        }
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(builder, writer.toByteArray())).accept(transformed, 0);
        for (String modernMethod : List.of("addVertex", "setColor", "setUv", "setUv1", "setUv2", "setNormal")) {
            assertTrue(transformed.methods.stream().anyMatch(method -> method.name.equals(modernMethod)
                    && (method.access & Opcodes.ACC_SYNTHETIC) != 0), modernMethod);
        }
        for (String[] legacyDefault : List.of(
                new String[] {"m_7404_", "(IIII)V"},
                new String[] {"m_141991_", "()V"})) {
            var method = transformed.methods.stream().filter(candidate -> candidate.name.equals(legacyDefault[0])
                    && candidate.desc.equals(legacyDefault[1])).findFirst().orElseThrow();
            assertTrue(java.util.Arrays.stream(method.instructions.toArray())
                    .noneMatch(MethodInsnNode.class::isInstance));
        }
        var overlayBridge = transformed.methods.stream().filter(method -> method.name.equals("setUv1"))
                .findFirst().orElseThrow();
        MethodInsnNode legacyOverlay = java.util.Arrays.stream(overlayBridge.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals("m_7122_", legacyOverlay.name);
        assertEquals(builder, legacyOverlay.owner);
    }

    @Test
    void adaptsLegacyLootDataLookupsToReloadableServerRegistries() {
        String server = "net/minecraft/server/MinecraftServer";
        String lootType = "net/minecraft/world/level/storage/loot/LootDataType";
        String holder = "net/minecraft/server/ReloadableServerRegistries$Holder";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "example/LegacyLootCalls", null,
                "java/lang/Object", null);
        var keys = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "keys",
                "(L" + server + ";L" + lootType + ";)Ljava/util/Collection;", null, null);
        keys.visitCode();
        keys.visitVarInsn(Opcodes.ALOAD, 0);
        keys.visitMethodInsn(Opcodes.INVOKEVIRTUAL, server, "m_278653_",
                "()Lnet/minecraft/world/level/storage/loot/LootDataManager;", false);
        keys.visitVarInsn(Opcodes.ALOAD, 1);
        keys.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/storage/loot/LootDataManager",
                "m_278706_", "(L" + lootType + ";)Ljava/util/Collection;", false);
        keys.visitInsn(Opcodes.ARETURN);
        keys.visitMaxs(2, 2);
        keys.visitEnd();
        var directory = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "directory",
                "(L" + lootType + ";)Ljava/lang/String;", null, null);
        directory.visitCode();
        directory.visitVarInsn(Opcodes.ALOAD, 0);
        directory.visitMethodInsn(Opcodes.INVOKEVIRTUAL, lootType, "m_278624_", "()Ljava/lang/String;", false);
        directory.visitInsn(Opcodes.ARETURN);
        directory.visitMaxs(1, 1);
        directory.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("example.LegacyLootCalls", writer.toByteArray()))
                .accept(transformed, 0);
        var keyCalls = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        assertEquals("reloadableRegistries", keyCalls.get(0).name);
        assertEquals("()L" + holder + ";", keyCalls.get(0).desc);
        assertEquals("registryKey", keyCalls.get(1).name);
        assertEquals("getKeys", keyCalls.get(2).name);
        assertEquals("(Lnet/minecraft/resources/ResourceKey;)Ljava/util/Collection;", keyCalls.get(2).desc);
        MethodInsnNode directoryCall = java.util.Arrays.stream(transformed.methods.get(1).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, directoryCall.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyLootDataType", directoryCall.owner);
        assertEquals("(L" + lootType + ";)Ljava/lang/String;", directoryCall.desc);
    }

    @Test
    void adaptsMissingModelLocationRecordToLegacyResourceLocation() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "example/LegacyMissingModel", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "missing",
                "()Lnet/minecraft/resources/ResourceLocation;", null, null);
        method.visitCode();
        method.visitFieldInsn(Opcodes.GETSTATIC,
                "net/minecraft/client/resources/model/ModelBakery", "f_119230_",
                "Lnet/minecraft/client/resources/model/ModelResourceLocation;");
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("example.LegacyMissingModel", writer.toByteArray()))
                .accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyModelResourceLocations", call.owner);
        assertEquals("missingModelLocation", call.name);
        assertEquals("()Lnet/minecraft/resources/ResourceLocation;", call.desc);
    }

    @Test
    void mapsRemovedLegacyGuiIconsFieldToTheGuiAtlas() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "example/LegacyGuiIcons", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "m_123456_",
                "()Lnet/minecraft/resources/ResourceLocation;", null, null);
        method.visitCode();
        method.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/client/gui/Gui", "f_279580_",
                "Lnet/minecraft/resources/ResourceLocation;");
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("example.LegacyGuiIcons", writer.toByteArray()))
                .accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals("net/neoforged/forgestuck/compat/LegacyGuiTexture", call.owner);
        assertEquals("vanillaIcons", call.name);
        assertEquals("()Lnet/minecraft/resources/ResourceLocation;", call.desc);
    }

    @Test
    void repairsVirtualSelfCallsInLegacyInterfaces() {
        String owner = "example/LegacyReloadListener";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT,
                owner, null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "reload", "()V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, "m_123456_", "()V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("example.LegacyReloadListener", writer.toByteArray()))
                .accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKEINTERFACE, call.getOpcode());
        assertTrue(call.itf);
    }

    @Test
    void adaptsPrivateComposterRegistrationMethod() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "example/LegacyComposter", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "m_123456_", "()V", null, null);
        method.visitCode();
        method.visitLdcInsn(0.65F);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/world/level/block/ComposterBlock",
                "m_51920_", "(FLnet/minecraft/world/level/ItemLike;)V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(2, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("example.LegacyComposter", writer.toByteArray()))
                .accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals("net/neoforged/forgestuck/compat/LegacyComposterRegistration", call.owner);
        assertEquals("add", call.name);
    }

    @Test
    void adaptsLegacyCauldronWaterMapField() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "example/LegacyCauldron", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "m_123456_",
                "()Ljava/util/Map;", null, null);
        method.visitCode();
        method.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/core/cauldron/CauldronInteraction",
                "f_175607_", "Ljava/util/Map;");
        method.visitInsn(Opcodes.POP);
        method.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/core/cauldron/CauldronInteraction",
                "f_175606_", "Ljava/util/Map;");
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("example.LegacyCauldron", writer.toByteArray()))
                .accept(transformed, 0);
        var calls = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .toList();
        assertEquals(2, calls.size());
        assertEquals("net/neoforged/forgestuck/compat/LegacyCauldronInteractions", calls.get(0).owner);
        assertEquals("water", calls.get(0).name);
        assertEquals("empty", calls.get(1).name);
        assertEquals("()Ljava/util/Map;", calls.get(0).desc);
    }

    @Test
    void rewritesInheritedLegacyResourceLocationSplitter() {
        String owner = "example/LegacyResourceId";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, owner, null,
                "net/minecraft/resources/ResourceLocation", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "split",
                "(Ljava/lang/String;)[Ljava/lang/String;", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitIntInsn(Opcodes.BIPUSH, ':');
        method.visitMethodInsn(Opcodes.INVOKESTATIC, owner, "m_135832_",
                "(Ljava/lang/String;C)[Ljava/lang/String;", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(2, 1);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("example.LegacyResourceId", writer.toByteArray()))
                .accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyResourceLocations", call.owner);
        assertEquals("split", call.name);
        assertEquals("(Ljava/lang/String;C)[Ljava/lang/String;", call.desc);
    }

    @Test
    void remapsRemovedFireworkShapeEnumToExplosionComponent() {
        String oldShape = "net/minecraft/world/item/FireworkRocketItem$Shape";
        String newShape = "net/minecraft/world/item/component/FireworkExplosion$Shape";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "example/LegacyFireworkShape", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "smallBall",
                "()L" + oldShape + ";", null, null);
        method.visitCode();
        method.visitFieldInsn(Opcodes.GETSTATIC, oldShape, "SMALL_BALL", "L" + oldShape + ";");
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("example.LegacyFireworkShape", writer.toByteArray()))
                .accept(transformed, 0);
        assertEquals("()L" + newShape + ";", transformed.methods.get(0).desc);
        FieldInsnNode field = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(FieldInsnNode.class::isInstance).map(FieldInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(newShape, field.owner);
        assertEquals("L" + newShape + ";", field.desc);
    }

    @Test
    void addsLegacyCoFHArmorAttributeMethodBridge() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "cofh/core/common/item/ArmorItemCoFH", null,
                "net/minecraft/world/item/ArmorItem", null);
        var marker = writer.visitMethod(Opcodes.ACC_PUBLIC, "m_123456_", "()V", null, null);
        marker.visitCode();
        marker.visitInsn(Opcodes.RETURN);
        marker.visitMaxs(0, 1);
        marker.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("cofh.core.common.item.ArmorItemCoFH", writer.toByteArray()))
                .accept(transformed, 0);
        MethodNode bridge = transformed.methods.stream()
                .filter(method -> method.name.equals("m_7167_"))
                .findFirst().orElseThrow();
        assertEquals("(Lnet/minecraft/world/entity/EquipmentSlot;)Lcom/google/common/collect/Multimap;",
                bridge.desc);
        MethodInsnNode call = java.util.Arrays.stream(bridge.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyArmorAttributeModifiers", call.owner);
        assertEquals("get", call.name);
    }

    @Test
    void queuesLegacyPotionBrewingMixesForNeoForgeRegistration() {
        String potion = "net/minecraft/world/item/alchemy/Potion";
        String mix = "net/minecraft/world/item/alchemy/PotionBrewing$Mix";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "example/LegacyPotionBrewing", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "add",
                "()V", null, null);
        method.visitCode();
        method.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/world/item/alchemy/PotionBrewing",
                "f_43494_", "Ljava/util/List;");
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/world/item/alchemy/PotionBrewing",
                "m_43513_", "(L" + potion + ";Lnet/minecraft/world/item/Item;L" + potion + ";)V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(3, 0);
        method.visitEnd();
        var mixType = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "mixType",
                "()L" + mix + ";", null, null);
        mixType.visitCode();
        mixType.visitInsn(Opcodes.ACONST_NULL);
        mixType.visitInsn(Opcodes.ARETURN);
        mixType.visitMaxs(1, 0);
        mixType.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("example.LegacyPotionBrewing", writer.toByteArray()))
                .accept(transformed, 0);
        var methodCalls = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray()).toList();
        FieldInsnNode field = methodCalls.stream().filter(FieldInsnNode.class::isInstance)
                .map(FieldInsnNode.class::cast).findFirst().orElseThrow();
        assertEquals("net/neoforged/forgestuck/compat/LegacyBrewingRecipeRegistry", field.owner);
        assertEquals("LEGACY_MIXES", field.name);
        MethodInsnNode addMix = methodCalls.stream().filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        assertEquals("net/neoforged/forgestuck/compat/LegacyBrewingRecipeRegistry", addMix.owner);
        assertEquals("addPotionMix", addMix.name);
        assertEquals("()Lnet/neoforged/forgestuck/compat/LegacyBrewingRecipeRegistry$LegacyMix;",
                transformed.methods.get(1).desc);
    }

    @Test
    void storesDrillEnchantmentsAsRegistryKeysAndChecksRuntimeKeys() {
        String owner = "cofh/thermal/innovation/common/item/RFDrillItem";
        String enchantments = "net/minecraft/world/item/enchantment/Enchantments";
        String enchantment = "net/minecraft/world/item/enchantment/Enchantment";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        var setup = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "setupEnchants", "()V", null, null);
        setup.visitCode();
        setup.visitFieldInsn(Opcodes.GETSTATIC, owner, "VALID_ENCHANTS", "Ljava/util/Set;");
        setup.visitFieldInsn(Opcodes.GETSTATIC, enchantments, "f_44984_", "L" + enchantment + ";");
        setup.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Set", "add",
                "(Ljava/lang/Object;)Z", true);
        setup.visitInsn(Opcodes.POP);
        setup.visitInsn(Opcodes.RETURN);
        setup.visitMaxs(2, 0);
        setup.visitEnd();
        var check = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "canApplyAtEnchantingTable",
                "(Ljava/util/Set;L" + enchantment + ";)Z", null, null);
        check.visitCode();
        check.visitVarInsn(Opcodes.ALOAD, 0);
        check.visitVarInsn(Opcodes.ALOAD, 1);
        check.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Set", "contains",
                "(Ljava/lang/Object;)Z", true);
        check.visitInsn(Opcodes.IRETURN);
        check.visitMaxs(2, 2);
        check.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("cofh.thermal.innovation.common.item.RFDrillItem",
                writer.toByteArray())).accept(transformed, 0);
        FieldInsnNode keyField = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(FieldInsnNode.class::isInstance).map(FieldInsnNode.class::cast)
                .filter(field -> field.owner.equals(enchantments)).findFirst().orElseThrow();
        assertEquals("EFFICIENCY", keyField.name);
        assertEquals("Lnet/minecraft/resources/ResourceKey;", keyField.desc);
        MethodInsnNode contains = java.util.Arrays.stream(transformed.methods.get(1).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, contains.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyEnchantmentRegistry", contains.owner);
        assertEquals("contains", contains.name);
        assertEquals("(Ljava/util/Set;Ljava/lang/Object;)Z", contains.desc);
    }

    @Test
    void skipsLegacyProtectionSlotMutationForDatapackEnchantments() {
        String owner = "slimeknights/tconstruct/common/config/Config$Common";
        String enchantments = "net/minecraft/world/item/enchantment/Enchantments";
        String enchantment = "net/minecraft/world/item/enchantment/Enchantment";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                "lambda$new$0", "()V", null, null);
        method.visitCode();
        method.visitFieldInsn(Opcodes.GETSTATIC, enchantments, "f_44966_", "L" + enchantment + ";");
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/world/entity/EquipmentSlot", "values",
                "()[Lnet/minecraft/world/entity/EquipmentSlot;", false);
        method.visitFieldInsn(Opcodes.PUTFIELD, enchantment, "f_44671_",
                "[Lnet/minecraft/world/entity/EquipmentSlot;");
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(2, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("slimeknights.tconstruct.common.config.Config$Common",
                writer.toByteArray())).accept(transformed, 0);
        var instructions = transformed.methods.get(0).instructions.toArray();
        assertEquals(1, instructions.length);
        assertEquals(Opcodes.RETURN, instructions[0].getOpcode());
    }

    @Test
    void usesEarlyLootFunctionRegistrationInsteadOfRegisteringAfterFreeze() {
        String owner = "cofh/lib/common/loot/TileNBTSync";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        var setup = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "setup", "()V", null, null);
        setup.visitCode();
        setup.visitInsn(Opcodes.ACONST_NULL);
        setup.visitInsn(Opcodes.ACONST_NULL);
        setup.visitInsn(Opcodes.ACONST_NULL);
        setup.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/core/Registry", "m_122965_",
                "(Lnet/minecraft/core/Registry;Lnet/minecraft/resources/ResourceLocation;"
                        + "Ljava/lang/Object;)Ljava/lang/Object;", true);
        setup.visitInsn(Opcodes.POP);
        setup.visitInsn(Opcodes.RETURN);
        setup.visitMaxs(3, 0);
        setup.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("cofh.lib.common.loot.TileNBTSync", writer.toByteArray()))
                .accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyLootFunctionRegistration", call.owner);
        assertEquals("existing", call.name);
        assertFalse(call.itf);
    }

    @Test
    void replacesLegacyOverlayPostConstructionWithNeoForgePostEvent() {
        String oldPost = "net/minecraftforge/client/event/RenderGuiOverlayEvent$Post";
        String oldPre = "net/minecraftforge/client/event/RenderGuiOverlayEvent$Pre";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "example/LegacyOverlayPost", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "m_123456_",
                "(L" + oldPre + ";)V", null, null);
        method.visitCode();
        method.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraftforge/common/MinecraftForge", "EVENT_BUS",
                "Lnet/minecraftforge/eventbus/api/IEventBus;");
        method.visitTypeInsn(Opcodes.NEW, oldPost);
        method.visitInsn(Opcodes.DUP);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.FCONST_0);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, oldPost, "<init>",
                "(Lcom/mojang/blaze3d/platform/Window;Lnet/minecraft/client/gui/GuiGraphics;F"
                        + "Lnet/minecraftforge/client/gui/overlay/NamedGuiOverlay;)V",
                false);
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE,
                "net/minecraftforge/eventbus/api/IEventBus", "post",
                "(Lnet/minecraftforge/eventbus/api/Event;)Z", true);
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("example.LegacyOverlayPost", writer.toByteArray()))
                .accept(transformed, 0);
        var calls = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        assertEquals(1, calls.size());
        assertEquals("net/neoforged/forgestuck/compat/LegacyGuiOverlayEvent", calls.get(0).owner);
        assertEquals("postAfterHealthbar", calls.get(0).name);
        assertEquals("(Lnet/neoforged/neoforge/client/event/RenderGuiLayerEvent$Pre;)Z", calls.get(0).desc);
        assertTrue(java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .noneMatch(instruction -> instruction instanceof TypeInsnNode type
                        && type.getOpcode() == Opcodes.NEW && type.desc.equals(oldPost)));
    }

    @Test
    void replacesLegacyOverlayPostConstructionAfterEventBusOwnerRemap() {
        String oldPost = "net/minecraftforge/client/event/RenderGuiOverlayEvent$Post";
        String oldPre = "net/minecraftforge/client/event/RenderGuiOverlayEvent$Pre";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "example/RemappedLegacyOverlayPost", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "m_123456_",
                "(L" + oldPre + ";)V", null, null);
        method.visitCode();
        method.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraftforge/common/MinecraftForge", "EVENT_BUS",
                "Lnet/minecraftforge/eventbus/api/IEventBus;");
        method.visitTypeInsn(Opcodes.NEW, oldPost);
        method.visitInsn(Opcodes.DUP);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.FCONST_0);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, oldPost, "<init>",
                "(Lcom/mojang/blaze3d/platform/Window;Lnet/minecraft/client/gui/GuiGraphics;F"
                        + "Lnet/minecraftforge/client/gui/overlay/NamedGuiOverlay;)V",
                false);
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, "net/minecraftforge/eventbus/api/IEventBus", "post",
                "(Lnet/minecraftforge/eventbus/api/Event;)Z", true);
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        byte[] ownerRemapped = new ForgeOwnerRemapStep().apply(
                "example.RemappedLegacyOverlayPost", writer.toByteArray());
        new ClassReader(new SrgMemberRemapStep().apply("example.RemappedLegacyOverlayPost", ownerRemapped))
                .accept(transformed, 0);
        var calls = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        assertEquals(1, calls.size());
        assertEquals("net/neoforged/forgestuck/compat/LegacyGuiOverlayEvent", calls.get(0).owner);
        assertEquals("postAfterHealthbar", calls.get(0).name);
        assertEquals("(Lnet/neoforged/neoforge/client/event/RenderGuiLayerEvent$Pre;)Z", calls.get(0).desc);
        assertTrue(java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .noneMatch(instruction -> instruction instanceof TypeInsnNode type
                        && type.getOpcode() == Opcodes.NEW && type.desc.equals(oldPost)));
    }

    @Test
    void bridgesLegacyGuiOverlayPartialTickAfterOwnerRemap() {
        String oldPost = "net/minecraftforge/client/event/RenderGuiOverlayEvent$Post";
        String targetEvent = "net/neoforged/neoforge/client/event/RenderGuiLayerEvent";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "example/LegacyOverlayPartialTick", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "m_123456_",
                "(L" + oldPost + ";)F", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, oldPost, "getPartialTick", "()F", false);
        method.visitInsn(Opcodes.FRETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();

        byte[] ownerRemapped = new ForgeOwnerRemapStep().apply(
                "example.LegacyOverlayPartialTick", writer.toByteArray());
        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("example.LegacyOverlayPartialTick", ownerRemapped))
                .accept(transformed, 0);
        var call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyGuiOverlayEvent", call.owner);
        assertEquals("getPartialTick", call.name);
        assertEquals("(L" + targetEvent + ";)F", call.desc);
    }

    private static void addVertexCall(org.objectweb.asm.MethodVisitor method, String owner, String name,
            String descriptor,
            int... constants) {
        method.visitVarInsn(Opcodes.ALOAD, 0);
        for (int constant : constants) method.visitInsn(constant);
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, owner, name, descriptor, true);
        method.visitInsn(Opcodes.POP);
    }

    @Test
    void keepsThermalVanillaHolidayDefaultIndependentFromTheFestiveMobsConfigValue() {
        String config = "cofh/thermal/core/common/config/ThermalClientConfig";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, config, null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "apply",
                "(Lnet/minecraftforge/common/ForgeConfigSpec$Builder;)V", null, null);
        method.visitCode();
        method.visitLdcInsn("Festive Mobs");
        method.visitFieldInsn(Opcodes.GETSTATIC, config, "festiveMobs", "Ljava/util/function/Supplier;");
        method.visitLdcInsn("Festive Vanilla Mobs");
        method.visitFieldInsn(Opcodes.GETSTATIC, config, "festiveMobs", "Ljava/util/function/Supplier;");
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(1, 2);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(config.replace('/', '.'), writer.toByteArray()))
                .accept(transformed, 0);
        var fields = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(FieldInsnNode.class::isInstance).map(FieldInsnNode.class::cast).toList();
        assertEquals(2, fields.size());
        assertEquals("festiveMobs", fields.get(0).name);
        assertEquals("festiveVanillaMobs", fields.get(1).name);
    }

    @Test
    void rewritesLegacyExtraCodecsValidateToCodecInstanceMethod() {
        String codec = "com/mojang/serialization/Codec";
        String descriptor = "(L" + codec + ";Ljava/util/function/Function;)L" + codec + ";";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "example/CodecCaller", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "validate",
                "()L" + codec + ";", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/util/ExtraCodecs",
                "m_264370_", descriptor, false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(2, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("example.CodecCaller", writer.toByteArray()))
                .accept(transformed, 0);
        MethodInsnNode invocation = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKEINTERFACE, invocation.getOpcode());
        assertEquals(codec, invocation.owner);
        assertEquals("validate", invocation.name);
        assertEquals("(Ljava/util/function/Function;)L" + codec + ";", invocation.desc);
        assertTrue(invocation.itf);
    }

    @Test
    void adaptsLegacySpriteSourceCodecToTheMapCodecRegistrationApi() {
        String spriteSources = "net/minecraft/client/renderer/texture/atlas/SpriteSources";
        String spriteSourceType = "net/minecraft/client/renderer/texture/atlas/SpriteSourceType";
        String descriptor = "(Ljava/lang/String;Lcom/mojang/serialization/Codec;)L" + spriteSourceType + ";";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "example/SpriteSourceCaller", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "register",
                "()L" + spriteSourceType + ";", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, spriteSources, "m_260887_", descriptor, false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(2, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "example.SpriteSourceCaller", writer.toByteArray())).accept(transformed, 0);
        var call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacySpriteSources", call.owner);
        assertEquals("register", call.name);
        assertEquals("(Ljava/lang/String;Lcom/mojang/serialization/Codec;)L" + spriteSourceType + ";",
                call.desc);
        assertFalse(call.itf);
    }

    @Test
    void remapsLegacySrgNameOnLambdaMetafactoryFunctionalMethod() {
        String registration = "net/minecraft/client/particle/ParticleEngine$SpriteParticleRegistration";
        String spriteSet = "net/minecraft/client/particle/SpriteSet";
        String provider = "net/minecraft/client/particle/ParticleProvider";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "example/ParticleRegistration", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "factory",
                "()L" + registration + ";", null, null);
        method.visitCode();
        method.visitInvokeDynamicInsn("m_107419_", "()L" + registration + ";",
                new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                                + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;"
                                + "Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false),
                Type.getMethodType("(L" + spriteSet + ";)L" + provider + ";"),
                new Handle(Opcodes.H_INVOKESTATIC, "example/ParticleRegistration", "create",
                        "(L" + spriteSet + ";)L" + provider + ";", false),
                Type.getMethodType("(L" + spriteSet + ";)L" + provider + ";"));
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("example.ParticleRegistration", writer.toByteArray()))
                .accept(transformed, 0);
        var invocation = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(org.objectweb.asm.tree.InvokeDynamicInsnNode.class::isInstance)
                .map(org.objectweb.asm.tree.InvokeDynamicInsnNode.class::cast).findFirst().orElseThrow();
        assertEquals("create", invocation.name);
    }

    @Test
    void adaptsLegacyToolStackItemPredicateAndCallSites() {
        String legacy = "slimeknights/tconstruct/library/json/predicate/tool/ToolStackItemPredicate";
        String itemPredicate = "net/minecraft/advancements/critereon/ItemPredicate";
        String helper = "net/neoforged/forgestuck/compat/LegacyToolStackItemPredicateAdapter";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, legacy, null, itemPredicate, null);
        var constructor = writer.visitMethod(Opcodes.ACC_PRIVATE, "<init>",
                "(Lslimeknights/mantle/data/predicate/IJsonPredicate;)V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, itemPredicate, "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 2);
        constructor.visitEnd();
        writer.visitEnd();

        ClassNode adapted = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(legacy.replace('/', '.'), writer.toByteArray()))
                .accept(adapted, 0);
        assertEquals("java/lang/Object", adapted.superName);
        MethodInsnNode superCall = java.util.Arrays.stream(adapted.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        assertEquals("java/lang/Object", superCall.owner);

        ClassWriter caller = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        caller.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyToolPredicateCaller", null,
                "java/lang/Object", null);
        var factory = caller.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "predicate",
                "(Lslimeknights/mantle/data/predicate/IJsonPredicate;)L" + itemPredicate + ";", null, null);
        factory.visitCode();
        factory.visitVarInsn(Opcodes.ALOAD, 0);
        factory.visitMethodInsn(Opcodes.INVOKESTATIC, legacy, "ofTool",
                "(Lslimeknights/mantle/data/predicate/IJsonPredicate;)L" + legacy + ";", false);
        factory.visitInsn(Opcodes.ARETURN);
        factory.visitMaxs(1, 1);
        factory.visitEnd();
        caller.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyToolPredicateCaller", caller.toByteArray()))
                .accept(transformed, 0);
        MethodInsnNode factoryCall = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        assertEquals(helper, factoryCall.owner);
        assertEquals("(Ljava/lang/Object;)L" + itemPredicate + ";", factoryCall.desc);
    }

    @Test
    void redirectsRemovedItemPredicateRegistrationToCompatibilityAdapter() {
        String legacy = "slimeknights/tconstruct/library/json/predicate/tool/ToolStackItemPredicate";
        String itemPredicate = "net/minecraft/advancements/critereon/ItemPredicate";
        String helper = "net/neoforged/forgestuck/compat/LegacyToolStackItemPredicateAdapter";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyToolPredicateRegistration", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "register", "()V", null, null);
        method.visitCode();
        method.visitFieldInsn(Opcodes.GETSTATIC, legacy, "ID", "Lnet/minecraft/resources/ResourceLocation;");
        method.visitInvokeDynamicInsn("apply", "()Ljava/util/function/Function;",
                new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                                + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;"
                                + "Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false),
                Type.getMethodType("(Ljava/lang/Object;)Ljava/lang/Object;"),
                new Handle(Opcodes.H_INVOKESTATIC, legacy, "deserialize",
                        "(Lcom/google/gson/JsonObject;)L" + legacy + ";", false),
                Type.getMethodType("(Lcom/google/gson/JsonObject;)L" + legacy + ";"));
        method.visitMethodInsn(Opcodes.INVOKESTATIC, itemPredicate, "register",
                "(Lnet/minecraft/resources/ResourceLocation;Ljava/util/function/Function;)V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(2, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyToolPredicateRegistration", writer.toByteArray()))
                .accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        assertEquals(helper, call.owner);
        assertEquals("register", call.name);
        assertEquals("(Lnet/minecraft/resources/ResourceLocation;Ljava/util/function/Function;)V", call.desc);
        assertTrue(java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .anyMatch(FieldInsnNode.class::isInstance));
    }

    @Test
    void reordersLegacyFungusBlockConstructorArguments() {
        String fungusBlock = "net/minecraft/world/level/block/FungusBlock";
        String properties = "Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;";
        String key = "Lnet/minecraft/resources/ResourceKey;";
        String block = "Lnet/minecraft/world/level/block/Block;";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/SlimeFungusBlock", null, fungusBlock, null);
        var constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(" + properties + key + ")V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitVarInsn(Opcodes.ALOAD, 1);
        constructor.visitVarInsn(Opcodes.ALOAD, 2);
        constructor.visitInsn(Opcodes.ACONST_NULL);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, fungusBlock, "<init>",
                "(" + properties + key + block + ")V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(4, 3);
        constructor.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.SlimeFungusBlock", writer.toByteArray()))
                .accept(transformed, 0);
        MethodNode adapted = transformed.methods.get(0);
        MethodInsnNode call = java.util.Arrays.stream(adapted.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .filter(method -> method.owner.equals(fungusBlock)).findFirst().orElseThrow();
        assertEquals("(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/world/level/block/Block;"
                + "Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)V", call.desc);
        assertEquals(6, adapted.maxLocals);
    }

    @Test
    void bridgesInheritedLegacyFluidStateOverride() {
        String state = "net/minecraft/world/level/block/state/BlockState";
        String fluidState = "net/minecraft/world/level/material/FluidState";
        String inventoryBlock = "slimeknights/mantle/block/InventoryBlock";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/TableBlock", null, inventoryBlock, null);
        var method = writer.visitMethod(Opcodes.ACC_PROTECTED, "m_5888_", "(L" + state + ";)L" + fluidState + ";",
                null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, inventoryBlock, "m_5888_",
                "(L" + state + ";)L" + fluidState + ";", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(2, 2);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.TableBlock", writer.toByteArray()))
                .accept(transformed, 0);
        assertTrue(transformed.methods.stream().anyMatch(methodNode -> methodNode.name.equals("getFluidState")
                && methodNode.desc.equals("(L" + state + ";)L" + fluidState + ";")));
        MethodInsnNode superCall = java.util.Arrays.stream(transformed.methods.stream()
                        .filter(methodNode -> methodNode.name.equals("m_5888_"))
                        .findFirst().orElseThrow().instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .filter(call -> call.owner.equals(inventoryBlock)).findFirst().orElseThrow();
        assertEquals("getFluidState", superCall.name);
    }

    @Test
    void bridgesLegacyStringRepresentableEnumMethod() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacySubtype", null,
                "java/lang/Enum", new String[] {"forestry/api/core/IItemSubtype"});
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "m_7912_", "()Ljava/lang/String;", null, null);
        method.visitCode();
        method.visitLdcInsn("legacy_name");
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacySubtype", writer.toByteArray()))
                .accept(transformed, 0);
        MethodNode bridge = transformed.methods.stream()
                .filter(candidate -> candidate.name.equals("getSerializedName"))
                .findFirst().orElseThrow();
        assertEquals("()Ljava/lang/String;", bridge.desc);
        MethodInsnNode call = java.util.Arrays.stream(bridge.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals("m_7912_", call.name);
        assertEquals("sample/LegacySubtype", call.owner);
    }

    @Test
    void adaptsLegacyDoorBlockConstructorWithOakSetType() {
        String properties = "net/minecraft/world/level/block/state/BlockBehaviour$Properties";
        String door = "net/minecraft/world/level/block/DoorBlock";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyDoor", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "create",
                "(L" + properties + ";)L" + door + ";", null, null);
        method.visitCode();
        method.visitTypeInsn(Opcodes.NEW, door);
        method.visitInsn(Opcodes.DUP);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, door, "<init>", "(L" + properties + ";)V", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode node = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyDoor", writer.toByteArray()))
                .accept(node, 0);
        var instructions = java.util.Arrays.stream(node.methods.get(0).instructions.toArray())
                .filter(instruction -> instruction.getType() != org.objectweb.asm.tree.AbstractInsnNode.LABEL
                        && instruction.getType() != org.objectweb.asm.tree.AbstractInsnNode.FRAME
                        && instruction.getType() != org.objectweb.asm.tree.AbstractInsnNode.LINE)
                .toList();
        MethodInsnNode call = instructions.stream().filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        assertEquals("(Lnet/minecraft/world/level/block/state/properties/BlockSetType;"
                + "L" + properties + ";)V", call.desc);
        int callIndex = instructions.indexOf(call);
        assertEquals(Opcodes.SWAP, instructions.get(callIndex - 1).getOpcode());
        assertEquals(Opcodes.GETSTATIC, instructions.get(callIndex - 2).getOpcode());
        assertEquals(4, node.methods.get(0).maxStack);
    }

    @Test
    void swapsLegacyDoorBlockPropertiesAndSetTypeArguments() {
        String properties = "net/minecraft/world/level/block/state/BlockBehaviour$Properties";
        String setType = "net/minecraft/world/level/block/state/properties/BlockSetType";
        String door = "net/minecraft/world/level/block/DoorBlock";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyTypedDoor", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "create",
                "(L" + properties + ";L" + setType + ";)L" + door + ";", null, null);
        method.visitCode();
        method.visitTypeInsn(Opcodes.NEW, door);
        method.visitInsn(Opcodes.DUP);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, door, "<init>",
                "(L" + properties + ";L" + setType + ";)V", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode node = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyTypedDoor", writer.toByteArray()))
                .accept(node, 0);
        var instructions = java.util.Arrays.stream(node.methods.get(0).instructions.toArray())
                .filter(instruction -> instruction.getType() != org.objectweb.asm.tree.AbstractInsnNode.LABEL
                        && instruction.getType() != org.objectweb.asm.tree.AbstractInsnNode.FRAME
                        && instruction.getType() != org.objectweb.asm.tree.AbstractInsnNode.LINE)
                .toList();
        MethodInsnNode call = instructions.stream().filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        assertEquals("(L" + setType + ";L" + properties + ";)V", call.desc);
        assertEquals(Opcodes.SWAP, instructions.get(instructions.indexOf(call) - 1).getOpcode());
    }

    @Test
    void adaptsLegacyButtonConstructorArgumentOrderAndRemovedArrowFlag() {
        String properties = "net/minecraft/world/level/block/state/BlockBehaviour$Properties";
        String setType = "net/minecraft/world/level/block/state/properties/BlockSetType";
        String button = "net/minecraft/world/level/block/ButtonBlock";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyButton", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "create",
                "(L" + properties + ";L" + setType + ";IZ)L" + button + ";", null, null);
        method.visitCode();
        method.visitTypeInsn(Opcodes.NEW, button);
        method.visitInsn(Opcodes.DUP);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitVarInsn(Opcodes.ILOAD, 2);
        method.visitVarInsn(Opcodes.ILOAD, 3);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, button, "<init>",
                "(L" + properties + ";L" + setType + ";IZ)V", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode node = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyButton", writer.toByteArray()))
                .accept(node, 0);
        MethodNode transformedMethod = node.methods.get(0);
        MethodInsnNode call = java.util.Arrays.stream(transformedMethod.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        assertEquals("(L" + setType + ";IL" + properties + ";)V", call.desc);
        assertEquals(8, transformedMethod.maxLocals);
    }

    @Test
    void adaptsLegacyPressurePlateConstructorToSetTypeAndProperties() {
        String properties = "net/minecraft/world/level/block/state/BlockBehaviour$Properties";
        String setType = "net/minecraft/world/level/block/state/properties/BlockSetType";
        String sensitivity = "net/minecraft/world/level/block/PressurePlateBlock$Sensitivity";
        String plate = "net/minecraft/world/level/block/PressurePlateBlock";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyPlate", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "create",
                "(L" + sensitivity + ";L" + properties + ";L" + setType + ";)L" + plate + ";", null, null);
        method.visitCode();
        method.visitTypeInsn(Opcodes.NEW, plate);
        method.visitInsn(Opcodes.DUP);
        method.visitFieldInsn(Opcodes.GETSTATIC, sensitivity, "EVERYTHING", "L" + sensitivity + ";");
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, plate, "<init>",
                "(L" + sensitivity + ";L" + properties + ";L" + setType + ";)V", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode node = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyPlate", writer.toByteArray()))
                .accept(node, 0);
        MethodNode transformedMethod = node.methods.get(0);
        MethodInsnNode call = java.util.Arrays.stream(transformedMethod.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        assertEquals("(L" + setType + ";L" + properties + ";)V", call.desc);
        assertEquals(5, transformedMethod.maxLocals);
        assertTrue(java.util.Arrays.stream(transformedMethod.instructions.toArray())
                .filter(FieldInsnNode.class::isInstance).map(FieldInsnNode.class::cast)
                .noneMatch(field -> field.owner.equals(sensitivity)));
    }

    @Test
    void remapsLegacyBlockPropertiesCopyToLegacyCopyFactory() {
        String owner = "net/minecraft/world/level/block/state/BlockBehaviour$Properties";
        String desc = "(Lnet/minecraft/world/level/block/state/BlockBehaviour;)L" + owner + ";";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyBlockProperties", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "copy",
                "(Lnet/minecraft/world/level/block/state/BlockBehaviour;)L" + owner + ";", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, owner, "m_60926_", desc, false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();

        ClassNode node = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyBlockProperties", writer.toByteArray()))
                .accept(node, 0);
        var call = java.util.Arrays.stream(node.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals(owner, call.owner);
        assertEquals("ofLegacyCopy", call.name);
        assertEquals(desc, call.desc);
    }

    @Test
    void adaptsLegacyCustomRecipeSuperclassConstructor() {
        String category = "net/minecraft/world/item/crafting/CraftingBookCategory";
        String resourceLocation = "net/minecraft/resources/ResourceLocation";
        String customRecipe = "net/minecraft/world/item/crafting/CustomRecipe";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyCustomRecipe", null,
                customRecipe, null);
        var constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                "(L" + resourceLocation + ";L" + category + ";)V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitVarInsn(Opcodes.ALOAD, 1);
        constructor.visitVarInsn(Opcodes.ALOAD, 2);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, customRecipe, "<init>",
                "(L" + resourceLocation + ";L" + category + ";)V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        writer.visitEnd();

        ClassNode node = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyCustomRecipe", writer.toByteArray())).accept(node, 0);
        var instructions = node.methods.get(0).instructions.toArray();
        MethodInsnNode superCall = java.util.Arrays.stream(instructions)
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        int callIndex = java.util.Arrays.asList(instructions).indexOf(superCall);
        assertEquals("(L" + category + ";)V", superCall.desc);
        assertEquals(Opcodes.POP, instructions[callIndex - 1].getOpcode());
        assertEquals(Opcodes.SWAP, instructions[callIndex - 2].getOpcode());
    }

    @Test
    void adaptsLegacySimpleCraftingRecipeFactoryLambda() {
        String category = "net/minecraft/world/item/crafting/CraftingBookCategory";
        String resourceLocation = "net/minecraft/resources/ResourceLocation";
        String customRecipe = "net/minecraft/world/item/crafting/CustomRecipe";
        String recipe = "sample/LegacySimpleRecipe";
        String factory = "net/minecraft/world/item/crafting/SimpleCraftingRecipeSerializer$Factory";
        String legacyArguments = "(L" + resourceLocation + ";L" + category + ";)";
        ClassWriter recipeWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        recipeWriter.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, recipe, null, customRecipe, null);
        var recipeConstructor = recipeWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                legacyArguments + "V", null, null);
        recipeConstructor.visitCode();
        recipeConstructor.visitVarInsn(Opcodes.ALOAD, 0);
        recipeConstructor.visitVarInsn(Opcodes.ALOAD, 1);
        recipeConstructor.visitVarInsn(Opcodes.ALOAD, 2);
        recipeConstructor.visitMethodInsn(Opcodes.INVOKESPECIAL, customRecipe, "<init>",
                legacyArguments + "V", false);
        recipeConstructor.visitInsn(Opcodes.RETURN);
        recipeConstructor.visitMaxs(0, 0);
        recipeConstructor.visitEnd();
        recipeWriter.visitEnd();
        byte[] recipeBytes = recipeWriter.toByteArray();

        ClassWriter factoryWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        factoryWriter.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyRecipeFactory", null,
                "java/lang/Object", null);
        var factoryMethod = factoryWriter.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "factory",
                "()L" + factory + ";", null, null);
        factoryMethod.visitCode();
        factoryMethod.visitInvokeDynamicInsn("m_247316_", "()L" + factory + ";",
                new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                                + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;"
                                + "Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false),
                Type.getMethodType(legacyArguments + "Lnet/minecraft/world/item/crafting/CraftingRecipe;"),
                new Handle(Opcodes.H_NEWINVOKESPECIAL, recipe, "<init>", legacyArguments + "V", false),
                Type.getMethodType(legacyArguments + "L" + recipe + ";"));
        factoryMethod.visitInsn(Opcodes.ARETURN);
        factoryMethod.visitMaxs(0, 0);
        factoryMethod.visitEnd();
        factoryWriter.visitEnd();
        byte[] factoryBytes = factoryWriter.toByteArray();

        SrgMemberRemapStep step = new SrgMemberRemapStep();
        step.scan("sample.LegacySimpleRecipe", recipeBytes, null);
        step.scan("sample.LegacyRecipeFactory", factoryBytes, null);
        ClassNode transformedRecipe = new ClassNode();
        new ClassReader(step.apply("sample.LegacySimpleRecipe", recipeBytes)).accept(transformedRecipe, 0);
        ClassNode transformedFactory = new ClassNode();
        new ClassReader(step.apply("sample.LegacyRecipeFactory", factoryBytes)).accept(transformedFactory, 0);

        MethodNode bridgeConstructor = transformedRecipe.methods.stream()
                .filter(method -> method.name.equals("<init>") && method.desc.equals("(L" + category + ";)V"))
                .findFirst().orElseThrow();
        assertTrue(java.util.Arrays.stream(bridgeConstructor.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .anyMatch(call -> call.owner.equals(
                        "net/neoforged/forgestuck/compat/LegacyRecipeSerializerCodec")
                        && call.name.equals("legacyRecipeId")));
        var indy = java.util.Arrays.stream(transformedFactory.methods.get(0).instructions.toArray())
                .filter(org.objectweb.asm.tree.InvokeDynamicInsnNode.class::isInstance)
                .map(org.objectweb.asm.tree.InvokeDynamicInsnNode.class::cast).findFirst().orElseThrow();
        assertEquals("create", indy.name);
        assertEquals("(L" + category + ";)Lnet/minecraft/world/item/crafting/CraftingRecipe;",
                ((Type) indy.bsmArgs[0]).getDescriptor());
        assertEquals("(L" + category + ";)V", ((Handle) indy.bsmArgs[1]).getDesc());
        assertEquals("(L" + category + ";)L" + recipe + ";", ((Type) indy.bsmArgs[2]).getDescriptor());
    }

    @Test
    void addsCodecBridgeToLegacyCriterionTriggersAndErasesRemovedContexts() {
        String context = "net/minecraft/advancements/critereon/DeserializationContext";
        String trigger = "sample/LegacyCriterionTrigger";
        String simpleTrigger = "net/minecraft/advancements/critereon/SimpleCriterionTrigger";
        String parserDescriptor = "(Lcom/google/gson/JsonObject;"
                + "Lnet/minecraft/advancements/critereon/ContextAwarePredicate;L" + context + ";)"
                + "Lsample/LegacyCriterionTrigger$Instance;";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, trigger, null, simpleTrigger, null);
        var parser = writer.visitMethod(Opcodes.ACC_PROTECTED, "createInstance", parserDescriptor, null, null);
        parser.visitCode();
        parser.visitInsn(Opcodes.ACONST_NULL);
        parser.visitInsn(Opcodes.ARETURN);
        parser.visitMaxs(0, 4);
        parser.visitEnd();
        var legacyOverride = writer.visitMethod(Opcodes.ACC_PROTECTED, "m_7214_",
                "(Lcom/google/gson/JsonObject;Lnet/minecraft/advancements/critereon/ContextAwarePredicate;"
                        + "L" + context + ";)Lnet/minecraft/advancements/critereon/AbstractCriterionTriggerInstance;",
                null, null);
        legacyOverride.visitCode();
        legacyOverride.visitInsn(Opcodes.ACONST_NULL);
        legacyOverride.visitInsn(Opcodes.ARETURN);
        legacyOverride.visitMaxs(0, 4);
        legacyOverride.visitEnd();
        writer.visitEnd();
        byte[] bytes = writer.toByteArray();

        SrgMemberRemapStep step = new SrgMemberRemapStep();
        step.scan("sample.LegacyCriterionTrigger", bytes, null);
        ClassNode transformed = new ClassNode();
        new ClassReader(step.apply("sample.LegacyCriterionTrigger", bytes)).accept(transformed, 0);

        assertTrue(transformed.methods.stream().anyMatch(method ->
                method.name.equals("createInstance")
                        && method.desc.equals(parserDescriptor.replace("L" + context + ";", "Ljava/lang/Object;"))));
        MethodNode codec = transformed.methods.stream()
                .filter(method -> method.name.equals("codec")
                        && method.desc.equals("()Lcom/mojang/serialization/Codec;"))
                .findFirst().orElseThrow();
        var call = java.util.Arrays.stream(codec.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        assertEquals("net/neoforged/forgestuck/compat/LegacyCriteriaTriggers", call.owner);
        assertEquals("codec", call.name);
    }

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
    void redirectsLegacyItemStackTagFieldReadsAndWritesToComponentShim() {
        String stack = "net/minecraft/world/item/ItemStack";
        String tag = "net/minecraft/nbt/CompoundTag";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyStackFields", null, "java/lang/Object", null);
        var read = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "read",
                "(L" + stack + ";)L" + tag + ";", null, null);
        read.visitCode();
        read.visitVarInsn(Opcodes.ALOAD, 0);
        read.visitFieldInsn(Opcodes.GETFIELD, stack, "f_41590_", "L" + tag + ";");
        read.visitInsn(Opcodes.ARETURN);
        read.visitMaxs(0, 0);
        read.visitEnd();
        var write = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "write",
                "(L" + stack + ";L" + tag + ";)V", null, null);
        write.visitCode();
        write.visitVarInsn(Opcodes.ALOAD, 0);
        write.visitVarInsn(Opcodes.ALOAD, 1);
        write.visitFieldInsn(Opcodes.PUTFIELD, stack, "f_41590_", "L" + tag + ";");
        write.visitInsn(Opcodes.RETURN);
        write.visitMaxs(0, 0);
        write.visitEnd();
        writer.visitEnd();

        ClassNode node = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyStackFields", writer.toByteArray()))
                .accept(node, 0);
        var readCall = java.util.Arrays.stream(node.methods.stream()
                        .filter(method -> method.name.equals("read")).findFirst().orElseThrow()
                        .instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        var writeCall = java.util.Arrays.stream(node.methods.stream()
                        .filter(method -> method.name.equals("write")).findFirst().orElseThrow()
                        .instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, readCall.getOpcode());
        assertEquals("getTag", readCall.name);
        assertEquals("(L" + stack + ";)L" + tag + ";", readCall.desc);
        assertEquals(Opcodes.INVOKESTATIC, writeCall.getOpcode());
        assertEquals("setTag", writeCall.name);
        assertEquals("(L" + stack + ";L" + tag + ";)V", writeCall.desc);
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
    void bridgesLegacyFluidSourceAndAmountOverridesThroughForgeFlowingFluid() {
        String fluidState = "net/minecraft/world/level/material/FluidState";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyFluidSource", null,
                "net/minecraftforge/fluids/ForgeFlowingFluid", null);
        var source = writer.visitMethod(Opcodes.ACC_PUBLIC, "m_7444_", "(L" + fluidState + ";)Z", null, null);
        source.visitCode();
        source.visitInsn(Opcodes.ICONST_1);
        source.visitInsn(Opcodes.IRETURN);
        source.visitMaxs(1, 2);
        source.visitEnd();
        var amount = writer.visitMethod(Opcodes.ACC_PUBLIC, "m_7430_", "(L" + fluidState + ";)I", null, null);
        amount.visitCode();
        amount.visitInsn(Opcodes.ICONST_1);
        amount.visitInsn(Opcodes.IRETURN);
        amount.visitMaxs(1, 2);
        amount.visitEnd();
        writer.visitEnd();

        SrgMemberRemapStep step = new SrgMemberRemapStep();
        step.scan("sample.LegacyFluidSource", writer.toByteArray(), null);
        ClassNode transformed = new ClassNode();
        new ClassReader(step.apply("sample.LegacyFluidSource", writer.toByteArray())).accept(transformed, 0);
        for (var method : List.of(
                transformed.methods.stream().filter(candidate -> candidate.name.equals("isSource")).findFirst().orElseThrow(),
                transformed.methods.stream().filter(candidate -> candidate.name.equals("getAmount")).findFirst().orElseThrow())) {
            assertTrue((method.access & Opcodes.ACC_SYNTHETIC) != 0);
        }
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
    void preservesSuperCallWhenLegacyBlockStateMethodIsDeclaredByTargetClass() {
        String owner = "sample/LegacyPaneBase";
        String desc = "(Lnet/minecraft/world/level/block/state/StateDefinition$Builder;)V";
        ClassWriter parent = new ClassWriter(0);
        parent.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, owner, null,
                "net/minecraft/world/level/block/Block", null);
        parent.visitMethod(Opcodes.ACC_PROTECTED | Opcodes.ACC_ABSTRACT, "m_7926_", desc, null, null).visitEnd();
        parent.visitEnd();

        SrgMemberRemapStep step = new SrgMemberRemapStep();
        step.scan(owner.replace('/', '.'), parent.toByteArray(), null);
        ClassWriter child = new ClassWriter(0);
        child.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyGlassPane", null, owner, null);
        var method = child.visitMethod(Opcodes.ACC_PROTECTED, "createBlockStateDefinition", desc, null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, "m_7926_", desc, false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(2, 2);
        method.visitEnd();
        child.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(step.apply("sample.LegacyGlassPane", child.toByteArray())).accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        assertEquals("m_7926_", call.name);
    }

    @Test
    void redirectsLegacyIngredientSerializationToCompatBridge() {
        String serializer = "slimeknights/mantle/recipe/helper/LoadableIngredientSerializer";
        String desc = "(Lnet/minecraft/world/item/crafting/Ingredient;)Lcom/google/gson/JsonObject;";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyIngredient", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "serialize", desc, null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, serializer, "serialize", desc, false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(2, 2);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyIngredient", writer.toByteArray()))
                .accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyIngredientBridge", call.owner);
        assertEquals("serializeLegacy", call.name);
        assertEquals("(Ljava/lang/Object;Ljava/lang/Object;)Lcom/google/gson/JsonObject;", call.desc);
    }

    @Test
    void adaptsLegacyLiquidBlockSupplierConstructor() {
        String owner = "net/minecraft/world/level/block/LiquidBlock";
        String properties = "net/minecraft/world/level/block/state/BlockBehaviour$Properties";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyLiquidBlock", null, owner, null);
        var constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                "(Ljava/util/function/Supplier;L" + properties + ";)V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitVarInsn(Opcodes.ALOAD, 1);
        constructor.visitVarInsn(Opcodes.ALOAD, 2);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, "<init>",
                "(Ljava/util/function/Supplier;L" + properties + ";)V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(3, 3);
        constructor.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyLiquidBlock", writer.toByteArray()))
                .accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .filter(instruction -> instruction.owner.equals(owner)).findFirst().orElseThrow();
        assertEquals("(Lnet/minecraft/world/level/material/FlowingFluid;"
                + "Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)V", call.desc);
        assertTrue(java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .anyMatch(instruction -> instruction.owner.equals("java/util/function/Supplier")
                        && instruction.name.equals("get")));
    }

    @Test
    void rewritesLegacyItemFoodFieldToDataComponentShim() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyEdibleItem", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "check", "()Ljava/lang/Object;", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitFieldInsn(Opcodes.GETFIELD, "sample/LegacyEdibleItem", "f_41380_",
                "Lnet/minecraft/world/food/FoodProperties;");
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyEdibleItem", writer.toByteArray()))
                .accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyItemStackShim", call.owner);
        assertEquals("getFoodProperties", call.name);
    }

    @Test
    void mapsLegacyEmptyPotionFieldToWaterHolder() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyPotionDefault", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "empty",
                "()Lnet/minecraft/world/item/alchemy/Potion;", null, null);
        method.visitCode();
        method.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/world/item/alchemy/Potions", "f_43598_",
                "Lnet/minecraft/world/item/alchemy/Potion;");
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyPotionDefault", writer.toByteArray()))
                .accept(transformed, 0);
        for (MethodNode transformedMethod : transformed.methods) {
            var instructions = java.util.Arrays.stream(transformedMethod.instructions.toArray()).toList();
            FieldInsnNode field = instructions.stream().filter(FieldInsnNode.class::isInstance)
                    .map(FieldInsnNode.class::cast).findFirst().orElseThrow();
            assertEquals("WATER", field.name);
            assertEquals("Lnet/minecraft/core/Holder;", field.desc);
            assertTrue(instructions.stream().filter(MethodInsnNode.class::isInstance)
                    .map(MethodInsnNode.class::cast)
                    .anyMatch(call -> call.owner.equals("net/minecraft/core/Holder") && call.name.equals("value")));
        }
    }

    @Test
    void mapsLegacyEmptyPotionKeyToWaterRegistryKey() {
        String resourceKey = "net/minecraft/resources/ResourceKey";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyPotionKey", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "empty",
                "()L" + resourceKey + ";", null, null);
        method.visitCode();
        method.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/world/item/alchemy/Potions", "f_268695_",
                "L" + resourceKey + ";");
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyPotionKey", writer.toByteArray())).accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyPotion", call.owner);
        assertEquals("emptyKey", call.name);
        assertEquals("()L" + resourceKey + ";", call.desc);
    }

    @Test
    void mapsLegacyHolderGetCallsAndHandlesToValue() {
        String holder = "net/minecraft/core/Holder";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyHolderGet", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "get",
                "(L" + holder + ";)Ljava/lang/Object;", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, holder, "get", "()Ljava/lang/Object;", true);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        var handleMethod = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "handle",
                "()Ljava/lang/invoke/MethodHandle;", null, null);
        handleMethod.visitCode();
        handleMethod.visitLdcInsn(new org.objectweb.asm.Handle(Opcodes.H_INVOKEINTERFACE, holder, "get",
                "()Ljava/lang/Object;", true));
        handleMethod.visitInsn(Opcodes.ARETURN);
        handleMethod.visitMaxs(1, 0);
        handleMethod.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyHolderGet", writer.toByteArray()))
                .accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        assertEquals("value", call.name);
        var handle = (org.objectweb.asm.Handle) ((org.objectweb.asm.tree.LdcInsnNode)
                transformed.methods.get(1).instructions.getFirst()).cst;
        assertEquals("value", handle.getName());
    }

    @Test
    void adaptsLegacyRecipeManagerByTypeCallsAndHandles() {
        String manager = "net/minecraft/world/item/crafting/RecipeManager";
        String recipeType = "net/minecraft/world/item/crafting/RecipeType";
        String legacyDescriptor = "(L" + recipeType + ";)Ljava/util/Map;";
        String adapterDescriptor = "(L" + manager + ";L" + recipeType + ";)Ljava/util/Map;";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyRecipeManager", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "lookup",
                "(L" + manager + ";L" + recipeType + ";)Ljava/util/Map;", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, manager, "m_44054_", legacyDescriptor, false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(2, 2);
        method.visitEnd();
        var handleMethod = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "handle",
                "()Ljava/lang/invoke/MethodHandle;", null, null);
        handleMethod.visitCode();
        handleMethod.visitLdcInsn(new Handle(Opcodes.H_INVOKEVIRTUAL, manager, "byType", legacyDescriptor, false));
        handleMethod.visitInsn(Opcodes.ARETURN);
        handleMethod.visitMaxs(1, 0);
        handleMethod.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyRecipeManager", writer.toByteArray()))
                .accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyRecipeManager", call.owner);
        assertEquals("byType", call.name);
        assertEquals(adapterDescriptor, call.desc);
        var handle = (Handle) ((org.objectweb.asm.tree.LdcInsnNode)
                transformed.methods.get(1).instructions.getFirst()).cst;
        assertEquals(Opcodes.H_INVOKESTATIC, handle.getTag());
        assertEquals("net/neoforged/forgestuck/compat/LegacyRecipeManager", handle.getOwner());
        assertEquals(adapterDescriptor, handle.getDesc());
    }

    @Test
    void adaptsLegacyRecipeManagerGetRecipesForCallsAndHandles() {
        String manager = "net/minecraft/world/item/crafting/RecipeManager";
        String recipeType = "net/minecraft/world/item/crafting/RecipeType";
        String container = "net/minecraft/world/Container";
        String level = "net/minecraft/world/level/Level";
        String legacyDescriptor = "(L" + recipeType + ";L" + container + ";L" + level + ";)Ljava/util/List;";
        String adapterDescriptor = "(L" + manager + ";" + legacyDescriptor.substring(1);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyRecipeLookup", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "lookup",
                adapterDescriptor, null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitVarInsn(Opcodes.ALOAD, 3);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, manager, "m_44056_", legacyDescriptor, false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(4, 4);
        method.visitEnd();
        var handleMethod = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "handle",
                "()Ljava/lang/invoke/MethodHandle;", null, null);
        handleMethod.visitCode();
        handleMethod.visitLdcInsn(new Handle(Opcodes.H_INVOKEVIRTUAL, manager, "getRecipesFor",
                legacyDescriptor, false));
        handleMethod.visitInsn(Opcodes.ARETURN);
        handleMethod.visitMaxs(1, 0);
        handleMethod.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyRecipeLookup", writer.toByteArray()))
                .accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyRecipeManager", call.owner);
        assertEquals("getRecipesFor", call.name);
        assertEquals(adapterDescriptor, call.desc);
        var handle = (Handle) ((org.objectweb.asm.tree.LdcInsnNode)
                transformed.methods.get(1).instructions.getFirst()).cst;
        assertEquals(Opcodes.H_INVOKESTATIC, handle.getTag());
        assertEquals("net/neoforged/forgestuck/compat/LegacyRecipeManager", handle.getOwner());
        assertEquals("getRecipesFor", handle.getName());
        assertEquals(adapterDescriptor, handle.getDesc());
    }

    @Test
    void adaptsLegacyRecipeManagerGetAllRecipesForCallsAndHandles() {
        String manager = "net/minecraft/world/item/crafting/RecipeManager";
        String recipeType = "net/minecraft/world/item/crafting/RecipeType";
        String legacyDescriptor = "(L" + recipeType + ";)Ljava/util/List;";
        String adapterDescriptor = "(L" + manager + ";L" + recipeType + ";)Ljava/util/List;";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyAllRecipeLookup", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "lookup",
                adapterDescriptor, null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, manager, "m_44013_", legacyDescriptor, false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(2, 2);
        method.visitEnd();
        var handleMethod = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "handle",
                "()Ljava/lang/invoke/MethodHandle;", null, null);
        handleMethod.visitCode();
        handleMethod.visitLdcInsn(new Handle(Opcodes.H_INVOKEVIRTUAL, manager, "getAllRecipesFor",
                legacyDescriptor, false));
        handleMethod.visitInsn(Opcodes.ARETURN);
        handleMethod.visitMaxs(1, 0);
        handleMethod.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyAllRecipeLookup", writer.toByteArray()))
                .accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyRecipeManager", call.owner);
        assertEquals("getAllRecipesFor", call.name);
        assertEquals(adapterDescriptor, call.desc);
        var handle = (Handle) ((org.objectweb.asm.tree.LdcInsnNode)
                transformed.methods.get(1).instructions.getFirst()).cst;
        assertEquals(Opcodes.H_INVOKESTATIC, handle.getTag());
        assertEquals("net/neoforged/forgestuck/compat/LegacyRecipeManager", handle.getOwner());
        assertEquals("getAllRecipesFor", handle.getName());
        assertEquals(adapterDescriptor, handle.getDesc());
    }

    @Test
    void adaptsLegacyRecipeManagerGetRecipeForCallsAndHandles() {
        String manager = "net/minecraft/world/item/crafting/RecipeManager";
        String recipeType = "net/minecraft/world/item/crafting/RecipeType";
        String container = "net/minecraft/world/Container";
        String level = "net/minecraft/world/level/Level";
        String legacyDescriptor = "(L" + recipeType + ";L" + container + ";L" + level + ";)Ljava/util/Optional;";
        String adapterDescriptor = "(L" + manager + ";" + legacyDescriptor.substring(1);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacySingleRecipeLookup", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "lookup",
                adapterDescriptor, null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitVarInsn(Opcodes.ALOAD, 3);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, manager, "m_44015_", legacyDescriptor, false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(4, 4);
        method.visitEnd();
        var handleMethod = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "handle",
                "()Ljava/lang/invoke/MethodHandle;", null, null);
        handleMethod.visitCode();
        handleMethod.visitLdcInsn(new Handle(Opcodes.H_INVOKEVIRTUAL, manager, "getRecipeFor",
                legacyDescriptor, false));
        handleMethod.visitInsn(Opcodes.ARETURN);
        handleMethod.visitMaxs(1, 0);
        handleMethod.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacySingleRecipeLookup", writer.toByteArray()))
                .accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyRecipeManager", call.owner);
        assertEquals("getRecipeFor", call.name);
        assertEquals(adapterDescriptor, call.desc);
        var handle = (Handle) ((org.objectweb.asm.tree.LdcInsnNode)
                transformed.methods.get(1).instructions.getFirst()).cst;
        assertEquals(Opcodes.H_INVOKESTATIC, handle.getTag());
        assertEquals("net/neoforged/forgestuck/compat/LegacyRecipeManager", handle.getOwner());
        assertEquals("getRecipeFor", handle.getName());
        assertEquals(adapterDescriptor, handle.getDesc());
    }

    @Test
    void clampsEmptyTConstructSmelteryColumnCountBeforeConstructingSideInventory() {
        String owner = "slimeknights/tconstruct/smeltery/menu/HeatingStructureContainerMenu";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "calcColumns",
                "(I)I", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ILOAD, 0);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(owner.replace('/', '.'), writer.toByteArray()))
                .accept(transformed, 0);
        var instructions = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray()).toList();
        int returnIndex = java.util.stream.IntStream.range(0, instructions.size())
                .filter(index -> instructions.get(index).getOpcode() == Opcodes.IRETURN)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.ICONST_1, instructions.get(returnIndex - 2).getOpcode());
        MethodInsnNode max = (MethodInsnNode) instructions.get(returnIndex - 1);
        assertEquals("java/lang/Math", max.owner);
        assertEquals("max", max.name);
        assertEquals("(II)I", max.desc);
    }

    @Test
    void adaptsLegacyItemIsEdibleCallsAndHandles() {
        String item = "net/minecraft/world/item/Item";
        String compat = "net/neoforged/forgestuck/compat/LegacyItemStackShim";
        String adapterDescriptor = "(L" + item + ";)Z";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyItemEdible", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "test",
                "(L" + item + ";)Z", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, item, "m_41472_", "()Z", false);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        var handleMethod = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "handle",
                "()Ljava/lang/invoke/MethodHandle;", null, null);
        handleMethod.visitCode();
        handleMethod.visitLdcInsn(new Handle(Opcodes.H_INVOKEVIRTUAL, item, "m_41472_", "()Z", false));
        handleMethod.visitInsn(Opcodes.ARETURN);
        handleMethod.visitMaxs(1, 0);
        handleMethod.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyItemEdible", writer.toByteArray()))
                .accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals(compat, call.owner);
        assertEquals("isEdible", call.name);
        assertEquals(adapterDescriptor, call.desc);
        var handle = (Handle) ((org.objectweb.asm.tree.LdcInsnNode)
                transformed.methods.get(1).instructions.getFirst()).cst;
        assertEquals(Opcodes.H_INVOKESTATIC, handle.getTag());
        assertEquals(compat, handle.getOwner());
        assertEquals("isEdible", handle.getName());
        assertEquals(adapterDescriptor, handle.getDesc());
    }

    @Test
    void adaptsLegacyItemIsDamageableToDefaultInstanceCheck() {
        String item = "net/minecraft/world/item/Item";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyItemDamageable", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "test",
                "(L" + item + ";)Z", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, item, "m_41465_", "()Z", false);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(
                "sample.LegacyItemDamageable", writer.toByteArray())).accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyItemStackShim", call.owner);
        assertEquals("isDamageable", call.name);
        assertEquals("(L" + item + ";)Z", call.desc);
    }

    @Test
    void updatesLegacyRecipeResultCallsAndHandlesToHolderLookupProvider() {
        String recipe = "net/minecraft/world/item/crafting/ShapelessRecipe";
        String legacyDescriptor =
                "(Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;";
        String currentDescriptor =
                "(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyRecipeResult", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "result",
                "(L" + recipe + ";Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;",
                null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, recipe, "m_8043_", legacyDescriptor, true);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(2, 2);
        method.visitEnd();
        var handleMethod = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "handle",
                "()Ljava/lang/invoke/MethodHandle;", null, null);
        handleMethod.visitCode();
        handleMethod.visitLdcInsn(new Handle(Opcodes.H_INVOKEINTERFACE, recipe, "getResultItem",
                legacyDescriptor, true));
        handleMethod.visitInsn(Opcodes.ARETURN);
        handleMethod.visitMaxs(1, 0);
        handleMethod.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyRecipeResult", writer.toByteArray()))
                .accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals("getResultItem", call.name);
        assertEquals(currentDescriptor, call.desc);
        var handle = (Handle) ((org.objectweb.asm.tree.LdcInsnNode)
                transformed.methods.get(1).instructions.getFirst()).cst;
        assertEquals("getResultItem", handle.getName());
        assertEquals(currentDescriptor, handle.getDesc());
    }

    @Test
    void remapsHolderReferenceGetCallsAndHandlesToValue() {
        String reference = "net/minecraft/core/Holder$Reference";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyHolderReference", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "get",
                "(L" + reference + ";)Ljava/lang/Object;", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, reference, "get", "()Ljava/lang/Object;", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        var handleMethod = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "handle",
                "()Ljava/lang/invoke/MethodHandle;", null, null);
        handleMethod.visitCode();
        handleMethod.visitLdcInsn(new Handle(Opcodes.H_INVOKEVIRTUAL, reference, "get",
                "()Ljava/lang/Object;", false));
        handleMethod.visitInsn(Opcodes.ARETURN);
        handleMethod.visitMaxs(1, 0);
        handleMethod.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyHolderReference", writer.toByteArray()))
                .accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals("value", call.name);
        var handle = (Handle) ((org.objectweb.asm.tree.LdcInsnNode)
                transformed.methods.get(1).instructions.getFirst()).cst;
        assertEquals("value", handle.getName());
    }

    @Test
    void adaptsLegacyEnchantmentInstanceConstructionToHolderFactory() {
        String enchantment = "net/minecraft/world/item/enchantment/Enchantment";
        String instance = "net/minecraft/world/item/enchantment/EnchantmentInstance";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyEnchantmentInstance", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "create",
                "(L" + enchantment + ";I)L" + instance + ";", null, null);
        method.visitCode();
        method.visitTypeInsn(Opcodes.NEW, instance);
        method.visitInsn(Opcodes.DUP);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ILOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, instance, "<init>",
                "(L" + enchantment + ";I)V", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyEnchantmentInstance", writer.toByteArray()))
                .accept(transformed, 0);
        var instructions = transformed.methods.get(0).instructions.toArray();
        assertFalse(java.util.Arrays.stream(instructions).anyMatch(instruction ->
                instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW
                        && type.desc.equals(instance)));
        MethodInsnNode factory = java.util.Arrays.stream(instructions)
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, factory.getOpcode());
        assertEquals("net/neoforged/forgestuck/compat/LegacyEnchantmentRegistry", factory.owner);
        assertEquals("createInstance", factory.name);
        assertEquals("(L" + enchantment + ";I)L" + instance + ";", factory.desc);
    }

    @Test
    void sizesLegacyArmorMaterialDefinitionsToRuntimeArmorTypes() {
        String className = "slimeknights/tconstruct/library/tools/definition/ModifiableArmorMaterial";
        String definition = "slimeknights/tconstruct/library/tools/definition/ToolDefinition";
        String descriptor = "(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/sounds/SoundEvent;"
                + "[Lnet/minecraft/world/item/ArmorItem$Type;)L" + className + ";";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "create", descriptor, null, null);
        method.visitCode();
        method.visitInsn(Opcodes.ICONST_4);
        method.visitTypeInsn(Opcodes.ANEWARRAY, definition);
        method.visitVarInsn(Opcodes.ASTORE, 3);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 3);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(className.replace('/', '.'), writer.toByteArray()))
                .accept(transformed, 0);
        var instructions = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray()).toList();
        assertEquals(Opcodes.ALOAD, instructions.get(0).getOpcode());
        assertEquals(2, ((org.objectweb.asm.tree.VarInsnNode) instructions.get(0)).var);
        MethodInsnNode filter = (MethodInsnNode) instructions.get(1);
        assertEquals("net/neoforged/forgestuck/compat/LegacyArmorTypeShim", filter.owner);
        assertEquals("removeBody", filter.name);
        assertEquals(Opcodes.ASTORE, instructions.get(2).getOpcode());
        assertEquals(Opcodes.ALOAD, instructions.get(3).getOpcode());
        assertEquals(Opcodes.ARRAYLENGTH, instructions.get(4).getOpcode());
        assertEquals(Opcodes.ANEWARRAY, instructions.get(5).getOpcode());
    }

    @Test
    void adaptsCoFHStemConstructorToRegistryKeyArguments() {
        String className = "cofh/lib/common/block/StemBlockCoFH";
        String stemBlock = "net/minecraft/world/level/block/StemBlock";
        String block = "net/minecraft/world/level/block/Block";
        String properties = "net/minecraft/world/level/block/state/BlockBehaviour$Properties";
        String legacyStem = "net/neoforged/forgestuck/compat/LegacyStemGrownBlock";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, className, null, stemBlock, null);
        writer.visitField(Opcodes.ACC_PROTECTED, "seed", "Ljava/util/function/Supplier;", null, null).visitEnd();
        var constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                "(L" + properties + ";Ljava/util/function/Supplier;)V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/world/level/block/Blocks", "PUMPKIN",
                "L" + block + ";");
        constructor.visitTypeInsn(Opcodes.CHECKCAST, legacyStem);
        constructor.visitVarInsn(Opcodes.ALOAD, 2);
        constructor.visitVarInsn(Opcodes.ALOAD, 1);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, stemBlock, "<init>",
                "(L" + legacyStem + ";Ljava/util/function/Supplier;L" + properties + ";)V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(4, 3);
        constructor.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(className.replace('/', '.'), writer.toByteArray()))
                .accept(transformed, 0);
        MethodNode adapted = transformed.methods.get(0);
        MethodInsnNode constructorCall = java.util.Arrays.stream(adapted.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .filter(call -> call.owner.equals(stemBlock) && call.name.equals("<init>"))
                .findFirst().orElseThrow();
        assertEquals("(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/resources/ResourceKey;"
                + "Lnet/minecraft/resources/ResourceKey;L" + properties + ";)V", constructorCall.desc);
        assertTrue(java.util.Arrays.stream(adapted.instructions.toArray())
                .filter(org.objectweb.asm.tree.TypeInsnNode.class::isInstance)
                .map(org.objectweb.asm.tree.TypeInsnNode.class::cast)
                .anyMatch(cast -> cast.getOpcode() == Opcodes.CHECKCAST && cast.desc.equals(block)));
        assertEquals(6, adapted.maxLocals);
        assertTrue(adapted.maxStack >= 5);
        MethodNode cloneStack = transformed.methods.stream()
                .filter(method -> method.name.equals("getCloneItemStack"))
                .findFirst().orElseThrow();
        assertTrue(java.util.Arrays.stream(cloneStack.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .anyMatch(call -> call.owner.equals("net/neoforged/forgestuck/compat/LegacyStemBlockShim")
                        && call.name.equals("seedStack")));
    }

    @Test
    void adaptsCoFHAttachedStemConstructorToRegistryKeyArguments() {
        String className = "cofh/lib/common/block/AttachedStemBlockCoFH";
        String attachedStem = "net/minecraft/world/level/block/AttachedStemBlock";
        String block = "net/minecraft/world/level/block/Block";
        String properties = "net/minecraft/world/level/block/state/BlockBehaviour$Properties";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, className, null, attachedStem, null);
        var constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                "(L" + properties + ";Ljava/util/function/Supplier;)V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/world/level/block/Blocks", "PUMPKIN",
                "L" + block + ";");
        constructor.visitTypeInsn(Opcodes.CHECKCAST,
                "net/minecraft/world/level/block/StemGrownBlock");
        constructor.visitVarInsn(Opcodes.ALOAD, 2);
        constructor.visitVarInsn(Opcodes.ALOAD, 1);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, attachedStem, "<init>",
                "(Lnet/minecraft/world/level/block/StemGrownBlock;Ljava/util/function/Supplier;"
                        + "L" + properties + ";)V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(4, 3);
        constructor.visitEnd();
        writer.visitEnd();

        byte[] ownerMapped = new ForgeOwnerRemapStep().apply(
                className.replace('/', '.'), writer.toByteArray());
        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(className.replace('/', '.'), ownerMapped))
                .accept(transformed, 0);
        MethodNode adapted = transformed.methods.get(0);
        MethodInsnNode constructorCall = java.util.Arrays.stream(adapted.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .filter(call -> call.owner.equals(attachedStem) && call.name.equals("<init>"))
                .findFirst().orElseThrow();
        assertEquals("(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/resources/ResourceKey;"
                + "Lnet/minecraft/resources/ResourceKey;L" + properties + ";)V", constructorCall.desc);
        assertTrue(java.util.Arrays.stream(adapted.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .anyMatch(call -> call.owner.equals("net/neoforged/forgestuck/compat/LegacyStemBlockShim")
                        && call.name.equals("stemKey")));
        assertTrue(adapted.maxStack >= 5);
    }

    @Test
    void accessesLegacyVoxelShapeBackingFieldThroughCompatibilityShim() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "example/ShapeReader", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "read",
                "(Lnet/minecraft/world/phys/shapes/VoxelShape;)Lnet/minecraft/world/phys/shapes/DiscreteVoxelShape;",
                null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/world/phys/shapes/VoxelShape", "f_83211_",
                "Lnet/minecraft/world/phys/shapes/DiscreteVoxelShape;");
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("example.ShapeReader", writer.toByteArray()))
                .accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals("net/neoforged/forgestuck/compat/LegacyVoxelShapeShim", call.owner);
        assertEquals("getDiscreteShape", call.name);
    }

    @Test
    void mapsRemovedLivingEntityMobTypeGetterToLegacyTagAdapter() {
        String livingEntity = "net/minecraft/world/entity/LivingEntity";
        String mobType = "net/minecraft/world/entity/MobType";
        String legacyType = "net/neoforged/forgestuck/compat/LegacyMobType";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "example/MobTypeReader", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "read",
                "(L" + livingEntity + ";)L" + mobType + ";", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, livingEntity, "m_6336_", "()L" + mobType + ";", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();

        byte[] ownerMapped = new ForgeOwnerRemapStep().apply("example.MobTypeReader", writer.toByteArray());
        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("example.MobTypeReader", ownerMapped))
                .accept(transformed, 0);
        assertEquals("(L" + livingEntity + ";)L" + legacyType + ";", transformed.methods.get(0).desc);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals(legacyType, call.owner);
        assertEquals("getMobType", call.name);
    }

    @Test
    void mapsLegacyMobTypeConstantsToAdapterEnumValues() {
        String mobType = "net/minecraft/world/entity/MobType";
        String legacyType = "net/neoforged/forgestuck/compat/LegacyMobType";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "example/MobTypeConstant", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "undefined",
                "()L" + mobType + ";", null, null);
        method.visitCode();
        method.visitFieldInsn(Opcodes.GETSTATIC, mobType, "f_21640_", "L" + mobType + ";");
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();

        byte[] ownerMapped = new ForgeOwnerRemapStep().apply("example.MobTypeConstant", writer.toByteArray());
        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("example.MobTypeConstant", ownerMapped))
                .accept(transformed, 0);
        FieldInsnNode field = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(FieldInsnNode.class::isInstance).map(FieldInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(legacyType, field.owner);
        assertEquals("UNDEFINED", field.name);
    }

    @Test
    void convertsLegacyArmorMaterialBeforeCallingArmorItemConstructor() {
        String armorItem = "net/minecraft/world/item/ArmorItem";
        String legacyMaterial = "net/neoforged/forgestuck/compat/LegacyArmorMaterial";
        String material = "net/minecraft/world/item/ArmorMaterial";
        String type = "net/minecraft/world/item/ArmorItem$Type";
        String properties = "net/minecraft/world/item/Item$Properties";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "example/LegacyArmor", null, armorItem, null);
        var constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                "(L" + material + ";L" + type + ";L" + properties + ";)V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitVarInsn(Opcodes.ALOAD, 1);
        constructor.visitVarInsn(Opcodes.ALOAD, 2);
        constructor.visitVarInsn(Opcodes.ALOAD, 3);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, armorItem, "<init>",
                "(L" + material + ";L" + type + ";L" + properties + ";)V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(4, 4);
        constructor.visitEnd();
        writer.visitEnd();

        byte[] ownerMapped = new ForgeOwnerRemapStep().apply("example.LegacyArmor", writer.toByteArray());
        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("example.LegacyArmor", ownerMapped))
                .accept(transformed, 0);
        MethodNode adapted = transformed.methods.get(0);
        MethodInsnNode armorConstructor = java.util.Arrays.stream(adapted.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .filter(call -> call.owner.equals(armorItem) && call.name.equals("<init>"))
                .findFirst().orElseThrow();
        assertEquals("(Lnet/minecraft/core/Holder;L" + type + ";L" + properties + ";)V",
                armorConstructor.desc);
        assertTrue(java.util.Arrays.stream(adapted.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .anyMatch(call -> call.owner.equals("net/neoforged/forgestuck/compat/LegacyArmorMaterialShim")
                        && call.name.equals("asHolder")));
        assertTrue(adapted.maxStack >= 4);
    }

    @Test
    void removesArmorBodyTypeFromLegacyEnumIteration() {
        String type = "net/minecraft/world/item/ArmorItem$Type";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "example/ArmorTypes", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "values",
                "()[L" + type + ";", null, null);
        method.visitCode();
        method.visitMethodInsn(Opcodes.INVOKESTATIC, type, "values", "()[L" + type + ";", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("example.ArmorTypes", writer.toByteArray()))
                .accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals("net/neoforged/forgestuck/compat/LegacyArmorTypeShim", call.owner);
        assertEquals("values", call.name);
    }

    @Test
    void adaptsBucketItemSupplierConstructorToFluidArgument() {
        String bucket = "net/minecraft/world/item/BucketItem";
        String properties = "net/minecraft/world/item/Item$Properties";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "example/LegacyBucket", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "create",
                "(Ljava/util/function/Supplier;L" + properties + ";)L" + bucket + ";", null, null);
        method.visitCode();
        method.visitTypeInsn(Opcodes.NEW, bucket);
        method.visitInsn(Opcodes.DUP);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, bucket, "<init>",
                "(Ljava/util/function/Supplier;L" + properties + ";)V", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(4, 2);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("example.LegacyBucket", writer.toByteArray()))
                .accept(transformed, 0);
        MethodNode create = transformed.methods.get(0);
        MethodInsnNode constructor = java.util.Arrays.stream(create.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .filter(call -> call.owner.equals(bucket) && call.name.equals("<init>"))
                .findFirst().orElseThrow();
        assertEquals("(Lnet/minecraft/world/level/material/Fluid;L" + properties + ";)V", constructor.desc);
        assertTrue(java.util.Arrays.stream(create.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .anyMatch(call -> call.owner.equals("java/util/function/Supplier") && call.name.equals("get")));
        assertTrue(create.maxLocals >= 3);
    }

    @Test
    void redirectsRemovedBlocksLogFactoryToCompatibilityFactory() {
        String blocks = "net/minecraft/world/level/block/Blocks";
        String mapColor = "net/minecraft/world/level/material/MapColor";
        String pillar = "net/minecraft/world/level/block/RotatedPillarBlock";
        String descriptor = "(L" + mapColor + ";L" + mapColor + ";)L" + pillar + ";";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "example/LegacyLogFactory", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "create",
                "(L" + mapColor + ";L" + mapColor + ";)L" + pillar + ";", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, blocks, "m_284204_", descriptor, false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(2, 2);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("example.LegacyLogFactory", writer.toByteArray()))
                .accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals("net/neoforged/forgestuck/compat/LegacyBlockFactoryShim", call.owner);
        assertEquals("log", call.name);
        assertEquals(descriptor, call.desc);
    }

    @Test
    void redirectsRemovedBlocksLeavesFactoryToCompatibilityFactory() {
        String blocks = "net/minecraft/world/level/block/Blocks";
        String sound = "net/minecraft/world/level/block/SoundType";
        String leaves = "net/minecraft/world/level/block/LeavesBlock";
        String descriptor = "(L" + sound + ";)L" + leaves + ";";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "example/LegacyLeavesFactory", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "create",
                "(L" + sound + ";)L" + leaves + ";", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, blocks, "m_152614_", descriptor, false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("example.LegacyLeavesFactory", writer.toByteArray()))
                .accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals("net/neoforged/forgestuck/compat/LegacyBlockFactoryShim", call.owner);
        assertEquals("leaves", call.name);
        assertEquals(descriptor, call.desc);
    }

    @Test
    void redirectsLegacyButtonFactoryToCompatibilityShim() {
        String blocks = "net/minecraft/world/level/block/Blocks";
        String type = "net/minecraft/world/level/block/state/properties/BlockSetType";
        String flags = "[Lnet/minecraft/world/flag/FeatureFlag;";
        String button = "net/minecraft/world/level/block/ButtonBlock";
        String descriptor = "(L" + type + ";" + flags + ")L" + button + ";";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "example/LegacyButtonFactory", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "create",
                "(L" + type + ";" + flags + ")L" + button + ";", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, blocks, "m_278156_", descriptor, false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(2, 2);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("example.LegacyButtonFactory", writer.toByteArray()))
                .accept(transformed, 0);
        MethodInsnNode call = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        assertEquals("net/neoforged/forgestuck/compat/LegacyBlockFactoryShim", call.owner);
        assertEquals("button", call.name);
        assertEquals(descriptor, call.desc);
    }

    @Test
    void relocatesRemovedProjectileDispenseSuperclassAndDescriptors() {
        String oldType = "net/minecraft/core/dispenser/AbstractProjectileDispenseBehavior";
        String shim = "net/neoforged/forgestuck/compat/LegacyProjectileDispenseBehavior";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "example/LegacyProjectileBehavior", null, oldType, null);
        writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "BEHAVIOR", "L" + oldType + ";", null, null)
                .visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("example.LegacyProjectileBehavior", writer.toByteArray()))
                .accept(transformed, 0);
        assertEquals(shim, transformed.superName);
        assertEquals("L" + shim + ";", transformed.fields.get(0).desc);
    }

    @Test
    void removesUnavailableVanishableMarkerInterface() {
        String vanish = "net/minecraft/world/item/Vanishable";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "example/LegacyVanishableItem", null,
                "java/lang/Object", new String[] {vanish});
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("example.LegacyVanishableItem", writer.toByteArray()))
                .accept(transformed, 0);
        assertTrue(transformed.interfaces.isEmpty());
    }

    @Test
    void mapsLegacyAttackUuidAndAdaptsAttributeModifierConstructor() {
        String owner = "example/LegacyModifier";
        String modifier = "net/minecraft/world/entity/ai/attributes/AttributeModifier";
        String operation = modifier + "$Operation";
        String oldDescriptor = "(Ljava/util/UUID;Ljava/lang/String;D"
                + "L" + operation + ";)V";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "create",
                "()L" + modifier + ";", null, null);
        method.visitCode();
        method.visitTypeInsn(Opcodes.NEW, modifier);
        method.visitInsn(Opcodes.DUP);
        method.visitFieldInsn(Opcodes.GETSTATIC, "cofh/thermal/core/common/item/WrenchItem",
                "f_41374_", "Ljava/util/UUID;");
        method.visitLdcInsn("Tool modifier");
        method.visitInsn(Opcodes.DCONST_0);
        method.visitFieldInsn(Opcodes.GETSTATIC, operation, "ADDITION", "L" + operation + ";");
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, modifier, "<init>", oldDescriptor, false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(6, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(owner.replace('/', '.'), writer.toByteArray()))
                .accept(transformed, 0);
        MethodNode create = transformed.methods.get(0);
        FieldInsnNode id = java.util.Arrays.stream(create.instructions.toArray())
                .filter(FieldInsnNode.class::isInstance).map(FieldInsnNode.class::cast)
                .filter(field -> field.name.equals("ATTACK_DAMAGE_UUID"))
                .findFirst().orElseThrow();
        assertEquals("net/neoforged/forgestuck/compat/LegacyAttributeModifierShim", id.owner);
        assertEquals("ATTACK_DAMAGE_UUID", id.name);
        assertEquals("Ljava/util/UUID;", id.desc);
        MethodInsnNode constructor = java.util.Arrays.stream(create.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .filter(call -> call.owner.equals(modifier) && call.name.equals("<init>"))
                .findFirst().orElseThrow();
        assertEquals("(Lnet/minecraft/resources/ResourceLocation;D"
                + "L" + operation + ";)V", constructor.desc);
        assertTrue(java.util.Arrays.stream(create.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .anyMatch(call -> call.owner.equals("net/neoforged/forgestuck/compat/LegacyAttributeModifierShim")
                        && call.name.equals("toId")));
        assertTrue(create.maxLocals >= 5);
    }

    @Test
    void adaptsLegacyAttributeSupplierBuilderToHolderArguments() {
        String builder = "net/minecraft/world/entity/ai/attributes/AttributeSupplier$Builder";
        String attribute = "net/minecraft/world/entity/ai/attributes/Attribute";
        String descriptor = "(L" + builder + ";L" + attribute + ";D)L" + builder + ";";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "example/LegacyAttributeBuilder", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "add", descriptor, null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitVarInsn(Opcodes.DLOAD, 2);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, builder, "m_22268_",
                "(L" + attribute + ";D)L" + builder + ";", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 4);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("example.LegacyAttributeBuilder", writer.toByteArray()))
                .accept(transformed, 0);
        var calls = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        MethodInsnNode add = calls.stream().filter(call -> call.owner.equals(builder)).findFirst().orElseThrow();
        assertEquals("add", add.name);
        assertEquals("(Lnet/minecraft/core/Holder;D)L" + builder + ";", add.desc);
        assertTrue(calls.stream().anyMatch(call -> call.owner.equals("net/minecraft/core/Holder")
                && call.name.equals("direct") && call.itf));
    }

    @Test
    void castsChargedSoilTickReceiverForProtectedAccess() {
        String owner = "cofh/thermal/core/common/block/ChargedSoilBlock";
        String block = "net/minecraft/world/level/block/Block";
        String state = "net/minecraft/world/level/block/state/BlockState";
        String descriptor = "(Lnet/minecraft/world/level/block/state/BlockState;"
                + "Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;"
                + "Lnet/minecraft/util/RandomSource;)V";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, owner, null, block, null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "charge",
                "(L" + state + ";Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, state, "m_60734_", "()L" + block + ";", false);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitTypeInsn(Opcodes.CHECKCAST, "net/minecraft/server/level/ServerLevel");
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/world/level/Level",
                "f_46441_", "Lnet/minecraft/util/RandomSource;");
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, block, "m_213897_", descriptor, false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode transformed = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply(owner.replace('/', '.'), writer.toByteArray()))
                .accept(transformed, 0);
        var instructions = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray())
                .filter(instruction -> instruction.getType() != org.objectweb.asm.tree.AbstractInsnNode.LABEL
                        && instruction.getType() != org.objectweb.asm.tree.AbstractInsnNode.FRAME
                        && instruction.getType() != org.objectweb.asm.tree.AbstractInsnNode.LINE)
                .toList();
        int blockGetter = -1;
        for (int i = 0; i < instructions.size(); i++) {
            if (instructions.get(i) instanceof MethodInsnNode call
                    && call.owner.equals(state) && call.name.equals("getBlock")) {
                blockGetter = i;
                break;
            }
        }
        assertTrue(blockGetter >= 0);
        TypeInsnNode cast = (TypeInsnNode) instructions.get(blockGetter + 1);
        assertEquals(Opcodes.CHECKCAST, cast.getOpcode());
        assertEquals(owner, cast.desc);
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
    void adaptsLegacyStairBlockSupplierConstructorToVanillaStateConstructor() {
        String stair = "net/minecraft/world/level/block/StairBlock";
        String state = "net/minecraft/world/level/block/state/BlockState";
        String properties = "net/minecraft/world/level/block/state/BlockBehaviour$Properties";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyStairFactory", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "create",
                "(Ljava/util/function/Supplier;L" + properties + ";)L" + stair + ";", null, null);
        method.visitCode();
        method.visitTypeInsn(Opcodes.NEW, stair);
        method.visitInsn(Opcodes.DUP);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, stair, "<init>",
                "(Ljava/util/function/Supplier;L" + properties + ";)V", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(4, 2);
        method.visitEnd();
        writer.visitEnd();

        ClassNode node = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyStairFactory", writer.toByteArray()))
                .accept(node, 0);
        var instructions = java.util.Arrays.stream(node.methods.get(0).instructions.toArray())
                .filter(instruction -> instruction.getOpcode() >= 0).toList();
        int constructorIndex = -1;
        for (int i = 0; i < instructions.size(); i++) {
            if (instructions.get(i) instanceof MethodInsnNode call
                    && call.getOpcode() == Opcodes.INVOKESPECIAL
                    && call.owner.equals(stair) && call.name.equals("<init>")) {
                constructorIndex = i;
                assertEquals("(L" + state + ";L" + properties + ";)V", call.desc);
            }
        }
        assertTrue(constructorIndex > 0);
        assertEquals(Opcodes.SWAP, instructions.get(constructorIndex - 1).getOpcode());
        assertEquals(Opcodes.CHECKCAST, instructions.get(constructorIndex - 2).getOpcode());
        MethodInsnNode get = (MethodInsnNode) instructions.get(constructorIndex - 3);
        assertEquals(Opcodes.INVOKEINTERFACE, get.getOpcode());
        assertEquals("java/util/function/Supplier", get.owner);
        assertEquals("get", get.name);
        assertEquals(Opcodes.SWAP, instructions.get(constructorIndex - 4).getOpcode());
    }

    @Test
    void widensLegacyAmethystClusterConstructorDimensionsToFloats() {
        String owner = "net/minecraft/world/level/block/AmethystClusterBlock";
        String properties = "net/minecraft/world/level/block/state/BlockBehaviour$Properties";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/LegacyClusterFactory", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "create",
                "(IIL" + properties + ";)L" + owner + ";", null, null);
        method.visitCode();
        method.visitTypeInsn(Opcodes.NEW, owner);
        method.visitInsn(Opcodes.DUP);
        method.visitVarInsn(Opcodes.ILOAD, 0);
        method.visitVarInsn(Opcodes.ILOAD, 1);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, "<init>",
                "(IIL" + properties + ";)V", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(5, 3);
        method.visitEnd();
        writer.visitEnd();

        ClassNode node = new ClassNode();
        new ClassReader(new SrgMemberRemapStep().apply("sample.LegacyClusterFactory", writer.toByteArray()))
                .accept(node, 0);
        MethodNode transformed = node.methods.get(0);
        MethodInsnNode constructor = java.util.Arrays.stream(transformed.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .filter(call -> call.owner.equals(owner) && call.name.equals("<init>"))
                .findFirst().orElseThrow();
        assertEquals("(FFL" + properties + ";)V", constructor.desc);
        assertEquals(6, transformed.maxLocals);
        var instructions = java.util.Arrays.stream(transformed.instructions.toArray())
                .filter(instruction -> instruction.getOpcode() >= 0).toList();
        int callIndex = instructions.indexOf(constructor);
        assertEquals(Opcodes.ALOAD, instructions.get(callIndex - 1).getOpcode());
        assertEquals(Opcodes.I2F, instructions.get(callIndex - 2).getOpcode());
        assertEquals(Opcodes.ILOAD, instructions.get(callIndex - 3).getOpcode());
        assertEquals(Opcodes.I2F, instructions.get(callIndex - 4).getOpcode());
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
