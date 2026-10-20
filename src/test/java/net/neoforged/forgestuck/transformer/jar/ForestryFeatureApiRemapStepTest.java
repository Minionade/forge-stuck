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
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.LdcInsnNode;
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
    void rewritesDnaExtractorToReadLifeStageFromItemStack() {
        String handler = "forestry/api/genetics/capability/IIndividualHandlerItem";
        String itemStack = "net/minecraft/world/item/ItemStack";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC,
                "thedarkcolour/gendustry/blockentity/DnaExtractorBlockEntity", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "getRecipe",
                "(L" + itemStack + ";)Lthedarkcolour/gendustry/recipe/DnaRecipe;", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, handler, "get",
                "(L" + itemStack + ";)L" + handler + ";", true);
        method.visitVarInsn(Opcodes.ASTORE, 2);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, handler, "getStage",
                "()Lforestry/api/genetics/ILifeStage;", true);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        byte[] transformed = new ForestryFeatureApiRemapStep().apply(
                "thedarkcolour.gendustry.blockentity.DnaExtractorBlockEntity", writer.toByteArray());

        ClassNode node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);
        var calls = java.util.Arrays.stream(node.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        assertEquals(1, calls.size());
        assertEquals(Opcodes.INVOKESTATIC, calls.get(0).getOpcode());
        assertEquals("forestry/api/core/genetics/capability/IIndividualHandlerItem", calls.get(0).owner);
        assertEquals("getLifeStage", calls.get(0).name);
        assertEquals("(L" + itemStack + ";)Lforestry/api/core/genetics/ILifeStage;", calls.get(0).desc);
    }

    @Test
    void updatesGendustryMutatronJeiFilterToForestryMutationDisplay() {
        String legacyMutation = "forestry/apiculture/compat/MutationRecipe";
        String currentMutationDisplay = "forestry/apiculture/compat/MutationDisplay";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "thedarkcolour/gendustry/compat/jei/GendustryJeiPlugin",
                null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "isMutation",
                "(Lmezz/jei/api/recipe/RecipeType;)Z", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "mezz/jei/api/recipe/RecipeType",
                "getRecipeClass", "()Ljava/lang/Class;", false);
        method.visitLdcInsn(Type.getObjectType(legacyMutation));
        var notEqual = new org.objectweb.asm.Label();
        method.visitJumpInsn(Opcodes.IF_ACMPNE, notEqual);
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.IRETURN);
        method.visitLabel(notEqual);
        method.visitInsn(Opcodes.ICONST_0);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        ClassNode node = new ClassNode();
        new ClassReader(new ForestryFeatureApiRemapStep().apply(
                "thedarkcolour.gendustry.compat.jei.GendustryJeiPlugin", writer.toByteArray()))
                .accept(node, 0);
        LdcInsnNode classLiteral = java.util.Arrays.stream(node.methods.get(0).instructions.toArray())
                .filter(LdcInsnNode.class::isInstance).map(LdcInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(currentMutationDisplay, ((Type) classLiteral.cst).getInternalName());
    }

    @Test
    void addsPoweredMachineProgressPersistenceAndDirtyTracking() {
        String className = "exampleaddon/machine/PoweredTankBlockEntity";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, className, null,
                "forestry/core/tiles/TilePowered", null);
        writer.visitEnd();

        ClassNode node = transform(className, writer.toByteArray());
        assertTrue(hasCall(node, "saveAdditional", "net/neoforged/forgestuck/compat/LegacyPoweredMachineProgress",
                "save"));
        assertTrue(hasCall(node, "loadAdditional", "net/neoforged/forgestuck/compat/LegacyPoweredMachineProgress",
                "load"));
        assertTrue(hasCall(node, "serverTick", "net/neoforged/forgestuck/compat/LegacyPoweredMachineProgress",
                "markChangedIfProgressAdvanced"));

        String directMachine = "exampleaddon/machine/SamplerBlockEntity";
        ClassWriter directWriter = new ClassWriter(0);
        directWriter.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, directMachine, null,
                "forestry/core/tiles/TilePowered", null);
        directWriter.visitEnd();
        assertTrue(hasCall(transform(directMachine, directWriter.toByteArray()), "serverTick",
                "net/neoforged/forgestuck/compat/LegacyPoweredMachineProgress",
                "markChangedIfProgressAdvanced"));
    }

    @Test
    void augmentsExistingPoweredMachineSaveAndLoadMethods() {
        String className = "exampleaddon/machine/PoweredTankBlockEntity";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, className, null,
                "forestry/core/tiles/TilePowered", null);
        for (String methodName : new String[] {"saveAdditional", "loadAdditional"}) {
            var method = writer.visitMethod(Opcodes.ACC_PUBLIC, methodName,
                    "(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V",
                    null, null);
            method.visitCode();
            method.visitInsn(Opcodes.RETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
        }
        writer.visitEnd();

        ClassNode node = transform(className, writer.toByteArray());
        assertTrue(hasCall(node, "saveAdditional", "net/neoforged/forgestuck/compat/LegacyPoweredMachineProgress",
                "save"));
        assertTrue(hasCall(node, "loadAdditional", "net/neoforged/forgestuck/compat/LegacyPoweredMachineProgress",
                "load"));
    }

    @Test
    void wrapsExistingPoweredMachineTickToMarkProgressDirty() {
        String className = "exampleaddon/machine/CustomPoweredTile";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, className, null,
                "forestry/core/tiles/TilePowered", null);
        var tick = writer.visitMethod(Opcodes.ACC_PUBLIC, "serverTick",
                "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                        + "Lnet/minecraft/world/level/block/state/BlockState;)V", null, null);
        tick.visitCode();
        tick.visitInsn(Opcodes.RETURN);
        tick.visitMaxs(0, 4);
        tick.visitEnd();
        writer.visitEnd();

        ClassNode node = transform(className, writer.toByteArray());
        assertTrue(hasCall(node, "serverTick", "net/neoforged/forgestuck/compat/LegacyPoweredMachineProgress",
                "markChangedIfProgressAdvanced"));
        assertTrue(node.methods.stream().anyMatch(method -> method.name.equals("forgestuck$serverTick")));
    }

    @Test
    void resendsForestryTileMenuStateForProgressDisplay() {
        String className = "exampleaddon/menu/PoweredMachineMenu";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, className, null,
                "forestry/core/gui/ContainerLiquidTanks", null);
        writer.visitEnd();

        ClassNode node = transform(className, writer.toByteArray());
        assertTrue(hasCall(node, "forgestuck$syncGuiProgress",
                "forestry/core/platform/network/packets/PacketGuiStream",
                "<init>"));
        assertTrue(hasCall(node, "forgestuck$syncGuiProgress", "forestry/core/platform/gui/ContainerForestry",
                "sendPacketToListeners"));
        assertTrue(java.util.Arrays.stream(node.methods.stream()
                        .filter(method -> method.name.equals("forgestuck$syncGuiProgress"))
                        .findFirst().orElseThrow().instructions.toArray())
                .anyMatch(FrameNode.class::isInstance));
    }

    @Test
    void augmentsExistingForestryTileMenuBroadcastForProgress() {
        String className = "exampleaddon/menu/CustomMachineMenu";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, className, null,
                "forestry/core/gui/ContainerTile", null);
        var broadcast = writer.visitMethod(Opcodes.ACC_PUBLIC, "broadcastChanges", "()V", null, null);
        broadcast.visitCode();
        broadcast.visitInsn(Opcodes.RETURN);
        broadcast.visitMaxs(0, 1);
        broadcast.visitEnd();
        writer.visitEnd();

        ClassNode node = transform(className, writer.toByteArray());
        assertTrue(hasCall(node, "broadcastChanges", className, "forgestuck$syncGuiProgress"));
        assertTrue(hasCall(node, "forgestuck$syncGuiProgress",
                "forestry/core/platform/network/packets/PacketGuiStream", "<init>"));
    }

    private static ClassNode transform(String className, byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(new ForestryFeatureApiRemapStep().apply(
                className.replace('/', '.'), bytes)).accept(node, 0);
        return node;
    }

    private static boolean hasCall(ClassNode node, String methodName, String owner, String calledName) {
        return node.methods.stream()
                .filter(method -> method.name.equals(methodName))
                .flatMap(method -> java.util.Arrays.stream(method.instructions.toArray()))
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .anyMatch(call -> call.owner.equals(owner) && call.name.equals(calledName));
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
    void upgradesTankManagerStreamMethodsToRegistryFriendlyByteBuf() {
        String oldTankManager = "forestry/core/fluids/TankManager";
        String currentTankManager = "forestry/core/platform/fluids/TankManager";
        String friendlyBuffer = "net/minecraft/network/FriendlyByteBuf";
        String registryBuffer = "net/minecraft/network/RegistryFriendlyByteBuf";

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/GendustryTank", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "writeGuiData",
                "(L" + friendlyBuffer + ";)V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, oldTankManager, "writeData",
                "(L" + friendlyBuffer + ";)V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(2, 2);
        method.visitEnd();
        var readMethod = writer.visitMethod(Opcodes.ACC_PUBLIC, "readGuiData",
                "(L" + friendlyBuffer + ";)V", null, null);
        readMethod.visitCode();
        readMethod.visitVarInsn(Opcodes.ALOAD, 0);
        readMethod.visitVarInsn(Opcodes.ALOAD, 1);
        readMethod.visitMethodInsn(Opcodes.INVOKEVIRTUAL, oldTankManager, "readData",
                "(L" + friendlyBuffer + ";)V", false);
        readMethod.visitInsn(Opcodes.RETURN);
        readMethod.visitMaxs(2, 2);
        readMethod.visitEnd();
        String oldTilePowered = "forestry/core/tiles/TilePowered";
        for (String methodName : new String[] {"writeData", "readData"}) {
            var streamMethod = writer.visitMethod(Opcodes.ACC_PUBLIC, methodName,
                    "(L" + friendlyBuffer + ";)V", null, null);
            streamMethod.visitCode();
            streamMethod.visitVarInsn(Opcodes.ALOAD, 0);
            streamMethod.visitVarInsn(Opcodes.ALOAD, 1);
            streamMethod.visitMethodInsn(Opcodes.INVOKESPECIAL, oldTilePowered, methodName,
                    "(L" + friendlyBuffer + ";)V", false);
            streamMethod.visitVarInsn(Opcodes.ALOAD, 0);
            streamMethod.visitVarInsn(Opcodes.ALOAD, 1);
            streamMethod.visitMethodInsn(Opcodes.INVOKEVIRTUAL, oldTankManager, methodName,
                    "(L" + friendlyBuffer + ";)V", false);
            streamMethod.visitInsn(Opcodes.RETURN);
            streamMethod.visitMaxs(3, 2);
            streamMethod.visitEnd();
        }
        writer.visitEnd();

        ClassNode node = new ClassNode();
        new ClassReader(new ForestryFeatureApiRemapStep().apply(
                "com.example.GendustryTank", writer.toByteArray())).accept(node, 0);
        assertEquals(4, node.methods.size());
        for (var streamMethod : node.methods) {
            assertEquals("(L" + registryBuffer + ";)V", streamMethod.desc);
        }
        var calls = node.methods.stream().flatMap(methodNode ->
                        java.util.Arrays.stream(methodNode.instructions.toArray()))
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .filter(call -> call.owner.equals(currentTankManager)).toList();
        assertEquals(4, calls.size());
        assertEquals(2, calls.stream().filter(call -> call.name.equals("writeData")).count());
        assertEquals(2, calls.stream().filter(call -> call.name.equals("readData")).count());
        assertTrue(calls.stream().allMatch(call -> call.desc.equals("(L" + registryBuffer + ";)V")));
        var superCalls = node.methods.stream().flatMap(methodNode ->
                        java.util.Arrays.stream(methodNode.instructions.toArray()))
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .filter(call -> call.owner.equals("forestry/core/platform/tile/TilePowered")).toList();
        assertEquals(2, superCalls.size());
        assertTrue(superCalls.stream().allMatch(call -> call.desc.equals("(L" + registryBuffer + ";)V")));
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
    void convertsLegacyWorkCycleTicksToForestrySteps() {
        String oldPoweredTile = "forestry/core/tiles/TilePowered";
        String currentPoweredTile = "forestry/core/platform/tile/TilePowered";
        String gendustryTile = "thedarkcolour/gendustry/blockentity/MutagenProducerBlockEntity";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, gendustryTile, null, oldPoweredTile, null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "startWorking", "()V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitIntInsn(Opcodes.SIPUSH, 200);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, gendustryTile,
                "setTicksPerWorkCycle", "(I)V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(2, 1);
        method.visitEnd();
        writer.visitEnd();

        ClassNode node = new ClassNode();
        new ClassReader(new ForestryFeatureApiRemapStep().apply(
                "thedarkcolour.gendustry.blockentity.MutagenProducerBlockEntity",
                writer.toByteArray())).accept(node, 0);
        var instructions = node.methods.get(0).instructions.toArray();
        MethodInsnNode call = java.util.Arrays.stream(instructions)
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals("net/neoforged/forgestuck/compat/LegacyPoweredMachineProgress", call.owner);
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("setStepsPerWorkCycle", call.name);
        assertEquals("(Lnet/minecraft/world/level/block/entity/BlockEntity;I)V", call.desc);
        assertEquals(Opcodes.IDIV, call.getPrevious().getOpcode());
        assertEquals(Opcodes.ICONST_5, call.getPrevious().getPrevious().getOpcode());
        assertTrue(node.methods.get(0).maxStack >= 3);
    }

    @Test
    void remapsLegacyStringRepresentableCallOnLifeStages() {
        String legacyLifeStage = "forestry/api/genetics/ILifeStage";
        String currentLifeStage = "forestry/api/core/genetics/ILifeStage";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/LegacyLifeStageName", null,
                "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "name",
                "(L" + legacyLifeStage + ";)Ljava/lang/String;", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(
                Opcodes.INVOKEINTERFACE, legacyLifeStage, "m_7912_", "()Ljava/lang/String;", true);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();

        ClassNode node = new ClassNode();
        new ClassReader(new ForestryFeatureApiRemapStep().apply(
                "com.example.LegacyLifeStageName", writer.toByteArray())).accept(node, 0);
        MethodInsnNode call = java.util.Arrays.stream(node.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(currentLifeStage, call.owner);
        assertEquals("getSerializedName", call.name);
        assertEquals("()Ljava/lang/String;", call.desc);
    }

    @Test
    void bridgesGendustryAlleleAndCreativeTabApis() {
        String oldAllele = "forestry/api/genetics/alleles/IAllele";
        String currentAllele = "forestry/api/core/genetics/alleles/Allele";
        String helper = "net/neoforged/forgestuck/compat/LegacyForestryGenetics";
        String itemStack = "net/minecraft/world/item/ItemStack";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "thedarkcolour/gendustry/item/GeneSampleItem", null,
                "java/lang/Object", null);

        var allele = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "getAllele",
                "(L" + itemStack + ";)L" + oldAllele + ";", null, null);
        allele.visitCode();
        allele.visitInsn(Opcodes.ACONST_NULL);
        allele.visitInsn(Opcodes.ARETURN);
        allele.visitMaxs(1, 1);
        allele.visitEnd();

        var encode = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "createStack",
                "(Ljava/lang/Object;Lforestry/api/genetics/alleles/IChromosome;L" + oldAllele + ";)"
                        + "L" + itemStack + ";",
                null, null);
        encode.visitCode();
        encode.visitVarInsn(Opcodes.ALOAD, 2);
        encode.visitMethodInsn(Opcodes.INVOKEINTERFACE, oldAllele, "alleleId",
                "()Lnet/minecraft/resources/ResourceLocation;", true);
        encode.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/resources/ResourceLocation", "toString",
                "()Ljava/lang/String;", false);
        encode.visitInsn(Opcodes.POP);
        encode.visitInsn(Opcodes.ACONST_NULL);
        encode.visitInsn(Opcodes.ARETURN);
        encode.visitMaxs(1, 3);
        encode.visitEnd();
        writer.visitEnd();

        ClassNode node = new ClassNode();
        new ClassReader(new ForestryFeatureApiRemapStep().apply(
                "thedarkcolour.gendustry.item.GeneSampleItem", writer.toByteArray())).accept(node, 0);
        var transformedGetAllele = node.methods.stream()
                .filter(method -> method.name.equals("getAllele")).findFirst().orElseThrow();
        assertEquals("(L" + itemStack + ";)L" + currentAllele + ";", transformedGetAllele.desc);
        var helperCall = java.util.Arrays.stream(transformedGetAllele.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(helper, helperCall.owner);
        assertEquals("geneSampleAllele", helperCall.name);

        var transformedEncode = node.methods.stream()
                .filter(method -> method.name.equals("createStack")).findFirst().orElseThrow();
        assertTrue(transformedEncode.maxStack >= 2);
        var calls = java.util.Arrays.stream(transformedEncode.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        MethodInsnNode alleleId = calls.stream().filter(call -> call.name.equals("encodeAllele"))
                .findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, alleleId.getOpcode());
        assertEquals("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/String;", alleleId.desc);
        assertTrue(calls.stream().anyMatch(call -> call.owner.equals("java/lang/String")
                && call.name.equals("toString")));

        ClassWriter groupWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        groupWriter.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "thedarkcolour/gendustry/registry/CreativeItems", null,
                "java/lang/Object", null);
        var groupMethod = groupWriter.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "blocks",
                "(Lforestry/modules/features/FeatureBlockGroup;)Ljava/util/Collection;", null, null);
        groupMethod.visitCode();
        groupMethod.visitVarInsn(Opcodes.ALOAD, 0);
        groupMethod.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "forestry/modules/features/FeatureBlockGroup",
                "getBlocks", "()Ljava/util/Collection;", false);
        groupMethod.visitInsn(Opcodes.ARETURN);
        groupMethod.visitMaxs(1, 1);
        groupMethod.visitEnd();
        groupWriter.visitEnd();
        ClassNode groupNode = new ClassNode();
        new ClassReader(new ForestryFeatureApiRemapStep().apply(
                "thedarkcolour.gendustry.registry.CreativeItems", groupWriter.toByteArray()))
                .accept(groupNode, 0);
        MethodInsnNode blocks = java.util.Arrays.stream(groupNode.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals(helper, blocks.owner);
        assertEquals("featureBlocks", blocks.name);
        assertEquals("(Ljava/lang/Object;)Ljava/util/List;", blocks.desc);
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
    void remapsLegacyMailTabReferenceToForestryMailAddon() {
        String oldTabs = "forestry/core/tab/ForestryCreativeTabs";
        String mailTabs = "forestry/mail/tab/MailCreativeTab";
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
        assertEquals(mailTabs, field.owner);
        assertEquals("MAIL", field.name);
        assertEquals("L" + currentFeatureTab + ";", field.desc);
    }
}
