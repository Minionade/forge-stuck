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
    private static final String INHERITED_RESOURCE_LOCATION_WITH_SUFFIX =
            "m_266382_(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;";

    private final LegacyNameMappings mappings;

    public SrgMemberRemapStep() {
        this(LegacyNameMappings.defaults());
    }

    public SrgMemberRemapStep(LegacyNameMappings mappings) {
        this.mappings = mappings;
    }

    /** Removed game members with a faithful runtime stand-in: {@code owner.name+desc} -> {owner, name}. */
    private static final java.util.Map<String, String[]> REPLACEMENTS = java.util.Map.of(
            "net/minecraft/core/registries/BuiltInRegistries.f_256876_Lnet/minecraft/core/Registry;",
            new String[] {"net/neoforged/forgestuck/compat/LegacyEnchantmentRegistry", "ENCHANTMENT"},
            "net/minecraft/core/registries/BuiltInRegistries.f_257047_Lnet/minecraft/core/Registry;",
            new String[] {"net/neoforged/forgestuck/compat/LegacyRegistryRoot", "REGISTRY"});

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

        if (!isMixin) modified |= addOverrideBridges(node, remapped);

        for (MethodNode m : node.methods) {
            java.util.Map<AbstractInsnNode, String> unmapped = new java.util.LinkedHashMap<>();
            for (AbstractInsnNode insn : m.instructions.toArray()) {
                if (insn instanceof MethodInsnNode call && isLegacyItemStackGetTag(call)) {
                    call.setOpcode(org.objectweb.asm.Opcodes.INVOKESTATIC);
                    call.owner = "net/neoforged/forgestuck/compat/LegacyItemStackShim";
                    call.name = "getTag";
                    call.desc = "(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/nbt/CompoundTag;";
                    call.itf = false;
                    modified = true;
                    remapped.add("ItemStack.getTag -> LegacyItemStackShim.getTag");
                } else if (insn instanceof MethodInsnNode call && LegacyNameMappings.looksLikeSrg(call.name)) {
                    String mapped = mappings.map(call.owner, call.name, call.desc);
                    if (mapped == null) {
                        if (!isMixin && ownerIsGame(call.owner) && !call.owner.equals(node.name)) {
                            unmapped.put(call, call.owner.replace('/', '.') + "." + call.name + call.desc);
                        }
                        continue;
                    }
                    boolean own = call.owner.equals(node.name)
                            && ownRenamed.contains("M" + call.name + call.desc + "=" + mapped);
                    boolean inherited = call.owner.equals(node.name)
                            && node.methods.stream().noneMatch(declared -> declared.name.equals(call.name)
                                    && declared.desc.equals(call.desc));
                    if (own || ownerIsGame(call.owner) || inherited || isInheritedResourceLocationMethod(call)) {
                        remapped.add(call.name + "->" + mapped);
                        call.name = mapped;
                        modified = true;
                    }
                } else if (insn instanceof org.objectweb.asm.tree.InvokeDynamicInsnNode indy && indy.bsmArgs != null) {
                    for (int i = 0; i < indy.bsmArgs.length; i++) {
                        if (indy.bsmArgs[i] instanceof org.objectweb.asm.Handle h && ownerIsGame(h.getOwner())
                                && LegacyNameMappings.looksLikeSrg(h.getName())) {
                            String mapped = mappings.map(h.getOwner(), h.getName(), h.getDesc());
                            if (mapped == null) continue;
                            remapped.add(h.getName() + "->" + mapped);
                            indy.bsmArgs[i] = new org.objectweb.asm.Handle(h.getTag(), h.getOwner(), mapped, h.getDesc(),
                                    h.isInterface());
                            modified = true;
                        }
                    }
                } else if (insn instanceof FieldInsnNode field && LegacyNameMappings.looksLikeSrg(field.name)) {
                    String[] repl = isMixin ? null : REPLACEMENTS.get(field.owner + "." + field.name + field.desc);
                    if (repl != null) {
                        remapped.add(field.name + "->" + repl[0] + "." + repl[1]);
                        field.owner = repl[0];
                        field.name = repl[1];
                        modified = true;
                        continue;
                    }
                    String mapped = mappings.map(field.owner, field.name, field.desc);
                    if (mapped == null) {
                        if (!isMixin && ownerIsGame(field.owner) && !field.owner.equals(node.name)) {
                            unmapped.put(field, field.owner.replace('/', '.') + "." + field.name);
                        }
                        continue;
                    }
                    boolean own = field.owner.equals(node.name) && ownRenamed.contains("F" + field.name + "=" + mapped);
                    boolean inherited = field.owner.equals(node.name)
                            && node.fields.stream().noneMatch(declared -> declared.name.equals(field.name)
                                    && declared.desc.equals(field.desc));
                    if (own || ownerIsGame(field.owner) || inherited) {
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

    private static boolean isLegacyItemStackGetTag(MethodInsnNode call) {
        return call.getOpcode() == org.objectweb.asm.Opcodes.INVOKEVIRTUAL
                && call.owner.equals("net/minecraft/world/item/ItemStack")
                && (call.name.equals("m_41783_") || call.name.equals("getTag"))
                && call.desc.equals("()Lnet/minecraft/nbt/CompoundTag;");
    }

    private static boolean isInheritedResourceLocationMethod(MethodInsnNode call) {
        return call.getOpcode() == org.objectweb.asm.Opcodes.INVOKEVIRTUAL
                && (call.name + call.desc).equals(INHERITED_RESOURCE_LOCATION_WITH_SUFFIX);
    }

    /**
     * A legacy class implementing a game interface/superclass method under its SRG name never satisfies the 1.21.1
     * (named) method; add a named bridge that delegates to the SRG-named implementation.
     */
    private boolean addOverrideBridges(ClassNode node, Set<String> remapped) {
        List<String> supers = new java.util.ArrayList<>(node.interfaces);
        if (node.superName != null) supers.add(node.superName);
        List<MethodNode> bridges = new java.util.ArrayList<>();
        for (MethodNode m : node.methods) {
            if (!LegacyNameMappings.looksLikeSrg(m.name) || m.name.equals("<init>") || m.name.equals("<clinit>")) continue;
            if ((m.access & (org.objectweb.asm.Opcodes.ACC_STATIC | org.objectweb.asm.Opcodes.ACC_ABSTRACT
                    | org.objectweb.asm.Opcodes.ACC_PRIVATE)) != 0) continue;
            for (String sup : supers) {
                if (!ownerIsGame(sup)) continue;
                String mapped = mappings.map(sup, m.name, m.desc);
                if (mapped == null || mapped.equals(m.name)) continue;
                boolean exists = node.methods.stream().anyMatch(o -> o.name.equals(mapped) && o.desc.equals(m.desc))
                        || bridges.stream().anyMatch(o -> o.name.equals(mapped) && o.desc.equals(m.desc));
                if (exists) break;
                MethodNode b = new MethodNode(org.objectweb.asm.Opcodes.ACC_PUBLIC | org.objectweb.asm.Opcodes.ACC_SYNTHETIC,
                        mapped, m.desc, null, null);
                org.objectweb.asm.Type[] args = org.objectweb.asm.Type.getArgumentTypes(m.desc);
                org.objectweb.asm.Type ret = org.objectweb.asm.Type.getReturnType(m.desc);
                b.visitCode();
                b.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 0);
                int slot = 1;
                for (org.objectweb.asm.Type a : args) {
                    b.visitVarInsn(a.getOpcode(org.objectweb.asm.Opcodes.ILOAD), slot);
                    slot += a.getSize();
                }
                b.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKEVIRTUAL, node.name, m.name, m.desc, false);
                b.visitInsn(ret.getOpcode(org.objectweb.asm.Opcodes.IRETURN));
                b.visitMaxs(Math.max(slot, ret.getSize()), slot);
                b.visitEnd();
                bridges.add(b);
                remapped.add(m.name + "->" + mapped + " (bridge)");
                break;
            }
        }
        node.methods.addAll(bridges);
        return !bridges.isEmpty();
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
