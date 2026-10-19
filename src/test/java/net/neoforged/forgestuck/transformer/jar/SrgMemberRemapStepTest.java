package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.objectweb.asm.tree.TypeInsnNode;

class SrgMemberRemapStepTest {
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
        assertEquals(7, calls.size());
        assertEquals(java.util.List.of("vertex", "color", "color", "normal", "uv", "uv2", "endVertex"),
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
        var instructions = java.util.Arrays.stream(transformed.methods.get(0).instructions.toArray()).toList();
        FieldInsnNode field = instructions.stream().filter(FieldInsnNode.class::isInstance)
                .map(FieldInsnNode.class::cast).findFirst().orElseThrow();
        assertEquals("WATER", field.name);
        assertEquals("Lnet/minecraft/core/Holder;", field.desc);
        assertTrue(instructions.stream().filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .anyMatch(call -> call.owner.equals("net/minecraft/core/Holder") && call.name.equals("value")));
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
