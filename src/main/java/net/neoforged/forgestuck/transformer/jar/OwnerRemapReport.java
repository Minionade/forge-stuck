package net.neoforged.forgestuck.transformer.jar;

import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;

/** Per-jar accumulation of Forge -> NeoForge owner remaps. */
public final class OwnerRemapReport {
    private final Map<String, Integer> counts = new TreeMap<>();
    private final Map<String, String> targets = new TreeMap<>();
    private final java.util.Set<String> jarBlocked = new java.util.TreeSet<>();
    private int classesRewritten;
    private int descriptorsAdapted;

    /** Owners that at least one class of this jar uses in an unsupported way; they stay unmapped jar-wide. */
    java.util.Set<String> jarBlocked() {
        return jarBlocked;
    }

    public java.util.Set<String> blockedOwners() {
        return java.util.Set.copyOf(jarBlocked);
    }

    void record(Map<String, Integer> classCounts, ForgeOwnerMappings mappings, int adapted) {
        classesRewritten++;
        descriptorsAdapted += adapted;
        classCounts.forEach((old, n) -> {
            counts.merge(old, n, Integer::sum);
            targets.put(old, mappings.get(old).newOwner());
        });
    }

    public int total() {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int descriptorsAdapted() {
        return descriptorsAdapted;
    }

    public int classesRewritten() {
        return classesRewritten;
    }

    public Map<String, Integer> counts() {
        return Map.copyOf(counts);
    }

    void log(Logger logger, String tag, String jarName) {
        counts.forEach((old, n) -> logger.info("{} {} owner remap {} -> {} ({} reference(s))", tag, jarName, old,
                targets.get(old), n));
    }
}
