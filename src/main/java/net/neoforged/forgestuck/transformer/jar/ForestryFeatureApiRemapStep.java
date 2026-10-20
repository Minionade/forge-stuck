package net.neoforged.forgestuck.transformer.jar;

import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Adapts Gendustry's 1.20 Forestry feature API package and signatures to Forestry 1.21.1. */
public final class ForestryFeatureApiRemapStep implements ClassTransformStep {
    private static final String LEGACY_PACKAGE = "forestry/modules/features/";
    private static final String CURRENT_PACKAGE = "forestry/core/platform/registration/";
    private static final String LEGACY_REGISTRY = LEGACY_PACKAGE + "ModFeatureRegistry";
    private static final String LEGACY_FEATURE_REGISTRY = LEGACY_PACKAGE + "IFeatureRegistry";
    private static final String LEGACY_FACTORY = "net/neoforged/forgestuck/compat/LegacyContainerFactory";
    private static final String LEGACY_DEFERRED_REGISTER =
            "net/neoforged/forgestuck/compat/LegacyDeferredRegister";
    private static final String CONTAINER_FACTORY = "net/neoforged/neoforge/network/IContainerFactory";
    private static final String DEFERRED_REGISTER = "net/neoforged/neoforge/registries/DeferredRegister";
    private static final String FRIENDLY_BYTE_BUF = "net/minecraft/network/FriendlyByteBuf";
    private static final String REGISTRY_FRIENDLY_BYTE_BUF = "net/minecraft/network/RegistryFriendlyByteBuf";
    private static final String LEGACY_PACKET_CLIENT = "forestry/api/modules/IForestryPacketClient";
    private static final String LEGACY_INDIVIDUAL_HANDLER =
            "forestry/api/genetics/capability/IIndividualHandlerItem";
    private static final String CURRENT_INDIVIDUAL_HANDLER =
            "forestry/api/core/genetics/capability/IIndividualHandlerItem";
    private static final String CUSTOM_PACKET_PAYLOAD =
            "net/minecraft/network/protocol/common/custom/CustomPacketPayload";
    private static final String BLOCK_ITEM_FACTORY_ADAPTER =
            "net/neoforged/forgestuck/compat/LegacyForestryBlockItemFactory";
    private static final String LEGACY_GENETICS =
            "net/neoforged/forgestuck/compat/LegacyForestryGenetics";
    private static final String GENE_SAMPLE_ITEM = "thedarkcolour/gendustry/item/GeneSampleItem";
    private static final String GENETIC_TEMPLATE_ITEM = "thedarkcolour/gendustry/item/GeneticTemplateItem";
    private static final String GENDUSTRY_JEI_PLUGIN = "thedarkcolour/gendustry/compat/jei/GendustryJeiPlugin";
    private static final String LEGACY_MUTATION_RECIPE = "forestry/apiculture/compat/MutationRecipe";
    private static final String CURRENT_MUTATION_DISPLAY = "forestry/apiculture/compat/MutationDisplay";
    private static final String LEGACY_POWERED_PROGRESS =
            "net/neoforged/forgestuck/compat/LegacyPoweredMachineProgress";
    private static final String TILE_POWERED = "forestry/core/tiles/TilePowered";
    private static final String CONTAINER_TILE = "forestry/core/gui/ContainerTile";
    private static final String CONTAINER_FORESTRY = "forestry/core/gui/ContainerForestry";
    private static final String STREAMABLE_GUI = "forestry/core/network/IStreamableGui";
    private static final String PACKET_GUI_STREAM = "forestry/core/network/packets/PacketGuiStream";
    private static final String COMPOUND_TAG = "net/minecraft/nbt/CompoundTag";
    private static final String HOLDER_PROVIDER = "net/minecraft/core/HolderLookup$Provider";
    private static final String LEVEL = "net/minecraft/world/level/Level";
    private static final String BLOCK_POS = "net/minecraft/core/BlockPos";
    private static final String BLOCK_STATE = "net/minecraft/world/level/block/state/BlockState";
    private static final String BLOCK_ENTITY = "net/minecraft/world/level/block/entity/BlockEntity";
    private static final String SAVED_DATA_DESC =
            "(L" + COMPOUND_TAG + ";L" + HOLDER_PROVIDER + ";)V";
    private static final String SERVER_TICK_DESC =
            "(L" + LEVEL + ";L" + BLOCK_POS + ";L" + BLOCK_STATE + ";)V";
    private static final String ITEM_STACK = "net/minecraft/world/item/ItemStack";
    private static final String MODERN_ALLELE = "forestry/api/core/genetics/alleles/Allele";
    private static final String OLD_ALLELE = "forestry/api/genetics/alleles/IAllele";
    private static final Map<String, String> EXACT_MOVES = Map.of(
            "forestry/core/tiles/IFilterSlotDelegate", "forestry/api/core/IFilterSlotDelegate",
            "forestry/core/inventory/IInventoryAdapter", "forestry/api/core/IInventoryAdapter",
            "forestry/core/ClientsideCode", "forestry/core/platform/client/ClientsideCode",
            "forestry/api/recipes/IForestryRecipe", "forestry/api/core/machines/IForestryRecipe",
            OLD_ALLELE, MODERN_ALLELE,
            "forestry/apiculture/compat/MutationRecipe",
            "forestry/core/engine/genetics/mutations/MutationRecipe");
    private static final Map<String, String> PACKAGE_MOVES = Map.ofEntries(
            Map.entry(LEGACY_PACKAGE, CURRENT_PACKAGE),
            Map.entry("forestry/api/climate/", "forestry/api/core/climate/"),
            Map.entry("forestry/api/genetics/", "forestry/api/core/genetics/"),
            Map.entry("forestry/apiculture/gui/", "forestry/apiculture/bees/"),
            Map.entry("forestry/core/blocks/", "forestry/core/platform/block/"),
            Map.entry("forestry/core/fluids/", "forestry/core/platform/fluids/"),
            Map.entry("forestry/core/gui/", "forestry/core/platform/gui/"),
            Map.entry("forestry/core/items/", "forestry/core/platform/item/"),
            Map.entry("forestry/core/network/", "forestry/core/platform/network/"),
            Map.entry("forestry/core/owner/", "forestry/core/platform/owner/"),
            Map.entry("forestry/core/recipes/jei/", "forestry/core/platform/recipes/jei/"),
            Map.entry("forestry/core/render/", "forestry/core/platform/render/"),
            Map.entry("forestry/core/tab/", "forestry/core/platform/tab/"),
            Map.entry("forestry/core/tiles/", "forestry/core/platform/tile/"),
            Map.entry("forestry/core/utils/", "forestry/core/platform/util/"),
            Map.entry("forestry/energy/", "forestry/core/content/energy/"));

    @Override
    public byte[] apply(String className, byte[] bytes) {
        if (bytes == null) return bytes;
        String raw = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        if (EXACT_MOVES.keySet().stream().noneMatch(raw::contains)
                && PACKAGE_MOVES.keySet().stream().noneMatch(raw::contains)
                && !raw.contains("forestry/core/inventory/InventoryAdapterTile")
                && !raw.contains("forestry/api/core/genetics/ILifeStage")
                && !raw.contains(TILE_POWERED)) {
            return bytes;
        }

        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        for (MethodNode method : node.methods) {
            if (rewriteGeneticsMethod(node.name, method)) continue;
            rewriteStreamSignature(method);
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode call) {
                    rewriteCall(node.name, method, call);
                } else if (instruction instanceof FieldInsnNode field) {
                    rewriteField(field);
                } else if (instruction instanceof InvokeDynamicInsnNode indy) {
                    for (int i = 0; i < indy.bsmArgs.length; i++) {
                        if (indy.bsmArgs[i] instanceof Handle handle) {
                            Handle replacement = rewriteRegistryGet(handle);
                            if (replacement != null) {
                                indy.bsmArgs[i] = replacement;
                            }
                        }
                    }
                } else if (instruction instanceof LdcInsnNode ldc) {
                    if (ldc.cst instanceof Handle handle) {
                        Handle replacement = rewriteRegistryGet(handle);
                        if (replacement != null) {
                            ldc.cst = replacement;
                        }
                    } else if (node.name.equals(GENDUSTRY_JEI_PLUGIN)
                            && ldc.cst instanceof Type type
                            && type.getSort() == Type.OBJECT
                            && type.getInternalName().equals(LEGACY_MUTATION_RECIPE)) {
                        ldc.cst = Type.getObjectType(CURRENT_MUTATION_DISPLAY);
                    }
                }
            }
        }
        if (needsPoweredProgressHooks(node)) {
            addPoweredProgressHooks(node);
        }
        if (needsGuiProgressSync(node)) {
            addGuiProgressSync(node);
        }
        Remapper packageRemapper = new Remapper() {
            @Override
            public String map(String internalName) {
                String exact = EXACT_MOVES.get(internalName);
                if (exact != null) {
                    return exact;
                }
                for (Map.Entry<String, String> move : PACKAGE_MOVES.entrySet()) {
                    if (internalName.startsWith(move.getKey())) {
                        return move.getValue() + internalName.substring(move.getKey().length());
                    }
                }
                if (internalName.equals("forestry/core/inventory/InventoryAdapterTile")) {
                    return "forestry/core/platform/inventory/InventoryAdapterTile";
                }
                return internalName;
            }
        };
        ClassWriter writer = new ClassWriter(0);
        node.accept(new ClassRemapper(writer, packageRemapper));
        return writer.toByteArray();
    }

    private static boolean needsPoweredProgressHooks(ClassNode node) {
        return TILE_POWERED.equals(node.superName);
    }

    private static boolean needsGuiProgressSync(ClassNode node) {
        return CONTAINER_TILE.equals(node.superName)
                || (node.superName != null
                        && node.superName.startsWith("forestry/core/gui/ContainerLiquidTanks"));
    }

    private static void addPoweredProgressHooks(ClassNode node) {
        String superName = node.superName;
        MethodNode existingSave = findMethod(node, "saveAdditional", SAVED_DATA_DESC);
        if (existingSave == null) {
            MethodNode saveMethod = new MethodNode(Opcodes.ACC_PUBLIC, "saveAdditional", SAVED_DATA_DESC, null, null);
            saveMethod.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            saveMethod.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
            saveMethod.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
            saveMethod.instructions.add(new MethodInsnNode(
                    Opcodes.INVOKESPECIAL, superName, "saveAdditional", SAVED_DATA_DESC, false));
            saveMethod.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            saveMethod.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
            saveMethod.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
            saveMethod.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, LEGACY_POWERED_PROGRESS, "save",
                    "(Lnet/minecraft/world/level/block/entity/BlockEntity;L"
                            + COMPOUND_TAG + ";L" + HOLDER_PROVIDER + ";)V", false));
            saveMethod.instructions.add(new InsnNode(Opcodes.RETURN));
            saveMethod.maxLocals = 3;
            saveMethod.maxStack = 3;
            node.methods.add(saveMethod);
        } else {
            addProgressPersistenceCall(existingSave, "save");
        }
        MethodNode existingLoad = findMethod(node, "loadAdditional", SAVED_DATA_DESC);
        if (existingLoad == null) {
            MethodNode loadMethod = new MethodNode(Opcodes.ACC_PUBLIC, "loadAdditional", SAVED_DATA_DESC, null, null);
            loadMethod.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            loadMethod.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
            loadMethod.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
            loadMethod.instructions.add(new MethodInsnNode(
                    Opcodes.INVOKESPECIAL, superName, "loadAdditional", SAVED_DATA_DESC, false));
            loadMethod.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            loadMethod.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
            loadMethod.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
            loadMethod.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, LEGACY_POWERED_PROGRESS, "load",
                    "(Lnet/minecraft/world/level/block/entity/BlockEntity;L"
                            + COMPOUND_TAG + ";L" + HOLDER_PROVIDER + ";)V", false));
            loadMethod.instructions.add(new InsnNode(Opcodes.RETURN));
            loadMethod.maxLocals = 3;
            loadMethod.maxStack = 3;
            node.methods.add(loadMethod);
        } else {
            addProgressPersistenceCall(existingLoad, "load");
        }
        addPoweredTickWrapper(node);
    }

    private static void addPoweredTickWrapper(ClassNode node) {
        MethodNode existingTick = findMethod(node, "serverTick", SERVER_TICK_DESC);
        String originalName = "forgestuck$serverTick";
        if (existingTick != null) {
            while (hasMethodNamed(node, originalName)) {
                originalName += "$";
            }
            existingTick.name = originalName;
            existingTick.access = (existingTick.access & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED))
                    | Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC;
        }
        MethodNode tick = new MethodNode(Opcodes.ACC_PUBLIC, "serverTick", SERVER_TICK_DESC, null, null);
        tick.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        tick.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TILE_POWERED, "getWorkCounter", "()I", false));
        tick.instructions.add(new VarInsnNode(Opcodes.ISTORE, 4));
        tick.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        tick.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        tick.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        tick.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
        tick.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                existingTick == null ? node.superName : node.name,
                existingTick == null ? "serverTick" : originalName, SERVER_TICK_DESC, false));
        tick.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        tick.instructions.add(new VarInsnNode(Opcodes.ILOAD, 4));
        tick.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, LEGACY_POWERED_PROGRESS,
                "markChangedIfProgressAdvanced",
                "(Lnet/minecraft/world/level/block/entity/BlockEntity;I)V", false));
        tick.instructions.add(new InsnNode(Opcodes.RETURN));
        tick.maxLocals = 5;
        tick.maxStack = 4;
        node.methods.add(tick);
    }

    private static MethodNode findMethod(ClassNode node, String name, String descriptor) {
        return node.methods.stream()
                .filter(method -> method.name.equals(name) && method.desc.equals(descriptor))
                .findFirst().orElse(null);
    }

    private static boolean hasMethodNamed(ClassNode node, String name) {
        return node.methods.stream().anyMatch(method -> method.name.equals(name));
    }

    private static void addProgressPersistenceCall(MethodNode method, String operation) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction.getOpcode() == Opcodes.RETURN) {
                InsnList call = new InsnList();
                call.add(new VarInsnNode(Opcodes.ALOAD, 0));
                call.add(new VarInsnNode(Opcodes.ALOAD, 1));
                call.add(new VarInsnNode(Opcodes.ALOAD, 2));
                call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, LEGACY_POWERED_PROGRESS, operation,
                        "(Lnet/minecraft/world/level/block/entity/BlockEntity;L" + COMPOUND_TAG + ";L"
                                + HOLDER_PROVIDER + ";)V", false));
                method.instructions.insertBefore(instruction, call);
            }
        }
        method.maxStack = Math.max(method.maxStack, 2);
    }

    private static void addGuiProgressSync(ClassNode node) {
        MethodNode broadcast = findMethod(node, "broadcastChanges", "()V");
        if (broadcast == null) {
            broadcast = new MethodNode(Opcodes.ACC_PUBLIC, "broadcastChanges", "()V", null, null);
            broadcast.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            broadcast.instructions.add(new MethodInsnNode(
                    Opcodes.INVOKESPECIAL, node.superName, "broadcastChanges", "()V", false));
            broadcast.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            broadcast.instructions.add(new MethodInsnNode(
                    Opcodes.INVOKESPECIAL, node.name, "forgestuck$syncGuiProgress", "()V", false));
            broadcast.instructions.add(new InsnNode(Opcodes.RETURN));
            broadcast.maxLocals = 1;
            broadcast.maxStack = 1;
            node.methods.add(broadcast);
        } else {
            for (AbstractInsnNode instruction : broadcast.instructions.toArray()) {
                if (instruction.getOpcode() == Opcodes.RETURN) {
                    broadcast.instructions.insertBefore(instruction, new VarInsnNode(Opcodes.ALOAD, 0));
                    broadcast.instructions.insertBefore(instruction,
                            new MethodInsnNode(Opcodes.INVOKESPECIAL, node.name,
                                    "forgestuck$syncGuiProgress", "()V", false));
                }
            }
        }
        MethodNode sync = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC,
                "forgestuck$syncGuiProgress", "()V", null, null);
        appendGuiProgressSend(sync.instructions);
        sync.instructions.add(new InsnNode(Opcodes.RETURN));
        sync.maxLocals = 1;
        sync.maxStack = 5;
        node.methods.add(sync);
    }

    private static void appendGuiProgressSend(InsnList instructions) {
        LabelNode end = new LabelNode();
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL, CONTAINER_TILE, "getTile", "()L" + BLOCK_ENTITY + ";", false));
        instructions.add(new TypeInsnNode(Opcodes.INSTANCEOF, STREAMABLE_GUI));
        instructions.add(new JumpInsnNode(Opcodes.IFEQ, end));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL, CONTAINER_TILE, "getTile", "()L" + BLOCK_ENTITY + ";", false));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL, BLOCK_ENTITY, "getLevel", "()L" + LEVEL + ";", false));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL, LEVEL, "getGameTime", "()J", false));
        instructions.add(new LdcInsnNode(5L));
        instructions.add(new InsnNode(Opcodes.LREM));
        instructions.add(new InsnNode(Opcodes.LCONST_0));
        instructions.add(new InsnNode(Opcodes.LCMP));
        instructions.add(new JumpInsnNode(Opcodes.IFNE, end));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(new TypeInsnNode(Opcodes.NEW, PACKET_GUI_STREAM));
        instructions.add(new InsnNode(Opcodes.DUP));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL, CONTAINER_TILE, "getTile", "()L" + BLOCK_ENTITY + ";", false));
        instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, STREAMABLE_GUI));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, PACKET_GUI_STREAM, "<init>", "(L" + STREAMABLE_GUI + ";)V", false));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL, CONTAINER_FORESTRY, "sendPacketToListeners",
                "(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V", false));
        instructions.add(end);
        instructions.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
    }

    private static boolean rewriteGeneticsMethod(String className, MethodNode method) {
        String itemStack = "(L" + ITEM_STACK + ";)";
        if (className.equals(GENE_SAMPLE_ITEM)) {
            if (method.name.equals("getChromosome") && method.desc.equals(itemStack
                    + "Lforestry/api/genetics/alleles/IChromosome;")) {
                replaceBody(method, LEGACY_GENETICS, "geneSampleChromosome",
                        itemStack + "Ljava/lang/Object;", true,
                        "forestry/api/genetics/alleles/IChromosome");
                return true;
            }
            if (method.name.equals("getAllele") && method.desc.equals(itemStack + "L" + OLD_ALLELE + ";")) {
                replaceBody(method, LEGACY_GENETICS, "geneSampleAllele",
                        itemStack + "Ljava/lang/Object;", true, MODERN_ALLELE);
                return true;
            }
        }
        if (className.equals(GENETIC_TEMPLATE_ITEM)) {
            if (method.name.equals("addAlleles") && method.desc.equals(itemStack + "Ljava/util/Map;)V")) {
                replaceBody(method, LEGACY_GENETICS, "addTemplateAlleles",
                        itemStack + "Ljava/util/Map;)V", false, null);
                return true;
            }
            if (method.name.equals("getAlleles") && method.desc.equals(itemStack + "Ljava/util/Map;")) {
                replaceBody(method, LEGACY_GENETICS, "templateAlleles",
                        itemStack + "Ljava/util/Map;", true, "java/util/Map");
                return true;
            }
        }
        return false;
    }

    private static void replaceBody(
            MethodNode method, String owner, String name, String descriptor,
            boolean castResult, String resultType) {
        method.instructions.clear();
        method.tryCatchBlocks.clear();
        method.localVariables = null;
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        if (descriptor.contains("Ljava/util/Map;)")) {
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        }
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC, owner, name, descriptor, false));
        if (castResult) {
            method.instructions.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.CHECKCAST, resultType));
        }
        method.instructions.add(new InsnNode(
                method.desc.endsWith(")V") ? Opcodes.RETURN : Opcodes.ARETURN));
        method.maxLocals = descriptor.contains("Ljava/util/Map;)") ? 2 : 1;
        method.maxStack = method.maxLocals;
    }

    private static void rewriteStreamSignature(MethodNode method) {
        if (isStreamMethod(method.name) && method.desc.equals("(L" + FRIENDLY_BYTE_BUF + ";)V")) {
            method.desc = "(L" + REGISTRY_FRIENDLY_BYTE_BUF + ";)V";
        }
    }

    private static boolean isStreamMethod(String name) {
        return name.equals("writeData") || name.equals("readData")
                || name.equals("writeGuiData") || name.equals("readGuiData");
    }

    private static boolean rewriteCall(String className, MethodNode method, MethodInsnNode call) {
        if (className.equals("thedarkcolour/gendustry/blockentity/DnaExtractorBlockEntity")
                && method.name.equals("getRecipe")
                && call.owner.equals(LEGACY_INDIVIDUAL_HANDLER)) {
            if (call.name.equals("get")
                    && call.desc.equals("(L" + ITEM_STACK + ";)L" + LEGACY_INDIVIDUAL_HANDLER + ";")) {
                call.setOpcode(Opcodes.INVOKESTATIC);
                call.owner = CURRENT_INDIVIDUAL_HANDLER;
                call.name = "getLifeStage";
                call.desc = "(L" + ITEM_STACK + ";)Lforestry/api/core/genetics/ILifeStage;";
                call.itf = true;
                return true;
            }
            if (call.name.equals("getStage")
                    && call.desc.equals("()Lforestry/api/genetics/ILifeStage;")) {
                method.instructions.remove(call);
                return true;
            }
        }
        if (call.owner.equals("forestry/core/blocks/BlockBase")
                && call.name.equals("<init>")
                && call.desc.equals("(Ljava/lang/Enum;Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)V")) {
            int propertiesLocal = method.maxLocals++;
            method.instructions.insertBefore(call, new VarInsnNode(Opcodes.ASTORE, propertiesLocal));
            method.instructions.insertBefore(call, new VarInsnNode(Opcodes.ALOAD, propertiesLocal));
            method.instructions.insertBefore(call, new InsnNode(Opcodes.SWAP));
            call.desc = "(Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;Ljava/lang/Enum;)V";
            return true;
        }
        if (call.owner.equals("forestry/core/blocks/IMachineProperties")
                && call.name.equals("m_7912_")
                && call.desc.equals("()Ljava/lang/String;")) {
            call.name = "getSerializedName";
            return true;
        }
        if (call.name.equals("setTicksPerWorkCycle") && call.desc.equals("(I)V")) {
            method.instructions.insertBefore(call, new InsnNode(Opcodes.ICONST_5));
            method.instructions.insertBefore(call, new InsnNode(Opcodes.IDIV));
            method.maxStack++;
            call.setOpcode(Opcodes.INVOKESTATIC);
            call.owner = LEGACY_POWERED_PROGRESS;
            call.name = "setStepsPerWorkCycle";
            call.desc = "(L" + BLOCK_ENTITY + ";I)V";
            call.itf = false;
            return true;
        }
        if ((call.owner.equals("forestry/api/genetics/ILifeStage")
                        || call.owner.equals("forestry/api/core/genetics/ILifeStage"))
                && call.name.equals("m_7912_")
                && call.desc.equals("()Ljava/lang/String;")) {
            call.name = "getSerializedName";
            return true;
        }
        if (call.owner.equals(OLD_ALLELE)) {
            if (call.name.equals("cast") && call.desc.equals("()L" + OLD_ALLELE + ";")) {
                method.instructions.remove(call);
                return true;
            }
            if (call.name.equals("dominant") && call.desc.equals("()Z")) {
                call.owner = MODERN_ALLELE;
                call.setOpcode(Opcodes.INVOKEVIRTUAL);
                call.itf = false;
                return true;
            }
            if (call.name.equals("alleleId") && call.desc.equals("()Lnet/minecraft/resources/ResourceLocation;")
                    && method.name.equals("createStack")) {
                method.instructions.insertBefore(call, new VarInsnNode(Opcodes.ALOAD, 1));
                method.maxStack++;
                call.setOpcode(Opcodes.INVOKESTATIC);
                call.owner = LEGACY_GENETICS;
                call.name = "encodeAllele";
                call.desc = "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/String;";
                call.itf = false;
                AbstractInsnNode next = call.getNext();
                if (next instanceof MethodInsnNode toString
                        && toString.owner.equals("net/minecraft/resources/ResourceLocation")
                        && toString.name.equals("toString")) {
                    toString.owner = "java/lang/String";
                    toString.setOpcode(Opcodes.INVOKEVIRTUAL);
                }
                return true;
            }
        }
        if (call.owner.equals("forestry/api/genetics/alleles/IKaryotype")
                && call.name.equals("getAlleles")
                && call.desc.equals("(Lforestry/api/genetics/alleles/IChromosome;)Ljava/util/Collection;")) {
            call.setOpcode(Opcodes.INVOKESTATIC);
            call.owner = LEGACY_GENETICS;
            call.name = "defaultAlleles";
            call.desc = "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;";
            call.itf = false;
            return true;
        }
        if (call.owner.equals("forestry/api/genetics/alleles/IChromosome")
                && call.name.equals("getChromosomeDisplayName")
                && call.desc.equals("()Lnet/minecraft/network/chat/MutableComponent;")) {
            call.setOpcode(Opcodes.INVOKESTATIC);
            call.owner = LEGACY_GENETICS;
            call.name = "chromosomeName";
            call.desc = "(Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;";
            call.itf = false;
            return true;
        }
        if (call.owner.equals("forestry/api/genetics/alleles/IChromosome")
                && call.name.equals("getDisplayName")
                && call.desc.equals("(L" + OLD_ALLELE + ";)Lnet/minecraft/network/chat/MutableComponent;")) {
            call.setOpcode(Opcodes.INVOKESTATIC);
            call.owner = LEGACY_GENETICS;
            call.name = "alleleName";
            call.desc = "(Ljava/lang/Object;Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;";
            call.itf = false;
            return true;
        }
        if (call.owner.equals("forestry/modules/features/FeatureBlockGroup")
                && call.name.equals("getBlocks") && call.desc.equals("()Ljava/util/Collection;")) {
            call.setOpcode(Opcodes.INVOKESTATIC);
            call.owner = LEGACY_GENETICS;
            call.name = "featureBlocks";
            call.desc = "(Ljava/lang/Object;)Ljava/util/List;";
            call.itf = false;
            return true;
        }
        if (call.name.equals("sendPacketToListeners")
                && call.desc.equals("(L" + LEGACY_PACKET_CLIENT + ";)V")) {
            call.desc = "(L" + CUSTOM_PACKET_PAYLOAD + ";)V";
            return true;
        }
        if (isStreamMethod(call.name)
                && call.desc.equals("(L" + FRIENDLY_BYTE_BUF + ";)V")) {
            call.desc = "(L" + REGISTRY_FRIENDLY_BYTE_BUF + ";)V";
            return true;
        }
        String legacyBlockGroupBuilder = LEGACY_PACKAGE + "FeatureBlockGroup$Builder";
        if (call.owner.equals(legacyBlockGroupBuilder) && call.name.equals("item")
                && call.desc.equals("(Ljava/util/function/Function;)L" + legacyBlockGroupBuilder + ";")) {
            method.instructions.insertBefore(call, new MethodInsnNode(Opcodes.INVOKESTATIC,
                    BLOCK_ITEM_FACTORY_ADAPTER, "adapt",
                    "(Ljava/util/function/Function;)Ljava/util/function/BiFunction;", false));
            call.desc = "(Ljava/util/function/BiFunction;)L" + legacyBlockGroupBuilder + ";";
            return true;
        }
        if (call.owner.equals(LEGACY_REGISTRY) && call.name.equals("get")
                && call.desc.equals("(Lnet/minecraft/resources/ResourceLocation;)L"
                        + LEGACY_FEATURE_REGISTRY + ";")) {
            call.desc = "(Lnet/minecraft/resources/ResourceLocation;)L"
                    + CURRENT_PACKAGE + "FeatureRegistry;";
            return true;
        }
        if (call.owner.equals(LEGACY_FEATURE_REGISTRY) && call.name.equals("menuType")) {
            Type[] arguments = Type.getArgumentTypes(call.desc);
            if (arguments.length == 2 && arguments[0].getSort() == Type.OBJECT
                    && arguments[0].getInternalName().equals(LEGACY_FACTORY)) {
                arguments[0] = Type.getObjectType(CONTAINER_FACTORY);
                call.desc = Type.getMethodDescriptor(Type.getReturnType(call.desc), arguments);
                return true;
            }
        }
        if (call.owner.equals(LEGACY_FEATURE_REGISTRY)
                && (call.name.equals("getRegistry") || call.name.equals("getRegistryNullable"))) {
            Type[] arguments = Type.getArgumentTypes(call.desc);
            if (arguments.length == 1 && arguments[0].getSort() == Type.OBJECT
                    && arguments[0].getInternalName().equals("net/minecraft/resources/ResourceKey")
                    && Type.getReturnType(call.desc).getSort() == Type.OBJECT
                    && Type.getReturnType(call.desc).getInternalName().equals(LEGACY_DEFERRED_REGISTER)) {
                call.desc = "(Lnet/minecraft/resources/ResourceKey;)L" + DEFERRED_REGISTER + ";";
                method.instructions.insert(call, new MethodInsnNode(Opcodes.INVOKESTATIC,
                        LEGACY_DEFERRED_REGISTER,
                        call.name.equals("getRegistryNullable") ? "wrapNullable" : "wrap",
                        "(L" + DEFERRED_REGISTER + ";)L" + LEGACY_DEFERRED_REGISTER + ";", false));
                return true;
            }
        }
        return false;
    }

    private static Handle rewriteRegistryGet(Handle handle) {
        if (handle.getTag() != Opcodes.H_INVOKESTATIC || !handle.getOwner().equals(LEGACY_REGISTRY)
                || !handle.getName().equals("get")
                || !handle.getDesc().equals("(Lnet/minecraft/resources/ResourceLocation;)L"
                        + LEGACY_FEATURE_REGISTRY + ";")) {
            return null;
        }
        return new Handle(handle.getTag(), handle.getOwner(), handle.getName(),
                "(Lnet/minecraft/resources/ResourceLocation;)L" + CURRENT_PACKAGE + "FeatureRegistry;",
                handle.isInterface());
    }

    private static void rewriteField(FieldInsnNode field) {
        if (field.owner.equals("forestry/core/tab/ForestryCreativeTabs") && field.name.equals("MAIL")) {
            field.owner = "forestry/mail/tab/MailCreativeTab";
        }
    }
}
