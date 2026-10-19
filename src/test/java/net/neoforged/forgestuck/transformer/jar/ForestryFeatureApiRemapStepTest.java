package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class ForestryFeatureApiRemapStepTest {
    @Test
    void remapsFeaturePackageAndCurrentMenuFactoryDescriptors() {
        String oldFeatureRegistry = "forestry/modules/features/IFeatureRegistry";
        String oldFeatureMenuType = "forestry/modules/features/FeatureMenuType";
        String oldRegistry = "forestry/modules/features/ModFeatureRegistry";
        String currentFeatureRegistry = "forestry/core/platform/registration/IFeatureRegistry";
        String currentFeatureMenuType = "forestry/core/platform/registration/FeatureMenuType";
        String currentFeatureRegistryImpl = "forestry/core/platform/registration/FeatureRegistry";
        String legacyFactory = "net/neoforged/forgestuck/compat/LegacyContainerFactory";
        String legacyDeferredRegister = "net/neoforged/forgestuck/compat/LegacyDeferredRegister";
        String modernFactory = "net/neoforged/neoforge/network/IContainerFactory";
        String modernDeferredRegister = "net/neoforged/neoforge/registries/DeferredRegister";

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/GendustryMenus", null,
                "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "registry",
                "L" + oldFeatureRegistry + ";", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "menu",
                "L" + oldFeatureMenuType + ";", null, null).visitEnd();
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "register",
                "(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceKey;L"
                        + legacyFactory + ";)V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, oldRegistry, "get",
                "(Lnet/minecraft/resources/ResourceLocation;)L" + oldFeatureRegistry + ";", false);
        method.visitInsn(Opcodes.DUP);
        method.visitFieldInsn(Opcodes.PUTSTATIC, "com/example/GendustryMenus", "registry",
                "L" + oldFeatureRegistry + ";");
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, oldFeatureRegistry, "getRegistry",
                "(Lnet/minecraft/resources/ResourceKey;)L" + legacyDeferredRegister + ";", true);
        method.visitInsn(Opcodes.POP);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitLdcInsn("test");
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, oldFeatureRegistry, "menuType",
                "(L" + legacyFactory + ";Ljava/lang/String;)L" + oldFeatureMenuType + ";", true);
        method.visitFieldInsn(Opcodes.PUTSTATIC, "com/example/GendustryMenus", "menu",
                "L" + oldFeatureMenuType + ";");
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        byte[] original = writer.toByteArray();
        byte[] transformed = new ForestryFeatureApiRemapStep().apply("com.example.GendustryMenus", original);
        assertNotSame(original, transformed);

        ClassNode node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);
        assertEquals("L" + currentFeatureRegistry + ";", node.fields.get(0).desc);
        assertEquals("L" + currentFeatureMenuType + ";", node.fields.get(1).desc);
        var calls = java.util.Arrays.stream(node.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        MethodInsnNode get = calls.stream().filter(call -> call.name.equals("get")).findFirst().orElseThrow();
        assertEquals(oldRegistry.replace("forestry/modules/features/", "forestry/core/platform/registration/"),
                get.owner);
        assertEquals("(Lnet/minecraft/resources/ResourceLocation;)L" + currentFeatureRegistryImpl + ";", get.desc);
        MethodInsnNode menuType = calls.stream().filter(call -> call.name.equals("menuType"))
                .findFirst().orElseThrow();
        assertTrue(menuType.itf);
        assertEquals("(L" + modernFactory + ";Ljava/lang/String;)L" + currentFeatureMenuType + ";",
                menuType.desc);
        MethodInsnNode getRegistry = calls.stream().filter(call -> call.name.equals("getRegistry"))
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKEINTERFACE, getRegistry.getOpcode());
        assertEquals(currentFeatureRegistry, getRegistry.owner);
        assertEquals("(Lnet/minecraft/resources/ResourceKey;)L" + modernDeferredRegister + ";", getRegistry.desc);
        MethodInsnNode wrap = calls.stream().filter(call -> call.owner.equals(legacyDeferredRegister))
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, wrap.getOpcode());
        assertEquals("(L" + modernDeferredRegister + ";)L" + legacyDeferredRegister + ";", wrap.desc);
        assertEquals(Type.getObjectType(currentFeatureRegistry), Type.getType(node.fields.get(0).desc));
    }

    @Test
    void remapsMovedGuiTypes() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/GendustryScreen", null,
                "forestry/core/gui/GuiForestryTitled",
                new String[] {"forestry/core/tiles/IFilterSlotDelegate"});
        writer.visitField(Opcodes.ACC_PUBLIC, "widget",
                "Lforestry/core/gui/widgets/TankWidget;", null, null).visitEnd();
        writer.visitEnd();

        ClassNode node = new ClassNode();
        new ClassReader(new ForestryFeatureApiRemapStep().apply(
                "com.example.GendustryScreen", writer.toByteArray())).accept(node, 0);
        assertEquals("forestry/core/platform/gui/GuiForestryTitled", node.superName);
        assertEquals("forestry/api/core/IFilterSlotDelegate", node.interfaces.get(0));
        assertEquals("Lforestry/core/platform/gui/widgets/TankWidget;", node.fields.get(0).desc);
    }

    @Test
    void upgradesForestryGuiStreamBufferAndPayloadSignatures() {
        String oldMenu = "forestry/core/gui/ContainerTile";
        String oldStream = "forestry/core/network/IStreamableGui";
        String oldPayload = "forestry/api/modules/IForestryPacketClient";
        String registryBuffer = "net/minecraft/network/RegistryFriendlyByteBuf";
        String customPayload = "net/minecraft/network/protocol/common/custom/CustomPacketPayload";

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/GendustryTile", null,
                oldMenu, new String[] {oldStream});
        var write = writer.visitMethod(Opcodes.ACC_PUBLIC, "writeGuiData",
                "(Lnet/minecraft/network/FriendlyByteBuf;)V", null, null);
        write.visitCode();
        write.visitInsn(Opcodes.RETURN);
        write.visitMaxs(0, 0);
        write.visitEnd();
        var send = writer.visitMethod(Opcodes.ACC_PUBLIC, "send", "()V", null, null);
        send.visitCode();
        send.visitVarInsn(Opcodes.ALOAD, 0);
        send.visitInsn(Opcodes.ACONST_NULL);
        send.visitMethodInsn(Opcodes.INVOKEVIRTUAL, oldMenu, "sendPacketToListeners",
                "(L" + oldPayload + ";)V", false);
        send.visitInsn(Opcodes.RETURN);
        send.visitMaxs(0, 0);
        send.visitEnd();
        writer.visitEnd();

        ClassNode node = new ClassNode();
        new ClassReader(new ForestryFeatureApiRemapStep().apply(
                "com.example.GendustryTile", writer.toByteArray())).accept(node, 0);
        assertEquals("forestry/core/platform/gui/ContainerTile", node.superName);
        assertEquals("forestry/core/platform/network/IStreamableGui", node.interfaces.get(0));
        assertEquals("(L" + registryBuffer + ";)V", node.methods.get(0).desc);
        MethodInsnNode sendCall = java.util.Arrays.stream(node.methods.get(1).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals("forestry/core/platform/gui/ContainerTile", sendCall.owner);
        assertEquals("(L" + customPayload + ";)V", sendCall.desc);
    }

    @Test
    void adaptsLegacyForestryBlockItemFactory() {
        String oldBuilder = "forestry/modules/features/FeatureBlockGroup$Builder";
        String currentBuilder = "forestry/core/platform/registration/FeatureBlockGroup$Builder";
        String adapter = "net/neoforged/forgestuck/compat/LegacyForestryBlockItemFactory";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/GendustryBlocks", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "withItem",
                "(L" + oldBuilder + ";Ljava/util/function/Function;)L" + oldBuilder + ";", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, oldBuilder, "item",
                "(Ljava/util/function/Function;)L" + oldBuilder + ";", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode node = new ClassNode();
        new ClassReader(new ForestryFeatureApiRemapStep().apply(
                "com.example.GendustryBlocks", writer.toByteArray())).accept(node, 0);
        var calls = java.util.Arrays.stream(node.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        assertEquals(adapter, calls.get(0).owner);
        assertEquals("(Ljava/util/function/Function;)Ljava/util/function/BiFunction;", calls.get(0).desc);
        assertEquals(currentBuilder, calls.get(1).owner);
        assertEquals("(Ljava/util/function/BiFunction;)L" + currentBuilder + ";", calls.get(1).desc);
    }

    @Test
    void remapsSerializedNameCallsOnForestryMachineProperties() {
        String oldProperties = "forestry/core/blocks/IMachineProperties";
        String currentProperties = "forestry/core/platform/block/IMachineProperties";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/MachineType", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "getName", "()Ljava/lang/String;", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, oldProperties, "m_7912_", "()Ljava/lang/String;", true);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();

        ClassNode node = new ClassNode();
        new ClassReader(new ForestryFeatureApiRemapStep().apply(
                "com.example.MachineType", writer.toByteArray())).accept(node, 0);
        MethodInsnNode call = java.util.Arrays.stream(node.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(currentProperties, call.owner);
        assertEquals("getSerializedName", call.name);
        assertEquals("()Ljava/lang/String;", call.desc);
    }

    @Test
    void reordersLegacyForestryBlockBaseConstructorArguments() {
        String oldBlockBase = "forestry/core/blocks/BlockBase";
        String oldConstructor = "(Ljava/lang/Enum;Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)V";
        String currentConstructor = "(Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;Ljava/lang/Enum;)V";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/GendustryBlock", null,
                oldBlockBase, null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/lang/Enum;)V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitTypeInsn(Opcodes.NEW, "net/minecraft/world/level/block/state/BlockBehaviour$Properties");
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "net/minecraft/world/level/block/state/BlockBehaviour$Properties", "<init>", "()V", false);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, oldBlockBase, "<init>", oldConstructor, false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode node = new ClassNode();
        new ClassReader(new ForestryFeatureApiRemapStep().apply(
                "com.example.GendustryBlock", writer.toByteArray())).accept(node, 0);
        var instructions = node.methods.get(0).instructions.toArray();
        MethodInsnNode call = java.util.Arrays.stream(instructions)
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .filter(instruction -> instruction.name.equals("<init>")
                        && instruction.owner.equals("forestry/core/platform/block/BlockBase"))
                .findFirst().orElseThrow();
        assertEquals(currentConstructor, call.desc);
        int callIndex = java.util.Arrays.asList(instructions).indexOf(call);
        assertEquals(Opcodes.ASTORE, instructions[callIndex - 3].getOpcode());
        assertEquals(Opcodes.ALOAD, instructions[callIndex - 2].getOpcode());
        assertEquals(Opcodes.SWAP, instructions[callIndex - 1].getOpcode());
    }

    @Test
    void mapsRemovedForestryMailTabToCurrentMainTab() {
        String oldTabs = "forestry/core/tab/ForestryCreativeTabs";
        String currentTabs = "forestry/core/platform/tab/ForestryCreativeTabs";
        String oldFeatureTab = "forestry/modules/features/FeatureCreativeTab";
        String currentFeatureTab = "forestry/core/platform/registration/FeatureCreativeTab";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/GendustryTabs", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "mailTab",
                "()L" + oldFeatureTab + ";", null, null);
        method.visitCode();
        method.visitFieldInsn(Opcodes.GETSTATIC, oldTabs, "MAIL", "L" + oldFeatureTab + ";");
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode node = new ClassNode();
        new ClassReader(new ForestryFeatureApiRemapStep().apply(
                "com.example.GendustryTabs", writer.toByteArray())).accept(node, 0);
        var field = java.util.Arrays.stream(node.methods.get(0).instructions.toArray())
                .filter(org.objectweb.asm.tree.FieldInsnNode.class::isInstance)
                .map(org.objectweb.asm.tree.FieldInsnNode.class::cast).findFirst().orElseThrow();
        assertEquals(currentTabs, field.owner);
        assertEquals("FORESTRY", field.name);
        assertEquals("L" + currentFeatureTab + ";", field.desc);
    }
}
