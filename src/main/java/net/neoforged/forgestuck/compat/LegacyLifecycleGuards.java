package net.neoforged.forgestuck.compat;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Prevents duplicate invocation of one-shot legacy setup callbacks. */
public final class LegacyLifecycleGuards {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgestuck");
    private static final Map<Object, Set<String>> INVOCATIONS = new IdentityHashMap<>();

    private LegacyLifecycleGuards() {}

    public static boolean firstInvocation(Object owner, String callback) {
        synchronized (INVOCATIONS) {
            Set<String> callbacks = INVOCATIONS.computeIfAbsent(owner,
                    ignored -> Collections.newSetFromMap(new java.util.HashMap<>()));
            if (callbacks.add(callback)) return true;
        }
        String ownerName = owner instanceof Class<?> ownerClass ? ownerClass.getName() : owner.getClass().getName();
        LOGGER.warn("[Forge Stuck!!] Skipping duplicate setup callback {} on {}", callback, ownerName);
        return false;
    }
}
