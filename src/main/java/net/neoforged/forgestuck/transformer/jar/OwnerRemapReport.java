package net.neoforged.forgestuck.transformer.jar;

import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;

/** Per-jar accumulation of Forge -> NeoForge owner remaps. */
public final class OwnerRemapReport {
    private final Map<String, Integer> counts = new TreeMap<>();
    private final Map<String, String> targets = new TreeMap<>();
    private final java.util.Set<String> jarBlocked = new java.util.TreeSet<>();
    private final java.util.Set<String> memberUses = new java.util.HashSet<>();
    private int classesRewritten;
    private int descriptorsAdapted;

    /** Owners left unmapped in at least one class (constructor/method-handle/Mixin uses that cannot be rewritten). */
    java.util.Set<String> jarBlocked() {
        return jarBlocked;
    }

    /** {@code owner + '\0' + descriptor} of every mapped-owner member use seen by the scan pass. */
    java.util.Set<String> memberUses() {
        return memberUses;
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

    private final Map<String, Integer> unsupportedCalls = new TreeMap<>();

    /** Records a call site rewritten to fail at execution time because the member has no verified equivalent. */
    void unsupportedCall(String member) {
        unsupportedCalls.merge(member, 1, Integer::sum);
    }

    /** Member (owner.name+descriptor) -> number of call sites rewritten to throw when executed. */
    public Map<String, Integer> unsupportedCalls() {
        return Map.copyOf(unsupportedCalls);
    }

    void log(Logger logger, String tag, String jarName) {
        counts.forEach((old, n) -> logger.info("{} {} owner remap {} -> {} ({} reference(s))", tag, jarName, old,
                targets.get(old), n));
        unsupportedCalls.forEach((member, n) -> logger.warn(
                "{} {} {} call site(s) of {} have no verified NeoForge equivalent; they link but throw "
                        + "UnsupportedOperationException if executed", tag, jarName, n, member));
        jarBlocked.forEach(owner -> logger.error(
                "{} {} owner {} left UNMAPPED in some class (constructor, method handle or Mixin use); that class "
                        + "will not link", tag, jarName, owner));
    }
}
