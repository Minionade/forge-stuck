package net.neoforged.forgestuck.transformer.jar;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
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
        for (MethodNode m : node.methods) {
            for (AbstractInsnNode insn : m.instructions.toArray()) {
                if (insn instanceof MethodInsnNode call) {
                    checkMember(className, call.owner, call.name, call.desc, blocked);
                    if (!blocked.contains(call.owner) && changesDescriptor(call.desc, blocked)) adapted[0]++;
                } else if (insn instanceof FieldInsnNode field) {
                    checkMember(className, field.owner, field.name, field.desc, blocked);
                } else if (insn instanceof InvokeDynamicInsnNode indy) {
                    checkHandle(className, indy.bsm, blocked);
                    for (Object arg : indy.bsmArgs) if (arg instanceof Handle h) checkHandle(className, h, blocked);
                }
            }
        }

        TreeMap<String, Integer> counts = new TreeMap<>();
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
        if (counts.isEmpty()) return bytes;
        if (report != null) report.record(counts, mappings, adapted[0]);
        LOGGER.debug("{} remapped Forge owners in {}: {}", TAG, className, counts);
        return writer.toByteArray();
    }

    /** True when the method descriptor mentions a mapped owner, i.e. it is adapted by the remap. */
    private boolean changesDescriptor(String desc, Set<String> blocked) {
        return !mappings.remapDescriptorTokens(desc, blocked, new java.util.HashMap<>()).equals(desc);
    }

    private void checkHandle(String className, Handle h, Set<String> blocked) {
        if (h.getTag() >= org.objectweb.asm.Opcodes.H_GETFIELD && h.getTag() <= org.objectweb.asm.Opcodes.H_PUTSTATIC
                || h.getTag() >= org.objectweb.asm.Opcodes.H_INVOKEVIRTUAL) {
            checkMember(className, h.getOwner(), h.getName(), h.getDesc(), blocked);
        }
    }

    private void checkMember(String className, String owner, String name, String desc, Set<String> blocked) {
        ForgeOwnerMappings.Entry e = mappings.get(owner);
        if (e == null || e.supports(name, desc) || !blocked.add(owner)) return;
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

    private static boolean isMixin(ClassNode node) {
        for (List<AnnotationNode> list : java.util.Arrays.asList(node.invisibleAnnotations, node.visibleAnnotations)) {
            if (list == null) continue;
            for (AnnotationNode a : list) if (MixinClassSupport.MIXIN.equals(a.desc)) return true;
        }
        return false;
    }
}
