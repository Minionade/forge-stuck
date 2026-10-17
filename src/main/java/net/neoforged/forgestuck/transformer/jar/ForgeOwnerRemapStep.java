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
        int replaced = mixin ? 0 : replaceUnsupported(className, node, report);
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

        ClassWriter writer = new ClassWriter(0);
        node.accept(new ClassRemapper(writer, mappings.remapper(blocked, counts)));
        if (counts.isEmpty() && replaced == 0) return bytes;
        if (report != null) report.record(counts, mappings, adapted[0]);
        LOGGER.debug("{} remapped Forge owners in {}: {}", TAG, className, counts);
        return writer.toByteArray();
    }

    /** Instance calls on a mapped owner that are re-pointed at a static compat helper taking the receiver first. */
    private static final java.util.Map<String, String> REDIRECTS = java.util.Map.of(
            "net/minecraftforge/common/capabilities/RegisterCapabilitiesEvent.register(Ljava/lang/Class;)V",
            "net/neoforged/forgestuck/compat/LegacyCapabilities");

    /**
     * Unsupported members no longer leave their owner unmapped (which guaranteed NoClassDefFoundError for every
     * other use of that owner). The call site is instead replaced by a call to {@link UnsupportedForgeApi#fail}
     * followed by stack-neutral pops and a default value, so the class links and verifies, and only executing that
     * exact path throws {@link UnsupportedOperationException}. Constructors (uninitialized-object stack shape),
     * method handles and Mixin classes cannot be rewritten this way and keep the owner unmapped in that class.
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
                InsnList list = new InsnList();
                list.add(new LdcInsnNode(member));
                list.add(new LdcInsnNode(className + "." + m.name));
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
                replaced++;
                if (report != null) report.unsupportedCall(member);
                LOGGER.debug("{} {} calls {} which has no verified equivalent; rewritten to fail at call time", TAG,
                        className, member);
            }
        }
        return replaced;
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
