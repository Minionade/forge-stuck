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
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
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
    private static final String CUSTOM_PACKET_PAYLOAD =
            "net/minecraft/network/protocol/common/custom/CustomPacketPayload";
    private static final String BLOCK_ITEM_FACTORY_ADAPTER =
            "net/neoforged/forgestuck/compat/LegacyForestryBlockItemFactory";
    private static final Map<String, String> EXACT_MOVES = Map.of(
            "forestry/core/tiles/IFilterSlotDelegate", "forestry/api/core/IFilterSlotDelegate",
            "forestry/core/inventory/IInventoryAdapter", "forestry/api/core/IInventoryAdapter",
            "forestry/core/ClientsideCode", "forestry/core/platform/client/ClientsideCode",
            "forestry/api/recipes/IForestryRecipe", "forestry/api/core/machines/IForestryRecipe",
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
                && !raw.contains("forestry/core/inventory/InventoryAdapterTile")) {
            return bytes;
        }

        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        for (MethodNode method : node.methods) {
            rewriteGuiStreamSignature(method);
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode call) {
                    rewriteCall(method, call);
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
                } else if (instruction instanceof LdcInsnNode ldc && ldc.cst instanceof Handle handle) {
                    Handle replacement = rewriteRegistryGet(handle);
                    if (replacement != null) {
                        ldc.cst = replacement;
                    }
                }
            }
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

    private static void rewriteGuiStreamSignature(MethodNode method) {
        if ((method.name.equals("writeGuiData") || method.name.equals("readGuiData"))
                && method.desc.equals("(L" + FRIENDLY_BYTE_BUF + ";)V")) {
            method.desc = "(L" + REGISTRY_FRIENDLY_BYTE_BUF + ";)V";
        }
    }

    private static boolean rewriteCall(MethodNode method, MethodInsnNode call) {
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
        if (call.name.equals("sendPacketToListeners")
                && call.desc.equals("(L" + LEGACY_PACKET_CLIENT + ";)V")) {
            call.desc = "(L" + CUSTOM_PACKET_PAYLOAD + ";)V";
            return true;
        }
        if ((call.name.equals("writeGuiData") || call.name.equals("readGuiData"))
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

    private static void rewriteField(FieldInsnNode field) {
        if (field.owner.equals("forestry/core/tab/ForestryCreativeTabs") && field.name.equals("MAIL")) {
            field.name = "FORESTRY";
        }
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
}
