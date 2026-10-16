package net.neoforged.forgestuck.transformer.jar;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;

/** Helpers for walking Mixin annotations; used only for validated Mixin metadata contexts. */
final class MixinClassSupport {
    static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
    private static final String MIXIN_PKG = "Lorg/spongepowered/asm/mixin/";

    private MixinClassSupport() {}

    static boolean isMixinAnnotation(String desc) {
        return desc != null && desc.startsWith(MIXIN_PKG);
    }

    static boolean isNameValuedAnnotation(String desc) {
        return desc != null && (desc.startsWith(MIXIN_PKG + "gen/Accessor;") || desc.startsWith(MIXIN_PKG + "gen/Invoker;"));
    }

    /** Remaps {@code method}/{@code target} (and Accessor/Invoker {@code value}) strings; returns true on change. */
    @SuppressWarnings("unchecked")
    static boolean remapAnnotation(AnnotationNode node, LegacyNameMappings mappings, Set<String> remapped) {
        if (node.values == null) return false;
        boolean changed = false;
        boolean nameValued = isNameValuedAnnotation(node.desc);
        for (int i = 0; i + 1 < node.values.size(); i += 2) {
            String key = (String) node.values.get(i);
            Object v = node.values.get(i + 1);
            boolean eligible = key.equals("method") || key.equals("target") || (nameValued && key.equals("value"));
            if (v instanceof AnnotationNode nested) {
                changed |= remapAnnotation(nested, mappings, remapped);
            } else if (v instanceof List<?> list) {
                List<Object> copy = (List<Object>) list;
                for (int j = 0; j < copy.size(); j++) {
                    Object e = copy.get(j);
                    if (e instanceof AnnotationNode nested) {
                        changed |= remapAnnotation(nested, mappings, remapped);
                    } else if (eligible && e instanceof String s) {
                        String r = mappings.remapTokens(s, remapped);
                        if (!r.equals(s)) {
                            copy.set(j, r);
                            changed = true;
                        }
                    }
                }
            } else if (eligible && v instanceof String s) {
                String r = mappings.remapTokens(s, remapped);
                if (!r.equals(s)) {
                    node.values.set(i + 1, r);
                    changed = true;
                }
            }
        }
        return changed;
    }

    /** Collects {@code method}/{@code target} strings from a Mixin annotation tree. */
    @SuppressWarnings("unchecked")
    static void collectMemberStrings(AnnotationNode node, List<String> out) {
        if (node.values == null) return;
        for (int i = 0; i + 1 < node.values.size(); i += 2) {
            String key = (String) node.values.get(i);
            Object v = node.values.get(i + 1);
            boolean eligible = key.equals("method") || key.equals("target");
            if (v instanceof AnnotationNode nested) {
                collectMemberStrings(nested, out);
            } else if (v instanceof List<?> list) {
                for (Object e : list) {
                    if (e instanceof AnnotationNode nested) collectMemberStrings(nested, out);
                    else if (eligible && e instanceof String s) out.add(s);
                }
            } else if (eligible && v instanceof String s) {
                out.add(s);
            }
        }
    }

    /** Target class names (dotted) declared by an {@code @Mixin} annotation. */
    static List<String> mixinTargets(AnnotationNode mixin) {
        List<String> targets = new ArrayList<>();
        if (mixin.values == null) return targets;
        for (int i = 0; i + 1 < mixin.values.size(); i += 2) {
            String key = (String) mixin.values.get(i);
            Object v = mixin.values.get(i + 1);
            if (!(v instanceof List<?> list)) continue;
            for (Object e : list) {
                if (key.equals("value") && e instanceof Type t) targets.add(t.getClassName());
                else if (key.equals("targets") && e instanceof String s) targets.add(s.replace('/', '.'));
            }
        }
        return targets;
    }
}
