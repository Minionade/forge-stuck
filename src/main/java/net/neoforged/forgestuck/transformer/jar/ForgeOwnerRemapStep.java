package net.neoforged.forgestuck.transformer.jar;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
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
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
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
    private static final String FLUID_STACK_TAG_CTOR =
            "(Lnet/minecraft/world/level/material/Fluid;ILnet/minecraft/nbt/CompoundTag;)V";
    private static final String FLUID_STACK_COPY_CTOR = "(L" + FLUID_STACK + ";I)V";
    private static final String PICKUP = "net/minecraftforge/event/entity/player/EntityItemPickupEvent";
    private static final String LEGACY_PICKUP = "net/neoforged/forgestuck/compat/LegacyItemPickup";
    private static final String LEGACY_FLUID_STACK = "net/neoforged/forgestuck/compat/LegacyFluidStack";

    private final ForgeOwnerMappings mappings;

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
        if (bytes == null || className.startsWith("net.neoforged.forgestuck") || !mappings.mayReference(bytes)) {
            return bytes;
        }
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);

        Set<String> blocked = new HashSet<>();
        int[] adapted = {0};
        Set<String> uses = new HashSet<>();
        boolean mixin = isMixin(node);
        int replaced = mixin ? 0 : rewriteLegacyFluidStackConstructors(className, node);
        if (!mixin) {
            replaced += rewriteGenericListenerCalls(node);
            replaced += rewriteByteBufCodecCalls(node);
            replaced += rewriteLegacyEntityDataSerializerFactories(node);
        }
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
        if (counts.isEmpty() && replaced == 0) return bytes;
        if (report != null) report.record(counts, mappings, adapted[0]);
        LOGGER.debug("{} remapped Forge owners in {}: {}", TAG, className, counts);
        return writer.toByteArray();
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

    /** Instance calls on a mapped owner that are re-pointed at a static compat helper taking the receiver first. */
    private static final java.util.Map<String, String> REDIRECTS = java.util.Map.ofEntries(
            java.util.Map.entry("net/minecraftforge/eventbus/api/IEventBus.register(Ljava/lang/Object;)V", "net/neoforged/forgestuck/compat/LegacyEventBusRegistration"),
            java.util.Map.entry("net/minecraftforge/common/ForgeConfigSpec.setConfig(Lcom/electronwill/nightconfig/core/CommentedConfig;)V", "net/neoforged/forgestuck/compat/LegacyConfigSpec"),
            java.util.Map.entry("net/minecraftforge/common/capabilities/RegisterCapabilitiesEvent.register(Ljava/lang/Class;)V", "net/neoforged/forgestuck/compat/LegacyCapabilities"),
            java.util.Map.entry("net/minecraftforge/event/level/SaplingGrowTreeEvent.getRandomSource()Lnet/minecraft/util/RandomSource;", "net/neoforged/forgestuck/compat/LegacySaplingGrowth"),
            java.util.Map.entry("net/minecraftforge/event/level/SaplingGrowTreeEvent.setResult(Lnet/minecraftforge/eventbus/api/Event$Result;)V", "net/neoforged/forgestuck/compat/LegacySaplingGrowth"),
            java.util.Map.entry("net/minecraftforge/event/level/SaplingGrowTreeEvent.getResult()Lnet/minecraftforge/eventbus/api/Event$Result;", "net/neoforged/forgestuck/compat/LegacySaplingGrowth"),
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
                String helper = field ? null : REDIRECTS.get(owner + "." + name + desc);
                if (helper != null) {
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
                if (e == null || "<init>".equals(name) || isObjectMethod(name, desc) || e.supports(name, desc)) continue;
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
    private static final String GENERIC_LISTENER_HELPER =
            "net/neoforged/forgestuck/compat/LegacyGenericListenerRegistration";

    private static int rewriteGenericListenerCalls(ClassNode node) {
        int rewritten = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (!(insn instanceof MethodInsnNode call)
                        || !GENERIC_LISTENER_OWNER.equals(call.owner)
                        || !"addGenericListener".equals(call.name)
                        || !GENERIC_LISTENER_DESC.equals(call.desc)) {
                    continue;
                }
                Type listenerEventType = invokedConsumerEventType(previousMeaningful(call));
                if (listenerEventType == null) {
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = GENERIC_LISTENER_HELPER;
                    call.name = "addGenericListener";
                    call.desc = "(Lnet/neoforged/bus/api/IEventBus;" + GENERIC_LISTENER_DESC.substring(1);
                    call.itf = false;
                } else {
                    method.instructions.insertBefore(call, new LdcInsnNode(listenerEventType));
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = GENERIC_LISTENER_HELPER;
                    call.name = "addGenericListener";
                    call.desc = "(Lnet/neoforged/bus/api/IEventBus;" + GENERIC_LISTENER_DESC.substring(1)
                            .replace(")V", "Ljava/lang/Class;)V");
                    call.itf = false;
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
            ForgeOwnerMappings.Entry e = mappings.get(handle.getOwner());
            if (e == null || e.supports(handle.getName(), handle.getDesc())) {
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

    /**
     * Retained for API compatibility: blocking is now class-local (see {@link #apply}), so there is no jar-wide
     * pre-pass state to collect.
     */
    @Override
    public void scan(String className, byte[] bytes, OwnerRemapReport report) {}

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
        if (e == null || isObjectMethod(name, desc) || e.supports(name, desc) || !blocked.add(owner)) return;
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
