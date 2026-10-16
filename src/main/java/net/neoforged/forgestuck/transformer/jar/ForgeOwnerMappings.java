package net.neoforged.forgestuck.transformer.jar;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/**
 * Table of legacy Forge class internal names -> NeoForge class internal names. Only exact internal names are ever
 * rewritten. An entry may list the members ({@code name + descriptor}) that are known to exist with the same
 * signature on the target class; references to any other member cannot be adapted safely and leave the owner
 * unmapped in that class (see {@link ForgeOwnerRemapStep}).
 */
public final class ForgeOwnerMappings {
    /** @param supportedMembers {@code name+desc} entries valid on the target, or {@code null} when every member is valid. */
    public record Entry(String oldOwner, String newOwner, Set<String> supportedMembers) {
        boolean supports(String name, String desc) {
            return supportedMembers == null || supportedMembers.contains(name + desc);
        }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public ForgeOwnerMappings() {}

    public ForgeOwnerMappings(Iterable<Entry> initial) {
        for (Entry e : initial) add(e);
    }

    public ForgeOwnerMappings add(Entry entry) {
        entries.put(entry.oldOwner(), entry);
        return this;
    }

    public ForgeOwnerMappings add(String oldOwner, String newOwner) {
        return add(new Entry(oldOwner, newOwner, null));
    }

    public Entry get(String owner) {
        return entries.get(owner);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public static ForgeOwnerMappings defaults() {
        return new ForgeOwnerMappings().add(new Entry("net/minecraftforge/registries/RegistryObject",
                "net/neoforged/neoforge/registries/DeferredHolder", Set.of(
                        "get()Ljava/lang/Object;",
                        "getId()Lnet/minecraft/resources/ResourceLocation;",
                        "getKey()Lnet/minecraft/resources/ResourceKey;")));
    }

    /** Cheap check on raw class bytes: could this class mention any mapped owner? */
    boolean mayReference(byte[] bytes) {
        String raw = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        for (String owner : entries.keySet()) if (raw.contains(owner)) return true;
        return false;
    }

    /** Replaces exact {@code L<old>;} tokens (descriptors in Mixin target strings); counts hits per owner. */
    String remapDescriptorTokens(String s, Set<String> blocked, Map<String, Integer> counts) {
        String result = s;
        for (Entry e : entries.values()) {
            if (blocked.contains(e.oldOwner())) continue;
            String from = "L" + e.oldOwner() + ";";
            if (!result.contains(from)) continue;
            int hits = result.split(java.util.regex.Pattern.quote(from), -1).length - 1;
            result = result.replace(from, "L" + e.newOwner() + ";");
            counts.merge(e.oldOwner(), hits, Integer::sum);
        }
        return result;
    }

    /** Owners from this table that the class still references (used to detect incomplete remaps). */
    public java.util.Set<String> remainingOwners(byte[] bytes) {
        java.util.Set<String> found = new java.util.TreeSet<>();
        if (!mayReference(bytes)) return found;
        Remapper recorder = new Remapper() {
            @Override
            public String map(String internalName) {
                if (entries.containsKey(internalName)) found.add(internalName);
                return internalName;
            }
        };
        new ClassReader(bytes).accept(new ClassRemapper(new ClassVisitor(Opcodes.ASM9) {}, recorder), 0);
        return found;
    }

    /** Remapper rewriting mapped owners (except {@code blocked}) and counting rewrites per old owner. */
    Remapper remapper(Set<String> blocked, TreeMap<String, Integer> counts) {
        return new Remapper() {
            @Override
            public String map(String internalName) {
                Entry e = entries.get(internalName);
                if (e == null || blocked.contains(internalName)) return internalName;
                counts.merge(internalName, 1, Integer::sum);
                return e.newOwner();
            }
        };
    }
}
