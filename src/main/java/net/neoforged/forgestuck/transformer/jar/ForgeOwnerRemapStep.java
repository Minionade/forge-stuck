package net.neoforged.forgestuck.transformer.jar;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import net.neoforged.forgestuck.compat.UnsupportedForgeApi;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rewrites exact legacy Forge class owners (see {@link ForgeOwnerMappings}) to their NeoForge equivalents in class
 * references, descriptors, signatures, method/field owners, annotation descriptors and Mixin target strings.
 * If a class uses a member that is not known to exist with the same signature on the NeoForge type, that owner is
 * left untouched in this class (and a warning is logged); Mixin classes still referencing it are dropped by
 * {@link MixinResourceRewriter}.
 */
public class ForgeOwnerRemapStep implements ClassTransformStep {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgestuck");
    private static final String TAG = "[Forge Stuck!!]";
    private static final String FLUID_STACK = "net/minecraftforge/fluids/FluidStack";
    private static final String FLUID_TANK = "net/minecraftforge/fluids/capability/templates/FluidTank";
    private static final String LEGACY_FLUID_TANK = "net/neoforged/forgestuck/compat/LegacyFluidTank";
    private static final String FLUID_STACK_TAG_CTOR =
            "(Lnet/minecraft/world/level/material/Fluid;ILnet/minecraft/nbt/CompoundTag;)V";
    private static final String FLUID_STACK_COPY_CTOR = "(L" + FLUID_STACK + ";I)V";
    private static final String FLUID_STACK_PACKET_READ_DESC =
            "(Lnet/minecraft/network/FriendlyByteBuf;)L" + FLUID_STACK + ";";
    private static final String FLUID_STACK_PACKET_WRITE_DESC =
            "(Lnet/minecraft/network/FriendlyByteBuf;)V";
    private static final String PICKUP = "net/minecraftforge/event/entity/player/EntityItemPickupEvent";
    private static final String LEGACY_PICKUP = "net/neoforged/forgestuck/compat/LegacyItemPickup";
    private static final String LEGACY_FLUID_STACK = "net/neoforged/forgestuck/compat/LegacyFluidStack";
    private static final String LEGACY_BURN_TIME_OWNER = "net/minecraftforge/common/ForgeHooks";
    private static final String LEGACY_BURN_TIME_DESC =
            "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/crafting/RecipeType;)I";
    private static final String ITEM_STACK_EXTENSION =
            "net/neoforged/neoforge/common/extensions/IItemStackExtension";
    private static final String LEGACY_LAZY = "net/minecraftforge/common/util/Lazy";
    private static final String BURN_TIME_EXTENSION_DESC =
            "(Lnet/minecraft/world/item/crafting/RecipeType;)I";
    private static final String LEGACY_PARTICLE_OPTIONS =
            "net/neoforged/forgestuck/compat/LegacyParticleOptions";
    private static final String PARTICLE_OPTIONS = "net/minecraft/core/particles/ParticleOptions";
    private static final String PARTICLE_PROVIDER = "net/minecraft/client/particle/ParticleProvider";
    private static final String LEGACY_ENTITY_CAPABILITY_DESC =
            "(Lnet/minecraftforge/common/capabilities/Capability;)"
                    + "Lnet/minecraftforge/common/util/LazyOptional;";
    private static final String LEGACY_SIDED_ENTITY_CAPABILITY_DESC =
            "(Lnet/minecraftforge/common/capabilities/Capability;Lnet/minecraft/core/Direction;)"
                    + "Lnet/minecraftforge/common/util/LazyOptional;";
    private static final String LEGACY_CAPABILITY_LOOKUP_DESC =
            "(Lnet/minecraftforge/common/capabilities/Capability;)"
                    + "Lnet/minecraftforge/common/util/LazyOptional;";
    private static final String LEGACY_CAPABILITY_ACCESS =
            "net/neoforged/forgestuck/compat/LegacyCapabilities";
    private static final String LEGACY_CAPABILITY_PROVIDER =
            "net/neoforged/forgestuck/compat/LegacyICapabilityProvider";
    private static final String LEGACY_ATTACH_CAPABILITIES_EVENT =
            "net/neoforged/forgestuck/compat/LegacyAttachCapabilitiesEvent";
    private static final String BLOCK_ENTITY = "net/minecraft/world/level/block/entity/BlockEntity";
    private static final String TCONSTRUCT_MOB_EQUIPMENT =
            "slimeknights/tconstruct/library/json/loot/equipment/MobEquipment";
    private static final String LEGACY_FINALIZE_SPAWN_EVENT =
            "net/minecraftforge/event/entity/living/MobSpawnEvent$FinalizeSpawn";

    private final ForgeOwnerMappings mappings;
    private final Map<String, Set<String>> declaredSupertypes = new ConcurrentHashMap<>();

    public ForgeOwnerRemapStep() {
        this(ForgeOwnerMappings.defaults());
    }

    public ForgeOwnerRemapStep(ForgeOwnerMappings mappings) {
        this.mappings = mappings;
    }

    @Override
    public byte[] apply(String className, byte[] bytes) {
        return apply(className, bytes, null);
    }

    @Override
    public byte[] apply(String className, byte[] bytes, OwnerRemapReport report) {
        if (bytes == null || className.startsWith("net.neoforged.forgestuck")) {
            return bytes;
        }
        boolean blockEntitySubclass = BLOCK_ENTITY.equals(new ClassReader(bytes).getSuperName());
        if (!mappings.mayReference(bytes) && !hasLegacyGameApiCalls(bytes) && !blockEntitySubclass) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        boolean addCapabilityProvider = blockEntitySubclass && !node.interfaces.contains(LEGACY_CAPABILITY_PROVIDER);
        if (addCapabilityProvider) node.interfaces.add(LEGACY_CAPABILITY_PROVIDER);

        Set<String> blocked = new HashSet<>();
        int[] adapted = {0};
        Set<String> uses = new HashSet<>();
        boolean mixin = isMixin(node);
        int replaced = mixin ? 0 : rewriteLegacyFluidStackConstructors(className, node);
        if (!mixin) {
            replaced += rewriteTConstructSpawnTag(node);
            replaced += rewriteLegacyFluidTankNbtCalls(node);
            replaced += rewriteLegacyAttachCapabilityHandlers(node);
            replaced += rewriteLegacyFluidStackPacketCalls(node);
            replaced += rewriteLegacyFriendlyByteBufFluidStackCalls(node);
            replaced += rewriteLegacyFriendlyByteBufRegistryCalls(node);
            replaced += rewriteLegacyItemTagAliases(node);
            replaced += rewriteLegacyBlockTagAliases(node);
            replaced += rewriteLegacyConditionListConstructors(node);
            replaced += rewriteLegacyMobEffectRemoveConstructors(node);
            replaced += rewriteEmptyFluidHandlerConstructors(node);
            replaced += rewriteLegacyItemHandlerHelperCalls(node);
            replaced += rewriteLegacyBurnTimeCalls(node);
            replaced += rewriteCustomLoaderBuilderConstructors(node);
            replaced += rewriteLegacyMobEffectModifiers(node);
            replaced += rewriteGenericListenerCalls(node);
            replaced += rewriteByteBufCodecCalls(node);
            replaced += rewriteLegacyEntityDataSerializerFactories(node);
            replaced += rewriteLegacyLazyInvocationKinds(node);
            replaced += rewriteLegacyEntityCapabilityCalls(node);
            replaced += rewriteLegacyBlockEntityCapabilityCalls(node);
            replaced += rewriteLegacyLevelCapabilityCalls(node);
        }
        replaced += rewriteLegacyItemStackCapabilityCalls(node);

        if (!mixin) replaced += replaceUnsupported(className, node, report);
        if (!mixin) replaced += replaceUnsupportedHandles(className, node, report);
        collectBlocked(className, node, blocked, adapted, uses);
        closeDependencies(uses, blocked);
        if (report != null) report.jarBlocked().addAll(blocked);

        TreeMap<String, Integer> counts = new TreeMap<>();
        translateEventBusEnum(node.visibleAnnotations);
        translateEventBusEnum(node.invisibleAnnotations);
        if (isMixin(node)) {
            for (MethodNode m : node.methods) {
                remapAnnotationStrings(m.invisibleAnnotations, blocked, counts);
                remapAnnotationStrings(m.visibleAnnotations, blocked, counts);
            }

            for (FieldNode f : node.fields) {
                remapAnnotationStrings(f.invisibleAnnotations, blocked, counts);
                remapAnnotationStrings(f.visibleAnnotations, blocked, counts);
            }
        }

        dropBrokenInnerClasses(node, mappings.remapper(blocked, new TreeMap<>()));
        ClassWriter writer = new ClassWriter(0);
        node.accept(new ClassRemapper(writer, mappings.remapper(blocked, counts)));
        if (counts.isEmpty() && replaced == 0 && !addCapabilityProvider) return bytes;
        if (report != null && !counts.isEmpty()) report.record(counts, mappings, adapted[0]);
        if (addCapabilityProvider) {
            LOGGER.debug("{} injected legacy BlockEntity capability provider in {}", TAG, className);
        }
        if (!counts.isEmpty()) LOGGER.debug("{} remapped Forge owners in {}: {}", TAG, className, counts);
        return restoreParticleProviderSamParameter(writer.toByteArray());
    }

    private int rewriteLegacyFluidTankNbtCalls(ClassNode node) {
        String legacyReadDescriptor = "(Lnet/minecraft/nbt/CompoundTag;)L" + FLUID_TANK + ";";
        String helperReadDescriptor = "(L" + FLUID_TANK + ";Lnet/minecraft/nbt/CompoundTag;)L" + FLUID_TANK + ";";
        String legacyWriteDescriptor = "(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;";
        String helperWriteDescriptor = "(L" + FLUID_TANK
                + ";Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;";
        int rewritten = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKEVIRTUAL) {
                    continue;
                }
                boolean readFromNbt = call.name.equals("readFromNBT") && call.desc.equals(legacyReadDescriptor);
                boolean writeToNbt = call.name.equals("writeToNBT")
                        && call.desc.equals(legacyWriteDescriptor)
                        && (call.owner.equals(FLUID_TANK) || hasSupertype(call.owner, FLUID_TANK));
                if (!readFromNbt && !writeToNbt) continue;
                call.setOpcode(Opcodes.INVOKESTATIC);
                call.owner = LEGACY_FLUID_TANK;
                call.desc = readFromNbt ? helperReadDescriptor : helperWriteDescriptor;
                call.itf = false;
                rewritten++;
            }
        }
        return rewritten;
    }

    private boolean hasSupertype(String className, String target) {
        Set<String> visited = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>(declaredSupertypes.getOrDefault(className, Set.of()));
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (current.equals(target)) return true;
            if (!visited.add(current)) continue;
            pending.addAll(declaredSupertypes.getOrDefault(current, Set.of()));
        }
        return false;
    }

    private static int rewriteTConstructSpawnTag(ClassNode node) {
        if (!TCONSTRUCT_MOB_EQUIPMENT.equals(node.name)) return 0;
        int rewritten = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode call)
                        || call.getOpcode() != Opcodes.INVOKEVIRTUAL
                        || !LEGACY_FINALIZE_SPAWN_EVENT.equals(call.owner)
                        || !call.name.equals("getSpawnTag")
                        || !call.desc.equals("()Lnet/minecraft/nbt/CompoundTag;")) continue;
                InsnList replacement = new InsnList();
                replacement.add(new InsnNode(Opcodes.POP));
                replacement.add(new InsnNode(Opcodes.ACONST_NULL));
                method.instructions.insertBefore(call, replacement);
                method.instructions.remove(call);
                rewritten++;
            }
        }
        return rewritten;
    }

    private static int rewriteLegacyEntityCapabilityCalls(ClassNode node) {
        String entity = "net/minecraft/world/entity/Entity";
        String capability = "Lnet/minecraftforge/common/capabilities/Capability;";
        String lazyOptional = "Lnet/minecraftforge/common/util/LazyOptional;";
        int rewritten = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode call)
                        || !(call.getOpcode() == Opcodes.INVOKEVIRTUAL || call.getOpcode() == Opcodes.INVOKEINTERFACE)
                        || !call.name.equals("getCapability")
                        || !(LEGACY_ENTITY_CAPABILITY_DESC.equals(call.desc)
                                || LEGACY_SIDED_ENTITY_CAPABILITY_DESC.equals(call.desc))
                        || !isEntityCapabilityOwner(call.owner)) continue;
                String helperDescriptor = "(L" + entity + ";" + capability
                        + (LEGACY_SIDED_ENTITY_CAPABILITY_DESC.equals(call.desc) ? "Lnet/minecraft/core/Direction;" : "")
                        + ")" + lazyOptional;
                call.setOpcode(Opcodes.INVOKESTATIC);
                call.owner = LEGACY_CAPABILITY_ACCESS;
                call.desc = helperDescriptor;
                call.itf = false;
                rewritten++;
            }
        }
        return rewritten;
    }

    private int rewriteLegacyBlockEntityCapabilityCalls(ClassNode node) {
        String capability = "Lnet/neoforged/forgestuck/compat/LegacyCapability;";
        String lazyOptional = "Lnet/neoforged/forgestuck/compat/LazyOptional;";
        int rewritten = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode call)
                        || !(call.getOpcode() == Opcodes.INVOKEVIRTUAL || call.getOpcode() == Opcodes.INVOKEINTERFACE)
                        || !call.name.equals("getCapability")
                        || !(LEGACY_ENTITY_CAPABILITY_DESC.equals(call.desc)
                                || LEGACY_SIDED_ENTITY_CAPABILITY_DESC.equals(call.desc))
                        || !(BLOCK_ENTITY.equals(call.owner) || hasSupertype(call.owner, BLOCK_ENTITY))) continue;
                String helperDescriptor = "(L" + BLOCK_ENTITY + ";" + capability
                        + (LEGACY_SIDED_ENTITY_CAPABILITY_DESC.equals(call.desc)
                                ? "Lnet/minecraft/core/Direction;" : "")
                        + ")" + lazyOptional;
                call.setOpcode(Opcodes.INVOKESTATIC);
                call.owner = LEGACY_CAPABILITY_ACCESS;
                call.desc = helperDescriptor;
                call.itf = false;
                rewritten++;
            }
        }
        return rewritten;
    }

    private static int rewriteLegacyAttachCapabilityHandlers(ClassNode node) {
        String legacyEvent = "net/minecraftforge/event/AttachCapabilitiesEvent";
        String subscribeEvent = "Lnet/minecraftforge/eventbus/api/SubscribeEvent;";
        int rewritten = 0;
        for (MethodNode method : node.methods) {
            if (!method.desc.startsWith("(L" + legacyEvent + ";)V")
                    || !hasSubscribeEvent(method, subscribeEvent)) continue;
            String payload = legacyAttachCapabilitiesPayload(method.signature, legacyEvent);
            if (payload == null) continue;
            int eventLocal = (method.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
            LabelNode matched = new LabelNode();
            InsnList guard = new InsnList();
            guard.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, eventLocal));
            guard.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, LEGACY_ATTACH_CAPABILITIES_EVENT, "getObject",
                    "()Ljava/lang/Object;", false));
            guard.add(new TypeInsnNode(Opcodes.INSTANCEOF, payload));
            guard.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.IFNE, matched));
            guard.add(new InsnNode(Opcodes.RETURN));
            guard.add(matched);
            guard.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
            method.instructions.insert(guard);
            rewritten++;
        }
        return rewritten;
    }

    private static boolean hasSubscribeEvent(MethodNode method, String annotation) {
        return hasAnnotation(method.visibleAnnotations, annotation)
                || hasAnnotation(method.invisibleAnnotations, annotation);
    }

    private static boolean hasAnnotation(List<AnnotationNode> annotations, String descriptor) {
        return annotations != null && annotations.stream().anyMatch(annotation -> annotation.desc.equals(descriptor));
    }

    private static String legacyAttachCapabilitiesPayload(String signature, String legacyEvent) {
        if (signature == null) return null;
        String marker = "L" + legacyEvent + "<L";
        int start = signature.indexOf(marker);
        if (start < 0) return null;
        start += marker.length();
        int end = signature.indexOf(';', start);
        return end < 0 ? null : signature.substring(start, end);
    }

    private static int rewriteLegacyItemStackCapabilityCalls(ClassNode node) {
        String itemStack = "net/minecraft/world/item/ItemStack";
        String capability = "Lnet/neoforged/forgestuck/compat/LegacyCapability;";
        String lazyOptional = "Lnet/neoforged/forgestuck/compat/LazyOptional;";
        int rewritten = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode call)
                        || call.getOpcode() != Opcodes.INVOKEVIRTUAL
                        || !call.owner.equals(itemStack)
                        || !call.name.equals("getCapability")
                        || !LEGACY_CAPABILITY_LOOKUP_DESC.equals(call.desc)) continue;
                call.setOpcode(Opcodes.INVOKESTATIC);
                call.owner = LEGACY_CAPABILITY_ACCESS;
                call.desc = "(L" + itemStack + ";" + capability + ")" + lazyOptional;
                call.itf = false;
                rewritten++;
            }
        }
        return rewritten;
    }

    private static int rewriteLegacyLevelCapabilityCalls(ClassNode node) {
        String level = "net/minecraft/world/level/Level";
        String capability = "Lnet/neoforged/forgestuck/compat/LegacyCapability;";
        String lazyOptional = "Lnet/neoforged/forgestuck/compat/LazyOptional;";
        int rewritten = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode call)
                        || call.getOpcode() != Opcodes.INVOKEVIRTUAL
                        || !call.owner.equals(level)
                        || !call.name.equals("getCapability")
                        || !LEGACY_CAPABILITY_LOOKUP_DESC.equals(call.desc)) continue;
                call.setOpcode(Opcodes.INVOKESTATIC);
                call.owner = LEGACY_CAPABILITY_ACCESS;
                call.desc = "(L" + level + ";" + capability + ")" + lazyOptional;
                call.itf = false;
                rewritten++;
            }
        }
        return rewritten;
    }

    private static boolean isEntityCapabilityOwner(String owner) {
        return owner.equals("net/minecraft/world/entity/Entity")
                || owner.equals("net/minecraft/world/entity/LivingEntity")
                || owner.equals("net/minecraft/world/entity/player/Player")
                || owner.equals("net/minecraft/server/level/ServerPlayer");
    }

    private static int rewriteLegacyLazyInvocationKinds(ClassNode node) {
        String factoryDesc = "(Ljava/util/function/Supplier;)L" + LEGACY_LAZY + ";";
        int rewritten = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode call && LEGACY_LAZY.equals(call.owner)) {
                    if (call.getOpcode() == Opcodes.INVOKESTATIC
                            && call.name.equals("of")
                            && call.desc.equals(factoryDesc)
                            && call.itf) {
                        call.itf = false;
                        rewritten++;
                    } else if (call.getOpcode() == Opcodes.INVOKEINTERFACE
                            && call.name.equals("get")
                            && call.desc.equals("()Ljava/lang/Object;")) {
                        call.setOpcode(Opcodes.INVOKEVIRTUAL);
                        call.itf = false;
                        rewritten++;
                    }
                } else if (instruction instanceof InvokeDynamicInsnNode indy) {
                    for (int i = 0; i < indy.bsmArgs.length; i++) {
                        if (indy.bsmArgs[i] instanceof Handle handle) {
                            Handle replacement = rewriteLegacyLazyHandle(handle, factoryDesc);
                            if (replacement != null) {
                                indy.bsmArgs[i] = replacement;
                                rewritten++;
                            }
                        }
                    }
                } else if (instruction instanceof LdcInsnNode ldc && ldc.cst instanceof Handle handle) {
                    Handle replacement = rewriteLegacyLazyHandle(handle, factoryDesc);
                    if (replacement != null) {
                        ldc.cst = replacement;
                        rewritten++;
                    }
                }
            }
        }
        return rewritten;
    }

    private static Handle rewriteLegacyLazyHandle(Handle handle, String factoryDesc) {
        if (!LEGACY_LAZY.equals(handle.getOwner())) return null;
        if (handle.getTag() == Opcodes.H_INVOKESTATIC
                && handle.getName().equals("of")
                && handle.getDesc().equals(factoryDesc)
                && handle.isInterface()) {
            return new Handle(Opcodes.H_INVOKESTATIC, LEGACY_LAZY, "of", factoryDesc, false);
        }
        if (handle.getTag() == Opcodes.H_INVOKEINTERFACE
                && handle.getName().equals("get")
                && handle.getDesc().equals("()Ljava/lang/Object;")) {
            return new Handle(Opcodes.H_INVOKEVIRTUAL, LEGACY_LAZY, "get",
                    "()Ljava/lang/Object;", false);
        }
        return null;
    }

    private static byte[] restoreParticleProviderSamParameter(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        boolean modified = false;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof InvokeDynamicInsnNode indy)
                        || indy.bsmArgs == null
                        || indy.bsmArgs.length == 0
                        || !indy.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")
                        || !(indy.bsmArgs[0] instanceof Type samType)
                        || Type.getReturnType(indy.desc).getSort() != Type.OBJECT
                        || !PARTICLE_PROVIDER.equals(Type.getReturnType(indy.desc).getInternalName())) {
                    continue;
                }
                Type[] arguments = samType.getArgumentTypes();
                if (arguments.length == 0
                        || arguments[0].getSort() != Type.OBJECT
                        || !LEGACY_PARTICLE_OPTIONS.equals(arguments[0].getInternalName())) {
                    continue;
                }
                arguments[0] = Type.getObjectType(PARTICLE_OPTIONS);
                indy.bsmArgs[0] = Type.getMethodType(
                        Type.getMethodDescriptor(samType.getReturnType(), arguments));
                modified = true;
            }
        }
        if (!modified) return bytes;
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static int rewriteLegacyItemTagAliases(ClassNode node) {
        String oldOwner = "net/minecraftforge/common/Tags$Items";
        String newOwner = "net/neoforged/forgestuck/compat/LegacyItemTags";
        Set<String> fields = Set.of("ARMORS_BOOTS", "ARMORS_CHESTPLATES", "ARMORS_HELMETS",
                "ARMORS_LEGGINGS", "DUSTS_PRISMARINE", "HEADS", "STAINED_GLASS", "STAINED_GLASS_PANES",
                "STORAGE_BLOCKS_QUARTZ");
        int rewritten = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof FieldInsnNode field
                        && field.getOpcode() == Opcodes.GETSTATIC
                        && field.owner.equals(oldOwner)
                        && fields.contains(field.name)
                        && field.desc.equals("Lnet/minecraft/tags/TagKey;")) {
                    field.owner = newOwner;
                    rewritten++;
                }
            }
        }
        return rewritten;
    }

    private static int rewriteLegacyBlockTagAliases(ClassNode node) {
        String oldOwner = "net/minecraftforge/common/Tags$Blocks";
        String newOwner = "net/neoforged/forgestuck/compat/LegacyBlockTags";
        Set<String> fields = Set.of("STAINED_GLASS", "STAINED_GLASS_PANES");
        int rewritten = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof FieldInsnNode field
                        && field.getOpcode() == Opcodes.GETSTATIC
                        && field.owner.equals(oldOwner)
                        && fields.contains(field.name)
                        && field.desc.equals("Lnet/minecraft/tags/TagKey;")) {
                    field.owner = newOwner;
                    rewritten++;
                }
            }
        }
        return rewritten;
    }

    private static int rewriteLegacyBurnTimeCalls(ClassNode node) {
        int rewritten = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode call)
                        || call.getOpcode() != Opcodes.INVOKESTATIC
                        || !call.owner.equals(LEGACY_BURN_TIME_OWNER)
                        || !call.name.equals("getBurnTime")
                        || !call.desc.equals(LEGACY_BURN_TIME_DESC)) {
                    continue;
                }
                method.instructions.set(call, new MethodInsnNode(
                        Opcodes.INVOKEINTERFACE, ITEM_STACK_EXTENSION, "getBurnTime",
                        BURN_TIME_EXTENSION_DESC, true));
                rewritten++;
            }
        }
        return rewritten;
    }

    private static boolean hasLegacyGameApiCalls(byte[] bytes) {
        String content = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        return content.contains("net/minecraft/world/effect/MobEffect")
                || content.contains("net/minecraft/world/entity/EntityType$Builder");
    }

    /**
     * An InnerClasses entry names (inner, outer, simple name). When a mapping turns a nested legacy class into a
     * top-level one (or into a differently nested one) the remapped triple no longer satisfies
     * {@code inner == outer + "$" + simple} and can even name one class as its own outer, which the JVM rejects with
     * "Class is both outer and inner class". Such entries describe a class that no longer exists in that shape, so
     * they are dropped; references to the class itself are unaffected.
     */
    static void dropBrokenInnerClasses(ClassNode node, org.objectweb.asm.commons.Remapper remapper) {
        if (node.innerClasses == null) return;
        node.innerClasses.removeIf(ic -> {
            if (ic.outerName == null || ic.innerName == null) return false;
            String inner = remapper.map(ic.name);
            String outer = remapper.map(ic.outerName);
            if (inner.equals(ic.name) && outer.equals(ic.outerName)) return false;
            return !inner.equals(outer + "$" + ic.innerName);
        });
    }

    /** Calls on mapped owners redirected to static compatibility helpers. */
    private static final java.util.Map<String, String> REDIRECTS = java.util.Map.ofEntries(
            java.util.Map.entry("net/minecraftforge/event/level/BlockEvent$BreakEvent.getExpToDrop()I",
                    "net/neoforged/forgestuck/compat/LegacyBlockBreakEvent"),
            java.util.Map.entry("net/minecraftforge/event/level/BlockEvent$BreakEvent.setExpToDrop(I)V",
                    "net/neoforged/forgestuck/compat/LegacyBlockBreakEvent"),
            java.util.Map.entry("net/minecraftforge/client/gui/overlay/ForgeGui"
                    + ".shouldDrawSurvivalElements()Z",
                    "net/neoforged/forgestuck/compat/LegacyForgeGui"),
            java.util.Map.entry("net/minecraftforge/client/gui/overlay/ForgeGui"
                    + ".setupOverlayRenderState(ZZ)V",
                    "net/neoforged/forgestuck/compat/LegacyForgeGui"),
            java.util.Map.entry("net/minecraftforge/client/event/ModelEvent$RegisterAdditional"
                    + ".register(Lnet/minecraft/resources/ResourceLocation;)V",
                    "net/neoforged/forgestuck/compat/LegacyAdditionalModelEvent"),
            java.util.Map.entry("net/minecraftforge/client/event/EntityRenderersEvent$AddLayers"
                    + ".getRenderer(Lnet/minecraft/world/entity/EntityType;)"
                    + "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;",
                    "net/neoforged/forgestuck/compat/LegacyAddLayersEvent"),
            java.util.Map.entry("net/minecraftforge/client/event/ModelEvent$RegisterGeometryLoaders"
                    + ".register(Ljava/lang/String;Lnet/minecraftforge/client/model/geometry/IGeometryLoader;)V",
                    "net/neoforged/forgestuck/compat/LegacyGeometryLoaderRegistration"),
            java.util.Map.entry("net/minecraftforge/event/BuildCreativeModeTabContentsEvent"
                    + ".m_246342_(Lnet/minecraft/world/item/ItemStack;)V",
                    "net/neoforged/forgestuck/compat/LegacyCreativeTabEvent"),
            java.util.Map.entry("net/minecraftforge/client/event/RenderLevelStageEvent.getPartialTick()F",
                    "net/neoforged/forgestuck/compat/LegacyRenderLevelStage"),
            java.util.Map.entry("net/minecraftforge/client/event/RenderNameTagEvent.getResult()"
                    + "Lnet/minecraftforge/eventbus/api/Event$Result;",
                    "net/neoforged/forgestuck/compat/LegacyRenderNameTagEvent"),
            java.util.Map.entry("net/minecraftforge/client/event/RenderGuiOverlayEvent$Post.getOverlay()"
                    + "Lnet/minecraftforge/client/gui/overlay/NamedGuiOverlay;",
                    "net/neoforged/forgestuck/compat/LegacyGuiOverlayEvent"),
            java.util.Map.entry("net/minecraftforge/client/event/RenderGuiOverlayEvent$Pre.getOverlay()"
                    + "Lnet/minecraftforge/client/gui/overlay/NamedGuiOverlay;",
                    "net/neoforged/forgestuck/compat/LegacyGuiOverlayEvent"),
            java.util.Map.entry("net/minecraftforge/common/loot/LootTableIdCondition$Builder"
                    + ".m_6409_()Lnet/minecraft/world/level/storage/loot/predicates/LootItemCondition;",
                    "net/neoforged/forgestuck/compat/LegacyLootTableIdCondition"),
            java.util.Map.entry("net/minecraftforge/common/crafting/IShapedRecipe.getRecipeWidth()I",
                    "net/neoforged/forgestuck/compat/LegacyShapedRecipe"),
            java.util.Map.entry("net/minecraftforge/common/crafting/IShapedRecipe.getRecipeHeight()I",
                    "net/neoforged/forgestuck/compat/LegacyShapedRecipe"),
            java.util.Map.entry("net/minecraftforge/common/IPlantable.getPlant("
                    + "Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)"
                    + "Lnet/minecraft/world/level/block/state/BlockState;",
                    "net/neoforged/forgestuck/compat/LegacyPlantable"),
            java.util.Map.entry("net/minecraftforge/common/IPlantable.getPlantType("
                    + "Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)"
                    + "Lnet/minecraftforge/common/PlantType;",
                    "net/neoforged/forgestuck/compat/LegacyPlantable"),
            java.util.Map.entry("net/minecraftforge/client/ForgeHooksClient.getArmorTexture("
                    + "Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;"
                    + "Ljava/lang/String;Lnet/minecraft/world/entity/EquipmentSlot;Ljava/lang/String;)"
                    + "Ljava/lang/String;",
                    "net/neoforged/forgestuck/compat/LegacyArmorTextureHooks"),
            java.util.Map.entry("net/minecraftforge/event/ForgeEventFactory.canCreateFluidSource("
                    + "Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;Z)Z",
                    "net/neoforged/forgestuck/compat/LegacyFluidSourceEvent"),
            java.util.Map.entry("net/minecraftforge/event/ForgeEventFactory.onApplyBonemeal("
                    + "Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;"
                    + "Lnet/minecraft/world/item/ItemStack;)I",
                    "net/neoforged/forgestuck/compat/LegacyBonemealHook"),
            java.util.Map.entry("net/minecraftforge/client/model/geometry/UnbakedGeometryHelper"
                    + ".createUnbakedItemElements(ILnet/minecraft/client/renderer/texture/SpriteContents;)"
                    + "Ljava/util/List;",
                    "net/neoforged/forgestuck/compat/LegacyUnbakedGeometryHelper"),
            java.util.Map.entry("net/minecraftforge/client/model/geometry/UnbakedGeometryHelper"
                    + ".createUnbakedItemMaskElements(ILnet/minecraft/client/renderer/texture/SpriteContents;)"
                    + "Ljava/util/List;",
                    "net/neoforged/forgestuck/compat/LegacyUnbakedGeometryHelper"),
            java.util.Map.entry("net/minecraftforge/client/model/geometry/UnbakedGeometryHelper"
                    + ".bakeElements(Ljava/util/List;Ljava/util/function/Function;"
                    + "Lnet/minecraft/client/resources/model/ModelState;Lnet/minecraft/resources/ResourceLocation;)"
                    + "Ljava/util/List;",
                    "net/neoforged/forgestuck/compat/LegacyUnbakedGeometryHelper"),
            java.util.Map.entry("net/minecraftforge/event/entity/player/PlayerInteractEvent$RightClickBlock"
                    + ".getUseBlock()Lnet/minecraftforge/eventbus/api/Event$Result;",
                    "net/neoforged/forgestuck/compat/LegacyPlayerInteractEvent"),
            java.util.Map.entry("net/minecraftforge/event/entity/player/PlayerInteractEvent$RightClickBlock"
                    + ".getUseItem()Lnet/minecraftforge/eventbus/api/Event$Result;",
                    "net/neoforged/forgestuck/compat/LegacyPlayerInteractEvent"),
            java.util.Map.entry("net/minecraftforge/event/entity/player/PlayerInteractEvent$RightClickEmpty"
                    + ".getCancellationResult()Lnet/minecraft/world/InteractionResult;",
                    "net/neoforged/forgestuck/compat/LegacyPlayerInteractEvent"),
            java.util.Map.entry("net/minecraftforge/event/entity/player/PlayerInteractEvent$RightClickEmpty"
                    + ".setCancellationResult(Lnet/minecraft/world/InteractionResult;)V",
                    "net/neoforged/forgestuck/compat/LegacyPlayerInteractEvent"),
            java.util.Map.entry("net/minecraftforge/event/entity/player/PlayerInteractEvent$LeftClickEmpty"
                    + ".getCancellationResult()Lnet/minecraft/world/InteractionResult;",
                    "net/neoforged/forgestuck/compat/LegacyPlayerInteractEvent"),
            java.util.Map.entry("net/minecraftforge/event/entity/player/PlayerInteractEvent$LeftClickEmpty"
                    + ".setCancellationResult(Lnet/minecraft/world/InteractionResult;)V",
                    "net/neoforged/forgestuck/compat/LegacyPlayerInteractEvent"),
            java.util.Map.entry("net/minecraftforge/event/entity/player/PlayerEvent$StartTracking.isCanceled()Z",
                    "net/neoforged/forgestuck/compat/LegacyStartTrackingEvent"),
            java.util.Map.entry("net/minecraftforge/event/entity/EntityAttributeModificationEvent.add("
                    + "Lnet/minecraft/world/entity/EntityType;"
                    + "Lnet/minecraft/world/entity/ai/attributes/Attribute;)V",
                    "net/neoforged/forgestuck/compat/LegacyEntityAttributeModification"),
            java.util.Map.entry("net/minecraftforge/event/entity/EntityAttributeModificationEvent.add("
                    + "Lnet/minecraft/world/entity/EntityType;"
                    + "Lnet/minecraft/world/entity/ai/attributes/Attribute;D)V",
                    "net/neoforged/forgestuck/compat/LegacyEntityAttributeModification"),
            java.util.Map.entry("net/minecraft/world/entity/EntityType$Builder.setCustomClientFactory(Ljava/util/function/BiFunction;)Lnet/minecraft/world/entity/EntityType$Builder;",
                    "net/neoforged/forgestuck/compat/LegacyEntityTypeBuilder"),
            java.util.Map.entry(FLUID_STACK + ".getTag()Lnet/minecraft/nbt/CompoundTag;",
                    "net/neoforged/forgestuck/compat/LegacyFluidStack"),
            java.util.Map.entry(FLUID_STACK + ".getOrCreateTag()Lnet/minecraft/nbt/CompoundTag;",
                    "net/neoforged/forgestuck/compat/LegacyFluidStack"),
            java.util.Map.entry(FLUID_STACK + ".hasTag()Z",
                    "net/neoforged/forgestuck/compat/LegacyFluidStack"),
            java.util.Map.entry(FLUID_STACK
                    + ".getRawFluid()Lnet/minecraft/world/level/material/Fluid;",
                    "net/neoforged/forgestuck/compat/LegacyFluidStack"),
            java.util.Map.entry(FLUID_STACK + ".setTag(Lnet/minecraft/nbt/CompoundTag;)V",
                    "net/neoforged/forgestuck/compat/LegacyFluidStack"),
            java.util.Map.entry(FLUID_STACK
                    + ".loadFluidStackFromNBT(Lnet/minecraft/nbt/CompoundTag;)L" + FLUID_STACK + ";",
                    "net/neoforged/forgestuck/compat/LegacyFluidStack"),
            java.util.Map.entry(FLUID_STACK
                    + ".writeToNBT(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;",
                    "net/neoforged/forgestuck/compat/LegacyFluidStack"),
            java.util.Map.entry(FLUID_STACK + ".readFromPacket" + FLUID_STACK_PACKET_READ_DESC,
                    LEGACY_FLUID_STACK),
            java.util.Map.entry(FLUID_STACK + ".writeToPacket" + FLUID_STACK_PACKET_WRITE_DESC,
                    LEGACY_FLUID_STACK),
            java.util.Map.entry("net/minecraftforge/fluids/FluidUtil.getFluidHandler("
                    + "Lnet/minecraft/world/item/ItemStack;)Lnet/minecraftforge/common/util/LazyOptional;",
                    "net/neoforged/forgestuck/compat/LegacyFluidUtil"),
            java.util.Map.entry("net/minecraftforge/fluids/capability/templates/FluidTank"
                    + ".readFromNBT(Lnet/minecraft/nbt/CompoundTag;)"
                    + "Lnet/minecraftforge/fluids/capability/templates/FluidTank;",
                    "net/neoforged/forgestuck/compat/LegacyFluidTank"),
            java.util.Map.entry("net/minecraftforge/fluids/capability/templates/FluidTank"
                    + ".writeToNBT(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;",
                    "net/neoforged/forgestuck/compat/LegacyFluidTank"),
            java.util.Map.entry("net/minecraftforge/common/ForgeHooks.getCriticalHit("
                    + "Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;ZF)"
                    + "Lnet/minecraftforge/event/entity/player/CriticalHitEvent;",
                    "net/neoforged/forgestuck/compat/LegacyCriticalHitEvent"),
            java.util.Map.entry("net/minecraftforge/common/ForgeHooks.onCropsGrowPre("
                    + "Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;Z)Z",
                    "net/neoforged/forgestuck/compat/LegacyCropGrowth"),
            java.util.Map.entry("net/minecraftforge/common/ForgeHooks.onCropsGrowPost("
                    + "Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;)V",
                    "net/neoforged/forgestuck/compat/LegacyCropGrowth"),
            java.util.Map.entry("net/minecraftforge/common/ForgeHooks.hasNoElements("
                    + "Lnet/minecraft/world/item/crafting/Ingredient;)Z",
                    "net/neoforged/forgestuck/compat/LegacyIngredientHooks"),
            java.util.Map.entry("net/minecraftforge/fml/loading/FMLLoader.isProduction()Z",
                    "net/neoforged/forgestuck/compat/LegacyFMLLoader"),
            java.util.Map.entry("net/minecraftforge/fml/ModLoader.isLoadingStateValid()Z",
                    "net/neoforged/forgestuck/compat/LegacyFMLLoader"),
            java.util.Map.entry("net/minecraftforge/event/entity/player/CriticalHitEvent.getDamageModifier()F",
                    "net/neoforged/forgestuck/compat/LegacyCriticalHitEvent"),
            java.util.Map.entry("net/minecraftforge/event/entity/player/CriticalHitEvent.setDamageModifier(F)V",
                    "net/neoforged/forgestuck/compat/LegacyCriticalHitEvent"),
            java.util.Map.entry("net/minecraftforge/event/entity/player/CriticalHitEvent.getResult()"
                    + "Lnet/minecraftforge/eventbus/api/Event$Result;",
                    "net/neoforged/forgestuck/compat/LegacyCriticalHitEvent"),
            java.util.Map.entry("net/minecraftforge/event/entity/player/CriticalHitEvent.setResult("
                    + "Lnet/minecraftforge/eventbus/api/Event$Result;)V",
                    "net/neoforged/forgestuck/compat/LegacyCriticalHitEvent"),
            java.util.Map.entry("net/minecraftforge/event/entity/living/MobEffectEvent$Applicable.setResult("
                    + "Lnet/minecraftforge/eventbus/api/Event$Result;)V",
                    "net/neoforged/forgestuck/compat/LegacyMobEffectApplicable"),
            java.util.Map.entry("net/minecraftforge/event/entity/living/MobEffectEvent$Applicable.getResult()"
                    + "Lnet/minecraftforge/eventbus/api/Event$Result;",
                    "net/neoforged/forgestuck/compat/LegacyMobEffectApplicable"),
            java.util.Map.entry("net/minecraftforge/event/entity/living/MobEffectEvent$Applicable.isCanceled()Z",
                    "net/neoforged/forgestuck/compat/LegacyMobEffectApplicable"),
            java.util.Map.entry("net/minecraftforge/event/entity/living/MobEffectEvent$Applicable.setCanceled(Z)V",
                    "net/neoforged/forgestuck/compat/LegacyMobEffectApplicable"),
            java.util.Map.entry("net/minecraftforge/event/entity/living/MobEffectEvent$Added.isCanceled()Z",
                    "net/neoforged/forgestuck/compat/LegacyMobEffectApplicable"),
            java.util.Map.entry("net/minecraftforge/eventbus/api/IEventBus.register(Ljava/lang/Object;)V", "net/neoforged/forgestuck/compat/LegacyEventBusRegistration"),
            java.util.Map.entry("net/minecraftforge/eventbus/api/IEventBus.post(Lnet/minecraftforge/eventbus/api/Event;)Z",
                    "net/neoforged/forgestuck/compat/LegacyEventBusPost"),
            java.util.Map.entry("net/minecraftforge/registries/RegisterEvent.getForgeRegistry()Lnet/minecraftforge/registries/IForgeRegistry;",
                    "net/neoforged/forgestuck/compat/LegacyRegisterEvent"),
            java.util.Map.entry("net/minecraftforge/common/ForgeConfigSpec.setConfig(Lcom/electronwill/nightconfig/core/CommentedConfig;)V", "net/neoforged/forgestuck/compat/LegacyConfigSpec"),
            java.util.Map.entry("net/minecraftforge/common/capabilities/RegisterCapabilitiesEvent.register(Ljava/lang/Class;)V", "net/neoforged/forgestuck/compat/LegacyCapabilities"),
            java.util.Map.entry("net/minecraftforge/common/brewing/BrewingRecipeRegistry.addRecipe(Lnet/minecraftforge/common/brewing/IBrewingRecipe;)Z",
                    "net/neoforged/forgestuck/compat/LegacyBrewingRecipeRegistry"),
            java.util.Map.entry("net/minecraftforge/event/level/SaplingGrowTreeEvent.getRandomSource()Lnet/minecraft/util/RandomSource;", "net/neoforged/forgestuck/compat/LegacySaplingGrowth"),
            java.util.Map.entry("net/minecraftforge/event/level/SaplingGrowTreeEvent.setResult(Lnet/minecraftforge/eventbus/api/Event$Result;)V", "net/neoforged/forgestuck/compat/LegacySaplingGrowth"),
            java.util.Map.entry("net/minecraftforge/event/level/SaplingGrowTreeEvent.getResult()Lnet/minecraftforge/eventbus/api/Event$Result;", "net/neoforged/forgestuck/compat/LegacySaplingGrowth"),
            java.util.Map.entry("net/minecraftforge/event/level/SaplingGrowTreeEvent.isCanceled()Z", "net/neoforged/forgestuck/compat/LegacySaplingGrowth"),
            java.util.Map.entry("net/minecraftforge/event/level/SaplingGrowTreeEvent.hasResult()Z", "net/neoforged/forgestuck/compat/LegacySaplingGrowth"),
            java.util.Map.entry("net/minecraftforge/event/level/SaplingGrowTreeEvent.isCancelable()Z", "net/neoforged/forgestuck/compat/LegacySaplingGrowth"),
            java.util.Map.entry("net/minecraftforge/event/entity/living/LivingAttackEvent.getAmount()F", "net/neoforged/forgestuck/compat/LegacyLivingAttack"),
            java.util.Map.entry("net/minecraftforge/event/entity/living/LivingAttackEvent.isCancelable()Z", "net/neoforged/forgestuck/compat/LegacyLivingAttack"),
            java.util.Map.entry("net/minecraftforge/event/entity/living/LivingAttackEvent.hasResult()Z",             "net/neoforged/forgestuck/compat/LegacyLivingAttack"),
                        java.util.Map.entry(PICKUP + ".getEntity()Lnet/minecraft/world/entity/player/Player;", LEGACY_PICKUP),
                        java.util.Map.entry(PICKUP + ".getItem()Lnet/minecraft/world/entity/item/ItemEntity;", LEGACY_PICKUP),
                        java.util.Map.entry(PICKUP + ".setCanceled(Z)V", LEGACY_PICKUP),
                        java.util.Map.entry(PICKUP + ".isCanceled()Z", LEGACY_PICKUP),
                        java.util.Map.entry(PICKUP + ".isCancelable()Z", LEGACY_PICKUP),
                        java.util.Map.entry(PICKUP + ".hasResult()Z", LEGACY_PICKUP));

    private static int rewriteLegacyMobEffectModifiers(ClassNode node) {
        int rewritten = 0;
        String owner = "net/minecraft/world/effect/MobEffect";
        String desc = "(Lnet/minecraft/world/entity/ai/attributes/Attribute;Ljava/lang/String;D"
                + "Lnet/minecraft/world/entity/ai/attributes/AttributeModifier$Operation;)L" + owner + ";";
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (!(insn instanceof MethodInsnNode call)
                        || !(call.name.equals("addAttributeModifier") || call.name.equals("m_19472_"))
                        || !call.desc.equals(desc)) continue;
                Type[] args = Type.getArgumentTypes(desc);
                Type[] staticArgs = new Type[args.length + 1];
                staticArgs[0] = Type.getObjectType(owner);
                System.arraycopy(args, 0, staticArgs, 1, args.length);
                method.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC,
                        "net/neoforged/forgestuck/compat/LegacyMobEffect", "addAttributeModifier",
                        Type.getMethodDescriptor(Type.getReturnType(desc), staticArgs), false));
                rewritten++;
            }
        }
        return rewritten;
    }

                /**
                 * Unsupported members no longer leave their owner unmapped (which guaranteed NoClassDefFoundError for every
     * other use of that owner). The call site is instead replaced by a call to {@link UnsupportedForgeApi#fail}
     * followed by stack-neutral pops and a default value, so the class links and verifies, and only executing that
     * exact path throws {@link UnsupportedOperationException}. Unsupported method handles are redirected through a
     * synthetic throwing helper; unsupported constructors are only adapted when their allocation pattern is known.
     * Mixin classes with unsupported members remain unmapped and are dropped from their config.
     */
    private int replaceUnsupported(String className, ClassNode node, OwnerRemapReport report) {
        int replaced = 0;
        for (MethodNode m : node.methods) {
            for (AbstractInsnNode insn : m.instructions.toArray()) {
                String owner, name, desc;
                boolean field = false;
                if (insn instanceof MethodInsnNode c) {
                    owner = c.owner;
                    name = c.name;
                    desc = c.desc;
                } else if (insn instanceof FieldInsnNode f) {
                    owner = f.owner;
                    name = f.name;
                    desc = f.desc;
                    field = true;
                } else {
                    continue;
                }
                if (insn instanceof MethodInsnNode call) {
                    String mappedName = mappings.mappedMemberName(owner, name, desc);
                    if (mappedName != null) {
                        call.name = mappedName;
                        name = mappedName;
                    }
                }
                String helper = field ? null : REDIRECTS.get(owner + "." + name + desc);
                if (helper != null) {
                    if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC) {
                        call.owner = helper;
                        call.itf = false;
                        replaced++;
                        continue;
                    }
                    Type[] ra = Type.getArgumentTypes(desc);
                    Type[] sa = new Type[ra.length + 1];
                    sa[0] = Type.getObjectType(owner);
                    System.arraycopy(ra, 0, sa, 1, ra.length);
                    m.instructions.insertBefore(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, helper, name,
                            Type.getMethodDescriptor(Type.getReturnType(desc), sa), false));
                    m.instructions.remove(insn);
                    replaced++;
                    continue;
                }
                ForgeOwnerMappings.Entry e = mappings.get(owner);
                if (e == null || "<init>".equals(name) || isObjectMethod(name, desc)
                        || mappings.supportsMember(owner, name, desc)) continue;
                String member = owner.replace('/', '.') + (field ? "." + name : "." + name + desc);
                failAtCall(m, insn, member, className + "." + m.name);
                replaced++;
                if (report != null) report.unsupportedCall(member);
                LOGGER.debug("{} {} calls {} which has no verified equivalent; rewritten to fail at call time", TAG,
                        className, member);
            }
        }
        return replaced;
    }

    private static final String BUF_WRITER = "Lnet/minecraft/network/FriendlyByteBuf$Writer;";
    private static final String BUF_READER = "Lnet/minecraft/network/FriendlyByteBuf$Reader;";
    private static final String BUF_ENCODER = "Lnet/minecraft/network/codec/StreamEncoder;";
    private static final String BUF_DECODER = "Lnet/minecraft/network/codec/StreamDecoder;";
    private static final String ENTITY_DATA_SERIALIZER = "net/minecraft/network/syncher/EntityDataSerializer";
    private static final String LEGACY_ENTITY_DATA_SERIALIZER =
            "net/neoforged/forgestuck/compat/LegacyEntityDataSerializer";
    private static final String LEGACY_WRITER =
            "Lnet/neoforged/forgestuck/compat/LegacyByteBufWriter;";
    private static final String LEGACY_READER =
            "Lnet/neoforged/forgestuck/compat/LegacyByteBufReader;";
    private static final String GENERIC_LISTENER_OWNER = "net/minecraftforge/eventbus/api/IEventBus";
    private static final String GENERIC_LISTENER_DESC =
            "(Ljava/lang/Class;Ljava/util/function/Consumer;)V";
    private static final String GENERIC_LISTENER_PRIORITY_DESC =
            "(Ljava/lang/Class;Lnet/minecraftforge/eventbus/api/EventPriority;"
                    + "Ljava/util/function/Consumer;)V";
    private static final String GENERIC_LISTENER_HELPER =
            "net/neoforged/forgestuck/compat/LegacyGenericListenerRegistration";

    private static int rewriteGenericListenerCalls(ClassNode node) {
        int rewritten = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (!(insn instanceof MethodInsnNode call)
                        || !GENERIC_LISTENER_OWNER.equals(call.owner)
                        || !"addGenericListener".equals(call.name)
                        || !(GENERIC_LISTENER_DESC.equals(call.desc)
                                || GENERIC_LISTENER_PRIORITY_DESC.equals(call.desc))) {
                    continue;
                }
                boolean hasPriority = GENERIC_LISTENER_PRIORITY_DESC.equals(call.desc);
                Type listenerEventType = invokedConsumerEventType(previousMeaningful(call));
                if (listenerEventType == null) {
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = GENERIC_LISTENER_HELPER;
                    call.name = "addGenericListener";
                    call.desc = "(Lnet/neoforged/bus/api/IEventBus;"
                            + (hasPriority ? GENERIC_LISTENER_PRIORITY_DESC : GENERIC_LISTENER_DESC).substring(1);
                    call.itf = false;
                } else {
                    method.instructions.insertBefore(call, new LdcInsnNode(listenerEventType));
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = GENERIC_LISTENER_HELPER;
                    call.name = "addGenericListener";
                    String descriptor = hasPriority ? GENERIC_LISTENER_PRIORITY_DESC : GENERIC_LISTENER_DESC;
                    call.desc = "(Lnet/neoforged/bus/api/IEventBus;"
                            + descriptor.substring(1, descriptor.length() - 2)
                            + "Ljava/lang/Class;)V";
                    call.itf = false;
                    method.maxStack = Math.max(method.maxStack, hasPriority ? 5 : 4);
                }
                rewritten++;
            }
        }
        return rewritten;
    }

    private static AbstractInsnNode previousMeaningful(AbstractInsnNode insn) {
        AbstractInsnNode previous = insn.getPrevious();
        while (previous != null && (previous.getType() == AbstractInsnNode.LABEL
                || previous.getType() == AbstractInsnNode.LINE || previous.getType() == AbstractInsnNode.FRAME)) {
            previous = previous.getPrevious();
        }
        return previous;
    }

    private static Type invokedConsumerEventType(AbstractInsnNode consumer) {
        if (!(consumer instanceof InvokeDynamicInsnNode indy) || indy.bsmArgs.length < 3
                || !(indy.bsmArgs[2] instanceof Type instantiatedType)
                || instantiatedType.getSort() != Type.METHOD) {
            return null;
        }
        Type[] parameters = instantiatedType.getArgumentTypes();
        if (parameters.length != 1 || parameters[0].getSort() != Type.OBJECT) return null;
        return parameters[0];
    }

    /**
     * 1.21's FriendlyByteBuf helpers take StreamEncoder/StreamDecoder where 1.20.1 took Writer/Reader. The compat
     * Writer/Reader types extend those, so only the called descriptors need to name the new parameter types.
     */
    private static int rewriteByteBufCodecCalls(ClassNode node) {
        int replaced = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn instanceof MethodInsnNode call
                        && ("net/minecraft/network/FriendlyByteBuf".equals(call.owner)
                                || "net/minecraft/network/RegistryFriendlyByteBuf".equals(call.owner))
                        && (call.desc.contains(BUF_WRITER) || call.desc.contains(BUF_READER))) {
                    call.desc = call.desc.replace(BUF_WRITER, BUF_ENCODER).replace(BUF_READER, BUF_DECODER);
                    replaced++;
                }
            }
        }
        return replaced;
    }

    private static int rewriteLegacyEntityDataSerializerFactories(ClassNode node) {
        int replaced = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (!(insn instanceof MethodInsnNode call)
                        || !ENTITY_DATA_SERIALIZER.equals(call.owner)
                        || !("m_238095_".equals(call.name) || "simple".equals(call.name)
                                || "m_238098_".equals(call.name) || "optional".equals(call.name))) {
                    continue;
                }
                String suffix = "m_238098_".equals(call.name) || "optional".equals(call.name)
                        ? "optional" : "create";
                if (!call.desc.contains(BUF_WRITER) || !call.desc.contains(BUF_READER)) continue;
                call.owner = LEGACY_ENTITY_DATA_SERIALIZER;
                call.name = suffix;
                call.desc = "(" + LEGACY_WRITER + LEGACY_READER + ")L" + ENTITY_DATA_SERIALIZER + ";";
                call.itf = false;
                replaced++;
            }
        }
        return replaced;
    }

    private static int rewriteLegacyFluidStackConstructors(String className, ClassNode node) {
        int replaced = 0;
        for (MethodNode method : node.methods) {
            var allocations = new java.util.ArrayDeque<org.objectweb.asm.tree.TypeInsnNode>();
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn instanceof org.objectweb.asm.tree.TypeInsnNode allocation
                        && allocation.getOpcode() == Opcodes.NEW && FLUID_STACK.equals(allocation.desc)) {
                    allocations.push(allocation);
                } else if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL
                        && FLUID_STACK.equals(call.owner) && "<init>".equals(call.name) && !allocations.isEmpty()) {
                    var allocation = allocations.pop();
                    AbstractInsnNode duplicate = nextMeaningful(allocation);
                    String factoryDesc = fluidStackFactoryDescriptor(call.desc);
                    if (factoryDesc == null || duplicate == null || duplicate.getOpcode() != Opcodes.DUP) continue;
                    method.instructions.remove(allocation);
                    method.instructions.remove(duplicate);
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = LEGACY_FLUID_STACK;
                    call.name = "create";
                    call.desc = factoryDesc;
                    call.itf = false;
                    replaced++;
                    LOGGER.debug("{} {} adapts Forge FluidStack constructor", TAG, className);
                }
            }
        }
        return replaced;
    }

    private static int rewriteLegacyFluidStackPacketCalls(ClassNode node) {
        int rewritten = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode call) || !call.owner.equals(FLUID_STACK)) continue;
                if (call.getOpcode() == Opcodes.INVOKESTATIC
                        && call.name.equals("readFromPacket")
                        && call.desc.equals(FLUID_STACK_PACKET_READ_DESC)) {
                    call.owner = LEGACY_FLUID_STACK;
                    call.itf = false;
                    rewritten++;
                } else if ((call.getOpcode() == Opcodes.INVOKEVIRTUAL || call.getOpcode() == Opcodes.INVOKEINTERFACE)
                        && call.name.equals("writeToPacket")
                        && call.desc.equals(FLUID_STACK_PACKET_WRITE_DESC)) {
                    call.owner = LEGACY_FLUID_STACK;
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.desc = "(L" + FLUID_STACK + ";" + FLUID_STACK_PACKET_WRITE_DESC.substring(1);
                    call.itf = false;
                    rewritten++;
                }
            }
        }
        return rewritten;
    }

    private static int rewriteLegacyFriendlyByteBufRegistryCalls(ClassNode node) {
        String buffer = "net/minecraft/network/FriendlyByteBuf";
        String helper = "net/neoforged/forgestuck/compat/LegacyFriendlyByteBuf";
        String legacyRegistry = "net/minecraftforge/registries/IForgeRegistry";
        int rewritten = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode call)
                        || !buffer.equals(call.owner)
                        || call.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
                if (call.name.equals("writeRegistryId")
                        && call.desc.equals("(L" + legacyRegistry + ";Ljava/lang/Object;)V")) {
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = helper;
                    call.desc = "(L" + buffer + ";L" + legacyRegistry + ";Ljava/lang/Object;)V";
                    call.itf = false;
                    rewritten++;
                } else if (call.name.equals("readRegistryId")
                        && call.desc.equals("()Ljava/lang/Object;")) {
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = helper;
                    call.desc = "(L" + buffer + ";)Ljava/lang/Object;";
                    call.itf = false;
                    rewritten++;
                }
            }
        }
        return rewritten;
    }

    private static int rewriteLegacyFriendlyByteBufFluidStackCalls(ClassNode node) {
        String buffer = "net/minecraft/network/FriendlyByteBuf";
        String readDesc = "()L" + FLUID_STACK + ";";
        String writeDesc = "(L" + FLUID_STACK + ";)V";
        int rewritten = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode call)
                        || !call.owner.equals(buffer)
                        || (call.getOpcode() != Opcodes.INVOKEVIRTUAL && call.getOpcode() != Opcodes.INVOKEINTERFACE)) {
                    continue;
                }
                if (call.name.equals("readFluidStack") && call.desc.equals(readDesc)) {
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = LEGACY_FLUID_STACK;
                    call.name = "readFromPacket";
                    call.desc = "(L" + buffer + ";)L" + FLUID_STACK + ";";
                    call.itf = false;
                    rewritten++;
                } else if (call.name.equals("writeFluidStack") && call.desc.equals(writeDesc)) {
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = LEGACY_FLUID_STACK;
                    call.name = "writeToPacket";
                    call.desc = "(L" + buffer + ";L" + FLUID_STACK + ";)V";
                    call.itf = false;
                    rewritten++;
                }
            }
        }
        return rewritten;
    }

    private static int rewriteEmptyFluidHandlerConstructors(ClassNode node) {
        String owner = "net/minecraftforge/fluids/capability/templates/EmptyFluidHandler";
        int replaced = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (!(insn instanceof org.objectweb.asm.tree.TypeInsnNode allocation)
                        || allocation.getOpcode() != Opcodes.NEW || !owner.equals(allocation.desc)) {
                    continue;
                }
                AbstractInsnNode duplicate = nextMeaningful(allocation);
                AbstractInsnNode constructor = duplicate == null ? null : nextMeaningful(duplicate);
                if (duplicate == null || duplicate.getOpcode() != Opcodes.DUP
                        || !(constructor instanceof MethodInsnNode call)
                        || call.getOpcode() != Opcodes.INVOKESPECIAL || !owner.equals(call.owner)
                        || !"<init>".equals(call.name) || !"()V".equals(call.desc)) {
                    continue;
                }
                method.instructions.insertBefore(allocation, new org.objectweb.asm.tree.FieldInsnNode(
                        Opcodes.GETSTATIC, owner, "INSTANCE", "L" + owner + ";"));
                method.instructions.remove(allocation);
                method.instructions.remove(duplicate);
                method.instructions.remove(call);
                replaced++;
            }
        }
        return replaced;
    }

    private static int rewriteLegacyConditionListConstructors(ClassNode node) {
        String prefix = "net/minecraftforge/common/crafting/conditions/";
        String descriptor = "([L" + prefix + "ICondition;)V";
        int replaced = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (!(insn instanceof MethodInsnNode call)
                        || call.getOpcode() != Opcodes.INVOKESPECIAL || !"<init>".equals(call.name)
                        || !descriptor.equals(call.desc)
                        || !(prefix.concat("AndCondition").equals(call.owner)
                                || prefix.concat("OrCondition").equals(call.owner))) {
                    continue;
                }
                method.instructions.insertBefore(call, new MethodInsnNode(Opcodes.INVOKESTATIC,
                        "net/neoforged/forgestuck/compat/LegacyConditionArrays", "asList",
                        descriptor.substring(0, descriptor.length() - 1) + "Ljava/util/List;", false));
                call.desc = "(Ljava/util/List;)V";
                replaced++;
            }
        }
        return replaced;
    }

    private static int rewriteLegacyMobEffectRemoveConstructors(ClassNode node) {
        String owner = "net/minecraftforge/event/entity/living/MobEffectEvent$Remove";
        String constructor = "(Lnet/minecraft/world/entity/LivingEntity;"
                + "Lnet/minecraft/world/effect/MobEffectInstance;)V";
        String targetConstructor = constructor.substring(0, constructor.length() - 2)
                + "Lnet/neoforged/neoforge/common/EffectCure;)V";
        int rewritten = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode call)
                        || call.getOpcode() != Opcodes.INVOKESPECIAL
                        || !owner.equals(call.owner)
                        || !"<init>".equals(call.name)
                        || !constructor.equals(call.desc)) continue;
                method.instructions.insertBefore(call, new InsnNode(Opcodes.ACONST_NULL));
                call.desc = targetConstructor;
                method.maxStack++;
                rewritten++;
            }
        }
        return rewritten;
    }

    private static int rewriteLegacyItemHandlerHelperCalls(ClassNode node) {
        String owner = "net/minecraftforge/items/ItemHandlerHelper";
        String helper = "net/neoforged/forgestuck/compat/LegacyItemHandlerHelper";
        String itemStack = "Lnet/minecraft/world/item/ItemStack;";
        int replaced = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (!(insn instanceof MethodInsnNode call)
                        || call.getOpcode() != Opcodes.INVOKESTATIC || !owner.equals(call.owner)
                        || !(("canItemStacksStack".equals(call.name)
                                        && ("(" + itemStack + itemStack + ")Z").equals(call.desc))
                                || ("copyStackWithSize".equals(call.name)
                                        && ("(" + itemStack + "I)" + itemStack).equals(call.desc)))) {
                    continue;
                }
                call.owner = helper;
                call.itf = false;
                replaced++;
            }
        }
        return replaced;
    }

    private static int rewriteCustomLoaderBuilderConstructors(ClassNode node) {
        String owner = "net/minecraftforge/client/model/generators/CustomLoaderBuilder";
        String descriptor = "(Lnet/minecraft/resources/ResourceLocation;"
                + "Lnet/minecraftforge/client/model/generators/ModelBuilder;"
                + "Lnet/minecraftforge/common/data/ExistingFileHelper;)V";
        int replaced = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (!(insn instanceof MethodInsnNode call)
                        || !owner.equals(call.owner)
                        || !"<init>".equals(call.name)
                        || !descriptor.equals(call.desc)) continue;
                method.instructions.insertBefore(call, new InsnNode(Opcodes.ICONST_1));
                call.desc = descriptor.substring(0, descriptor.length() - 2) + "Z)V";
                replaced++;
            }
        }
        return replaced;
    }

    private static String fluidStackFactoryDescriptor(String constructorDesc) {
        if (!FLUID_STACK_TAG_CTOR.equals(constructorDesc) && !FLUID_STACK_COPY_CTOR.equals(constructorDesc)) {
            return null;
        }
        return constructorDesc.substring(0, constructorDesc.length() - 1) + "L" + FLUID_STACK + ";";
    }

    private static AbstractInsnNode nextMeaningful(AbstractInsnNode insn) {
        AbstractInsnNode next = insn.getNext();
        while (next != null && (next.getType() == AbstractInsnNode.LABEL
                || next.getType() == AbstractInsnNode.LINE || next.getType() == AbstractInsnNode.FRAME)) {
            next = next.getNext();
        }
        return next;
    }

    private int replaceUnsupportedHandles(String className, ClassNode node, OwnerRemapReport report) {
        int replaced = 0;
        int nextHelper = 0;
        for (MethodNode method : List.copyOf(node.methods)) {
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn instanceof LdcInsnNode ldc) {
                    Object replacement = replaceUnsupportedHandle(className, node, method, ldc.cst, report,
                            nextHelper);
                    if (replacement instanceof Handle handle && replacement != ldc.cst) {
                        ldc.cst = handle;
                        nextHelper++;
                        replaced++;
                    }
                } else if (insn instanceof InvokeDynamicInsnNode indy) {
                    Object bootstrap = replaceUnsupportedHandle(className, node, method, indy.bsm, report,
                            nextHelper);
                    if (bootstrap instanceof Handle handle && bootstrap != indy.bsm) {
                        indy.bsm = handle;
                        nextHelper++;
                        replaced++;
                    }
                    for (int i = 0; i < indy.bsmArgs.length; i++) {
                        Object replacement = replaceUnsupportedHandle(className, node, method, indy.bsmArgs[i],
                                report, nextHelper);
                        if (replacement != indy.bsmArgs[i]) {
                            indy.bsmArgs[i] = replacement;
                            nextHelper++;
                            replaced++;
                        }
                    }
                }
            }
        }
        return replaced;
    }

    private Object replaceUnsupportedHandle(String className, ClassNode node, MethodNode method, Object value,
            OwnerRemapReport report, int helperIndex) {
        if (value instanceof Handle handle) {
            String mappedName = mappings.mappedMemberName(
                    handle.getOwner(), handle.getName(), handle.getDesc());
            if (mappedName != null) {
                return new Handle(handle.getTag(), handle.getOwner(), mappedName, handle.getDesc(),
                        handle.isInterface());
            }
            if (handle.getTag() == Opcodes.H_INVOKESTATIC
                    && handle.getOwner().equals(LEGACY_BURN_TIME_OWNER)
                    && handle.getName().equals("getBurnTime")
                    && handle.getDesc().equals(LEGACY_BURN_TIME_DESC)) {
                return new Handle(Opcodes.H_INVOKEINTERFACE, ITEM_STACK_EXTENSION, "getBurnTime",
                        BURN_TIME_EXTENSION_DESC, true);
            }
            String redirect = REDIRECTS.get(handle.getOwner() + "." + handle.getName() + handle.getDesc());
            if (redirect != null && handle.getTag() == Opcodes.H_INVOKESTATIC) {
                return new Handle(Opcodes.H_INVOKESTATIC, redirect, handle.getName(), handle.getDesc(), false);
            }
            if (redirect != null && (handle.getTag() == Opcodes.H_INVOKEVIRTUAL
                    || handle.getTag() == Opcodes.H_INVOKEINTERFACE)) {
                Type[] arguments = Type.getArgumentTypes(handle.getDesc());
                Type[] redirectedArguments = new Type[arguments.length + 1];
                redirectedArguments[0] = Type.getObjectType(handle.getOwner());
                System.arraycopy(arguments, 0, redirectedArguments, 1, arguments.length);
                return new Handle(Opcodes.H_INVOKESTATIC, redirect, handle.getName(),
                        Type.getMethodDescriptor(Type.getReturnType(handle.getDesc()), redirectedArguments), false);
            }
            ForgeOwnerMappings.Entry e = mappings.get(handle.getOwner());
            if (e == null || mappings.supportsMember(handle.getOwner(), handle.getName(), handle.getDesc())) {
                return value;
            }
            if (FLUID_STACK.equals(handle.getOwner()) && "<init>".equals(handle.getName())
                    && fluidStackFactoryDescriptor(handle.getDesc()) != null
                    && handle.getTag() == Opcodes.H_NEWINVOKESPECIAL) {
                return new Handle(Opcodes.H_INVOKESTATIC, LEGACY_FLUID_STACK, "create",
                        fluidStackFactoryDescriptor(handle.getDesc()), false);
            }
            String member = handle.getOwner().replace('/', '.') + "." + handle.getName() + handle.getDesc();
            String caller = className + "." + method.name;
            String helperDesc = unsupportedHandleDescriptor(handle);
            if (helperDesc == null) return value;
            String helperName = "$forgestuck$unsupported$" + helperIndex;
            while (hasMethod(node, helperName, helperDesc)) {
                helperIndex++;
                helperName = "$forgestuck$unsupported$" + helperIndex;
            }
            node.methods.add(unsupportedHandleStub(helperName, helperDesc, member, caller));
            if (report != null) report.unsupportedCall(member);
            LOGGER.debug("{} {} references unsupported handle {}; rewritten to fail when invoked", TAG, className,
                    member);
            return new Handle(Opcodes.H_INVOKESTATIC, node.name, helperName, helperDesc, false);
        }
        if (value instanceof org.objectweb.asm.ConstantDynamic dynamic) {
            Handle bootstrap = (Handle) replaceUnsupportedHandle(className, node, method, dynamic.getBootstrapMethod(),
                    report, helperIndex);
            Object[] args = new Object[dynamic.getBootstrapMethodArgumentCount()];
            boolean changed = bootstrap != dynamic.getBootstrapMethod();
            for (int i = 0; i < args.length; i++) {
                args[i] = replaceUnsupportedHandle(className, node, method, dynamic.getBootstrapMethodArgument(i),
                        report, helperIndex + i + 1);
                changed |= args[i] != dynamic.getBootstrapMethodArgument(i);
            }
            if (changed) return new org.objectweb.asm.ConstantDynamic(dynamic.getName(), dynamic.getDescriptor(),
                    bootstrap, args);
        }
        return value;
    }

    private static boolean hasMethod(ClassNode node, String name, String desc) {
        for (MethodNode method : node.methods) {
            if (method.name.equals(name) && method.desc.equals(desc)) return true;
        }
        return false;
    }

    private static String unsupportedHandleDescriptor(Handle handle) {
        String desc = handle.getDesc();
        return switch (handle.getTag()) {
            case Opcodes.H_GETFIELD -> Type.getMethodDescriptor(Type.getType(desc),
                    Type.getObjectType(handle.getOwner()));
            case Opcodes.H_GETSTATIC -> Type.getMethodDescriptor(Type.getType(desc));
            case Opcodes.H_PUTFIELD -> Type.getMethodDescriptor(Type.VOID_TYPE,
                    Type.getObjectType(handle.getOwner()), Type.getType(desc));
            case Opcodes.H_PUTSTATIC -> Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(desc));
            case Opcodes.H_INVOKEVIRTUAL, Opcodes.H_INVOKEINTERFACE, Opcodes.H_INVOKESPECIAL -> {
                Type[] arguments = Type.getArgumentTypes(desc);
                Type[] staticArguments = new Type[arguments.length + 1];
                staticArguments[0] = Type.getObjectType(handle.getOwner());
                System.arraycopy(arguments, 0, staticArguments, 1, arguments.length);
                yield Type.getMethodDescriptor(Type.getReturnType(desc), staticArguments);
            }
            case Opcodes.H_INVOKESTATIC -> desc;
            case Opcodes.H_NEWINVOKESPECIAL ->
                    Type.getMethodDescriptor(Type.getObjectType(handle.getOwner()), Type.getArgumentTypes(desc));
            default -> null;
        };
    }

    private static MethodNode unsupportedHandleStub(String name, String desc, String member, String caller) {
        MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                name, desc, null, null);
        method.instructions.add(new LdcInsnNode(member));
        method.instructions.add(new LdcInsnNode(caller));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "net/neoforged/forgestuck/compat/UnsupportedForgeApi", "fail",
                "(Ljava/lang/String;Ljava/lang/String;)V", false));
        Type returnType = Type.getReturnType(desc);
        if (returnType.getSort() != Type.VOID) method.instructions.add(defaultValue(returnType));
        method.instructions.add(new InsnNode(returnType.getSort() == Type.VOID ? Opcodes.RETURN
                : returnType.getSize() == 2 ? (returnType.getSort() == Type.LONG ? Opcodes.LRETURN : Opcodes.DRETURN)
                        : switch (returnType.getSort()) {
                            case Type.OBJECT, Type.ARRAY -> Opcodes.ARETURN;
                            case Type.FLOAT -> Opcodes.FRETURN;
                            default -> Opcodes.IRETURN;
                        }));
        int locals = 0;
        for (Type argument : Type.getArgumentTypes(desc)) locals += argument.getSize();
        method.maxStack = 2;
        method.maxLocals = locals;
        return method;
    }

    /**
     * Replaces a field/method instruction with {@link UnsupportedForgeApi#fail} plus stack-neutral pops and a
     * default value, so the class links and verifies and only executing this exact path throws.
     */
    static void failAtCall(MethodNode m, AbstractInsnNode insn, String member, String caller) {
        boolean field = insn instanceof FieldInsnNode;
        String desc = field ? ((FieldInsnNode) insn).desc : ((MethodInsnNode) insn).desc;
        {
            {
                InsnList list = new InsnList();
                list.add(new LdcInsnNode(member));
                list.add(new LdcInsnNode(caller));
                list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/neoforged/forgestuck/compat/UnsupportedForgeApi",
                        "fail", "(Ljava/lang/String;Ljava/lang/String;)V", false));
                int op = insn.getOpcode();
                if (field) {
                    Type t = Type.getType(desc);
                    if (op == Opcodes.PUTSTATIC || op == Opcodes.PUTFIELD) list.add(pop(t));
                    if (op == Opcodes.GETFIELD || op == Opcodes.PUTFIELD) list.add(new InsnNode(Opcodes.POP));
                    if (op == Opcodes.GETSTATIC || op == Opcodes.GETFIELD) list.add(defaultValue(t));
                } else {
                    Type[] args = Type.getArgumentTypes(desc);
                    for (int i = args.length - 1; i >= 0; i--) list.add(pop(args[i]));
                    if (op != Opcodes.INVOKESTATIC) list.add(new InsnNode(Opcodes.POP));
                    Type ret = Type.getReturnType(desc);
                    if (ret.getSort() != Type.VOID) list.add(defaultValue(ret));
                }
                m.instructions.insertBefore(insn, list);
                m.instructions.remove(insn);
                m.maxStack += 2;
            }
        }
    }

    private static InsnNode pop(Type t) {
        return new InsnNode(t.getSize() == 2 ? Opcodes.POP2 : Opcodes.POP);
    }

    private static InsnNode defaultValue(Type t) {
        return new InsnNode(switch (t.getSort()) {
            case Type.LONG -> Opcodes.LCONST_0;
            case Type.FLOAT -> Opcodes.FCONST_0;
            case Type.DOUBLE -> Opcodes.DCONST_0;
            case Type.OBJECT, Type.ARRAY -> Opcodes.ACONST_NULL;
            default -> Opcodes.ICONST_0;
        });
    }

    @Override
    public void scan(String className, byte[] bytes, OwnerRemapReport report) {
        if (bytes == null) return;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node,
                ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        Set<String> supertypes = new HashSet<>(node.interfaces);
        if (node.superName != null) supertypes.add(node.superName);
        declaredSupertypes.put(node.name, Set.copyOf(supertypes));
    }

    private void collectBlocked(String className, ClassNode node, Set<String> blocked, int[] adapted,
            Set<String> uses) {
        for (MethodNode m : node.methods) {
            for (AbstractInsnNode insn : m.instructions.toArray()) {
                if (insn instanceof MethodInsnNode call) {
                    checkMember(className, call.owner, call.name, call.desc, blocked, uses);
                    if (!blocked.contains(call.owner) && changesDescriptor(call.desc, blocked)) adapted[0]++;
                } else if (insn instanceof FieldInsnNode field) {
                    checkMember(className, field.owner, field.name, field.desc, blocked, uses);
                } else if (insn instanceof InvokeDynamicInsnNode indy) {
                    checkHandle(className, indy.bsm, blocked, uses);
                    for (Object arg : indy.bsmArgs) if (arg instanceof Handle h) checkHandle(className, h, blocked, uses);
                }
            }
        }
    }

    /**
     * A member of mapped owner O whose descriptor mentions a mapped owner T that is blocked cannot be linked: the
     * rewritten call would target NeoForge's O with a descriptor still naming the legacy T (or the reverse). O is
     * therefore blocked as well, to a fixed point. Uses are {@code owner + '\0' + descriptor}.
     */
    private void closeDependencies(Set<String> uses, Set<String> blocked) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String use : uses) {
                int sep = use.indexOf('\0');
                String owner = use.substring(0, sep);
                if (blocked.contains(owner)) continue;
                java.util.regex.Matcher t = TYPE_TOKEN.matcher(use.substring(sep + 1));
                while (t.find()) {
                    String dep = t.group(1);
                    if (blocked.contains(dep) && mappings.get(dep) != null) {
                        blocked.add(owner);
                        LOGGER.warn("{} {} is used with {} which is unmapped in this jar; owner left unmapped too", TAG,
                                owner, dep);
                        changed = true;
                        break;
                    }
                }
            }
        }
    }

    private static final java.util.regex.Pattern TYPE_TOKEN = java.util.regex.Pattern.compile("L([^;()\\[]+);");

    /** True when the method descriptor mentions a mapped owner, i.e. it is adapted by the remap. */
    private boolean changesDescriptor(String desc, Set<String> blocked) {
        return !mappings.remapDescriptorTokens(desc, blocked, new java.util.HashMap<>()).equals(desc);
    }

    private void checkHandle(String className, Handle h, Set<String> blocked, Set<String> uses) {
        if (h.getTag() >= org.objectweb.asm.Opcodes.H_GETFIELD && h.getTag() <= org.objectweb.asm.Opcodes.H_PUTSTATIC
                || h.getTag() >= org.objectweb.asm.Opcodes.H_INVOKEVIRTUAL) {
            checkMember(className, h.getOwner(), h.getName(), h.getDesc(), blocked, uses);
        }
    }

    /** {@code java.lang.Object} members resolve identically on any replacement class. */
    private static boolean isObjectMethod(String name, String desc) {
        return "hashCode".equals(name) && "()I".equals(desc)
                || "equals".equals(name) && "(Ljava/lang/Object;)Z".equals(desc)
                || "toString".equals(name) && "()Ljava/lang/String;".equals(desc);
    }

    private void checkMember(String className, String owner, String name, String desc, Set<String> blocked,
            Set<String> uses) {
        ForgeOwnerMappings.Entry e = mappings.get(owner);
        if (e != null && desc.contains("L")) uses.add(owner + '\0' + desc);
        if (e == null || isObjectMethod(name, desc) || mappings.supportsMember(owner, name, desc)
                || !blocked.add(owner)) return;
        LOGGER.warn("{} {} uses {}.{}{} which has no verified equivalent on {}; owner left unmapped in this class",
                TAG, className, owner, name, desc, e.newOwner());
    }

    private void remapAnnotationStrings(List<AnnotationNode> annotations, Set<String> blocked,
            TreeMap<String, Integer> counts) {
        if (annotations == null) return;
        for (AnnotationNode a : annotations) {
            if (MixinClassSupport.isMixinAnnotation(a.desc)) remapAnnotation(a, blocked, counts);
        }
    }

    @SuppressWarnings("unchecked")
    private void remapAnnotation(AnnotationNode node, Set<String> blocked, TreeMap<String, Integer> counts) {
        if (node.values == null) return;
        for (int i = 0; i + 1 < node.values.size(); i += 2) {
            boolean eligible = node.values.get(i).equals("method") || node.values.get(i).equals("target");
            Object v = node.values.get(i + 1);
            if (v instanceof AnnotationNode nested) {
                remapAnnotation(nested, blocked, counts);
            } else if (v instanceof List<?> list) {
                List<Object> copy = (List<Object>) list;
                for (int j = 0; j < copy.size(); j++) {
                    Object e = copy.get(j);
                    if (e instanceof AnnotationNode nested) remapAnnotation(nested, blocked, counts);
                    else if (eligible && e instanceof String s) copy.set(j, mappings.remapDescriptorTokens(s, blocked, counts));
                }
            } else if (eligible && v instanceof String s) {
                node.values.set(i + 1, mappings.remapDescriptorTokens(s, blocked, counts));
            }
        }
    }

    private static final String FORGE_EBS = "Lnet/minecraftforge/fml/common/Mod$EventBusSubscriber;";
    private static final String FORGE_BUS = "Lnet/minecraftforge/fml/common/Mod$EventBusSubscriber$Bus;";

    /**
     * Forge's {@code Bus.FORGE} became {@code Bus.GAME}. ClassRemapper only rewrites the enum descriptor, so the
     * constant name is translated here (before the remap, while the old descriptor is still present).
     */
    @SuppressWarnings("unchecked")
    private static void translateEventBusEnum(List<AnnotationNode> annotations) {
        if (annotations == null) return;
        for (AnnotationNode a : annotations) {
            if (!FORGE_EBS.equals(a.desc) || a.values == null) continue;
            for (int i = 0; i + 1 < a.values.size(); i += 2) {
                if ("bus".equals(a.values.get(i)) && a.values.get(i + 1) instanceof String[] e
                        && e.length == 2 && FORGE_BUS.equals(e[0]) && "FORGE".equals(e[1])) {
                    a.values.set(i + 1, new String[] {e[0], "GAME"});
                }
            }
        }
    }

    private static boolean isMixin(ClassNode node) {
        for (List<AnnotationNode> list : java.util.Arrays.asList(node.invisibleAnnotations, node.visibleAnnotations)) {
            if (list == null) continue;
            for (AnnotationNode a : list) if (MixinClassSupport.MIXIN.equals(a.desc)) return true;
        }
        return false;
    }
}
