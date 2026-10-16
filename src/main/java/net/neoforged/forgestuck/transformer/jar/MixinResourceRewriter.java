package net.neoforged.forgestuck.transformer.jar;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-jar mixin handling: finds Mixin classes that cannot work on the runtime (missing target class or unresolved
 * legacy SRG member names), then rewrites mixin configs (dropping only those entries) and refmaps (remapping legacy
 * member names). Non-mixin resources are never touched.
 */
public class MixinResourceRewriter {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgestuck");
    private static final String TAG = "[Forge Stuck!!]";

    private final LegacyNameMappings mappings;
    private final String jarName;
    private final ForgeOwnerMappings owners;
    /** dotted mixin class name -> reason it was dropped */
    private final Map<String, String> dropped = new LinkedHashMap<>();
    private final TreeSet<String> rewrittenConfigs = new TreeSet<>();
    private int refmapEntriesRemapped;
    private int refmapEntriesDropped;

    public MixinResourceRewriter(LegacyNameMappings mappings, String jarName) {
        this(mappings, jarName, ForgeOwnerMappings.defaults());
    }

    public MixinResourceRewriter(LegacyNameMappings mappings, String jarName, ForgeOwnerMappings owners) {
        this.mappings = mappings;
        this.jarName = jarName;
        this.owners = owners;
    }

    /** Inspects an already transformed class; records it as unusable when its mixin cannot be applied. */
    public void inspectClass(String className, byte[] bytes) {
        if (bytes == null || !new String(bytes, StandardCharsets.ISO_8859_1).contains(MixinClassSupport.MIXIN.substring(1, MixinClassSupport.MIXIN.length() - 1))) {
            return;
        }
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            AnnotationNode mixin = find(node.invisibleAnnotations, MixinClassSupport.MIXIN);
            if (mixin == null) mixin = find(node.visibleAnnotations, MixinClassSupport.MIXIN);
            if (mixin == null) return;
            for (String target : MixinClassSupport.mixinTargets(mixin)) {
                if (mappings.isMissingTarget(target)) {
                    drop(className, "target class " + target + " does not exist on this Minecraft version");
                    return;
                }
            }
            Set<String> legacyOwners = owners.remainingOwners(bytes);
            if (!legacyOwners.isEmpty()) {
                drop(className, "unresolved owner " + legacyOwners
                        + " (no safe NeoForge equivalent for the members used / incompatible descriptor)");
                return;
            }
            TreeSet<String> unresolved = new TreeSet<>();
            for (MethodNode m : node.methods) {
                if (isShadow(m.invisibleAnnotations, m.visibleAnnotations) && LegacyNameMappings.looksLikeSrg(m.name)) {
                    unresolved.add(m.name);
                }
                collectUnresolved(m.invisibleAnnotations, unresolved);
                collectUnresolved(m.visibleAnnotations, unresolved);
            }
            for (FieldNode f : node.fields) {
                if (isShadow(f.invisibleAnnotations, f.visibleAnnotations) && LegacyNameMappings.looksLikeSrg(f.name)) {
                    unresolved.add(f.name);
                }
                collectUnresolved(f.invisibleAnnotations, unresolved);
                collectUnresolved(f.visibleAnnotations, unresolved);
            }
            if (!unresolved.isEmpty()) {
                drop(className, "unmapped legacy member(s) " + unresolved);
            }
        } catch (RuntimeException e) {
            LOGGER.debug("{} could not inspect {} for mixin metadata: {}", TAG, className, e.toString());
        }
    }

    private void drop(String className, String reason) {
        dropped.put(className, reason);
    }

    private void collectUnresolved(List<AnnotationNode> annotations, Set<String> out) {
        if (annotations == null) return;
        for (AnnotationNode a : annotations) {
            if (!MixinClassSupport.isMixinAnnotation(a.desc)) continue;
            List<String> strings = new ArrayList<>();
            MixinClassSupport.collectMemberStrings(a, strings);
            for (String s : strings) {
                if (mappings.hasUnmappedTokens(s)) out.add(s);
            }
        }
    }

    private static AnnotationNode find(List<AnnotationNode> list, String desc) {
        if (list == null) return null;
        for (AnnotationNode a : list) if (desc.equals(a.desc)) return a;
        return null;
    }

    private static boolean isShadow(List<AnnotationNode> a, List<AnnotationNode> b) {
        return find(a, MixinClassSupport.SHADOW) != null || find(b, MixinClassSupport.SHADOW) != null;
    }

    /** True for root-level JSON files that may be a mixin config or refmap. */
    public static boolean isCandidate(String entryName) {
        String lower = entryName.toLowerCase(java.util.Locale.ROOT);
        return !entryName.contains("/") && lower.endsWith(".json")
                && (lower.contains("mixin") || lower.contains("refmap"));
    }

    /** Rewrites a mixin config or refmap; returns the original bytes for anything else or on parse errors. */
    public byte[] rewrite(String entryName, byte[] data) {
        try {
            JsonElement root = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) return data;
            JsonObject obj = root.getAsJsonObject();
            boolean changed;
            if (obj.has("package") && obj.get("package").isJsonPrimitive()) {
                changed = rewriteConfig(entryName, obj);
            } else if (obj.has("mappings") && obj.get("mappings").isJsonObject()) {
                changed = rewriteRefmap(entryName, obj);
            } else {
                return data;
            }
            if (!changed) return data;
            rewrittenConfigs.add(entryName);
            return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(obj)
                    .getBytes(StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            LOGGER.warn("{} {}: could not parse mixin resource {}, leaving it unchanged: {}", TAG, jarName, entryName,
                    e.toString());
            return data;
        }
    }

    private boolean rewriteConfig(String entryName, JsonObject config) {
        String pkg = config.get("package").getAsString();
        boolean changed = false;
        int remaining = 0;
        for (String key : List.of("mixins", "client", "server")) {
            JsonElement el = config.get(key);
            if (el == null || !el.isJsonArray()) continue;
            JsonArray kept = new JsonArray();
            for (JsonElement e : el.getAsJsonArray()) {
                if (!e.isJsonPrimitive()) {
                    kept.add(e);
                    continue;
                }
                String full = pkg.isEmpty() ? e.getAsString() : pkg + "." + e.getAsString();
                String reason = dropped.get(full);
                if (reason != null) {
                    LOGGER.warn("{} {}: dropped mixin {} from {} ({})", TAG, jarName, full, entryName, reason);
                    changed = true;
                } else {
                    kept.add(e);
                }
            }
            if (changed) config.add(key, kept);
            remaining += kept.size();
        }
        if (changed && remaining == 0) {
            LOGGER.warn("{} {}: mixin config {} has no mixins left and is effectively disabled", TAG, jarName, entryName);
        }
        return changed;
    }

    private boolean rewriteRefmap(String entryName, JsonObject refmap) {
        boolean changed = false;
        changed |= rewriteRefmapSection(entryName, refmap.getAsJsonObject("mappings"));
        JsonElement data = refmap.get("data");
        if (data != null && data.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : data.getAsJsonObject().entrySet()) {
                if (e.getValue().isJsonObject()) changed |= rewriteRefmapSection(entryName, e.getValue().getAsJsonObject());
            }
        }
        return changed;
    }

    private boolean rewriteRefmapSection(String entryName, JsonObject section) {
        boolean changed = false;
        for (String cls : new ArrayList<>(section.keySet())) {
            if (dropped.containsKey(cls.replace('/', '.'))) {
                section.remove(cls);
                refmapEntriesDropped++;
                changed = true;
                continue;
            }
            JsonElement members = section.get(cls);
            if (!members.isJsonObject()) continue;
            JsonObject in = members.getAsJsonObject();
            JsonObject out = new JsonObject();
            boolean classChanged = false;
            for (Map.Entry<String, JsonElement> m : in.entrySet()) {
                Set<String> remapped = new TreeSet<>();
                String key = remapOwners(mappings.remapTokens(m.getKey(), remapped), remapped);
                JsonElement value = m.getValue();
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                    String v = remapOwners(mappings.remapTokens(value.getAsString(), remapped), remapped);
                    if (!v.equals(value.getAsString())) value = new com.google.gson.JsonPrimitive(v);
                }
                if (!remapped.isEmpty()) {
                    classChanged = true;
                    refmapEntriesRemapped++;
                    LOGGER.info("{} {}: refmap {} {} remapped {}", TAG, jarName, entryName, cls, remapped);
                }
                out.add(key, value);
            }
            if (classChanged) {
                section.add(cls, out);
                changed = true;
            }
        }
        return changed;
    }

    private String remapOwners(String s, Set<String> remapped) {
        java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
        String r = owners.remapDescriptorTokens(s, Set.of(), counts);
        remapped.addAll(counts.keySet());
        return r;
    }

    /** Logs the per-jar summary. */
    public void logSummary() {
        if (rewrittenConfigs.isEmpty() && dropped.isEmpty()) return;
        LOGGER.info("{} {} mixin summary: {} config/refmap file(s) rewritten {}, {} refmap member entr(ies) remapped, "
                + "{} refmap class entr(ies) dropped, {} mixin class(es) dropped", TAG, jarName,
                rewrittenConfigs.size(), rewrittenConfigs, refmapEntriesRemapped, refmapEntriesDropped, dropped.size());
    }

    public Map<String, String> droppedMixins() {
        return Map.copyOf(dropped);
    }

    public int refmapEntriesRemapped() {
        return refmapEntriesRemapped;
    }
}
