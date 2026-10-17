package net.neoforged.forgestuck.transformer.jar;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Remaps legacy SRG member names to runtime names where mapping data exists:
 * <ul>
 * <li>method/field instruction references whose owner is a Minecraft/NeoForge class,</li>
 * <li>{@code @Shadow} member declarations of Mixin classes (and references to them from the same class),</li>
 * <li>Mixin annotation {@code method}/{@code target} strings.</li>
 * </ul>
 * Members without a mapping are left untouched.
 */
public class SrgMemberRemapStep implements ClassTransformStep {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgestuck");
    private static final String TAG = "[Forge Stuck!!]";

    private final LegacyNameMappings mappings;

    public SrgMemberRemapStep() {
        this(LegacyNameMappings.defaults());
    }

    public SrgMemberRemapStep(LegacyNameMappings mappings) {
        this.mappings = mappings;
    }

    static boolean ownerIsGame(String owner) {
        return owner.startsWith("net/minecraft/") || owner.startsWith("com/mojang/") || owner.startsWith("net/neoforged/");
    }

    @Override
    public byte[] apply(String className, byte[] bytes) {
        if (bytes == null || className.startsWith("net.neoforged.forgestuck")) return bytes;
        // Quick reject: SRG names always contain "_" followed by digits; avoid parsing unrelated classes.
        String raw = new String(bytes, StandardCharsets.ISO_8859_1);
        if (!raw.contains("m_") && !raw.contains("f_") && !raw.contains("c_")) return bytes;

        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        boolean isMixin = hasAnnotation(node.invisibleAnnotations, MixinClassSupport.MIXIN)
                || hasAnnotation(node.visibleAnnotations, MixinClassSupport.MIXIN);

        Set<String> remapped = new TreeSet<>();
        boolean modified = false;
        Set<String> ownRenamed = new HashSet<>();
        Set<String> unresolved = new TreeSet<>();

        if (isMixin) {
            for (MethodNode m : node.methods) {
                String mapped = LegacyNameMappings.looksLikeSrg(m.name) && isShadow(m.invisibleAnnotations, m.visibleAnnotations)
                        ? mappings.map(node.name, m.name, m.desc) : null;
                if (mapped != null) {
                    remapped.add(m.name + "->" + mapped);
                    ownRenamed.add("M" + m.name + m.desc + "=" + mapped);
                    m.name = mapped;
                    modified = true;
                }
            }
            for (FieldNode f : node.fields) {
                String mapped = LegacyNameMappings.looksLikeSrg(f.name) && isShadow(f.invisibleAnnotations, f.visibleAnnotations)
                        ? mappings.map(node.name, f.name, f.desc) : null;
                if (mapped != null) {
                    remapped.add(f.name + "->" + mapped);
                    ownRenamed.add("F" + f.name + "=" + mapped);
                    f.name = mapped;
                    modified = true;
                }
            }
            modified |= remapAnnotations(node.invisibleAnnotations, remapped);
            modified |= remapAnnotations(node.visibleAnnotations, remapped);
            for (MethodNode m : node.methods) {
                modified |= remapAnnotations(m.invisibleAnnotations, remapped);
                modified |= remapAnnotations(m.visibleAnnotations, remapped);
            }
            for (FieldNode f : node.fields) {
                modified |= remapAnnotations(f.invisibleAnnotations, remapped);
                modified |= remapAnnotations(f.visibleAnnotations, remapped);
            }
        }

        for (MethodNode m : node.methods) {
            java.util.Map<AbstractInsnNode, String> unmapped = new java.util.LinkedHashMap<>();
            for (AbstractInsnNode insn : m.instructions.toArray()) {
                if (insn instanceof MethodInsnNode call && LegacyNameMappings.looksLikeSrg(call.name)) {
                    String mapped = mappings.map(call.owner, call.name, call.desc);
                    if (mapped == null) {
                        if (!isMixin && ownerIsGame(call.owner) && !call.owner.equals(node.name)) {
                            unmapped.put(call, call.owner.replace('/', '.') + "." + call.name + call.desc);
                        }
                        continue;
                    }
                    boolean own = call.owner.equals(node.name)
                            && ownRenamed.contains("M" + call.name + call.desc + "=" + mapped);
                    if (own || ownerIsGame(call.owner)) {
                        remapped.add(call.name + "->" + mapped);
                        call.name = mapped;
                        modified = true;
                    }
                } else if (insn instanceof FieldInsnNode field && LegacyNameMappings.looksLikeSrg(field.name)) {
                    String mapped = mappings.map(field.owner, field.name, field.desc);
                    if (mapped == null) {
                        if (!isMixin && ownerIsGame(field.owner) && !field.owner.equals(node.name)) {
                            unmapped.put(field, field.owner.replace('/', '.') + "." + field.name);
                        }
                        continue;
                    }
                    boolean own = field.owner.equals(node.name) && ownRenamed.contains("F" + field.name + "=" + mapped);
                    if (own || ownerIsGame(field.owner)) {
                        remapped.add(field.name + "->" + mapped);
                        field.name = mapped;
                        modified = true;
                    }
                }
            }
            for (var e : unmapped.entrySet()) {
                ForgeOwnerRemapStep.failAtCall(m, e.getKey(), e.getValue() + " (legacy SRG name without a 1.21.1 mapping)", node.name + "." + m.name);
                unresolved.add(e.getValue());
                modified = true;
            }
        }

        if (!modified) return bytes;
        if (!remapped.isEmpty()) {
            LOGGER.info("{} remapped {} legacy member name(s) in {}: {}", TAG, remapped.size(), className, remapped);
        }
        if (!unresolved.isEmpty()) {
            LOGGER.warn("{} {} references legacy SRG member(s) with no 1.21.1 mapping; they link but throw "
                    + "UnsupportedOperationException if executed: {}", TAG, className, unresolved);
        }
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private boolean remapAnnotations(List<AnnotationNode> annotations, Set<String> remapped) {
        if (annotations == null) return false;
        boolean changed = false;
        for (AnnotationNode a : annotations) {
            if (MixinClassSupport.isMixinAnnotation(a.desc)) {
                changed |= MixinClassSupport.remapAnnotation(a, mappings, remapped);
            }
        }
        return changed;
    }

    private static boolean hasAnnotation(List<AnnotationNode> list, String desc) {
        if (list == null) return false;
        for (AnnotationNode a : list) if (desc.equals(a.desc)) return true;
        return false;
    }

    private static boolean isShadow(List<AnnotationNode> a, List<AnnotationNode> b) {
        return hasAnnotation(a, MixinClassSupport.SHADOW) || hasAnnotation(b, MixinClassSupport.SHADOW);
    }
}
