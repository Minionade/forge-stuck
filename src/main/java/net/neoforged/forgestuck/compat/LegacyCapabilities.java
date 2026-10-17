package net.neoforged.forgestuck.compat;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Target of redirected Forge 1.20.1 {@code RegisterCapabilitiesEvent.register(Class)} calls. Forge's interface
 * based capability tokens have no counterpart in NeoForge's {@code BlockCapability}/{@code ItemCapability}/
 * {@code EntityCapability} model, so this only records which legacy capability types a mod declared (so the
 * mod's registration listener links and completes). It does not make {@code getCapability(Capability, ...)}
 * calls resolve; those still fail per call and the recorded set is the input for a future adapter.
 */
public final class LegacyCapabilities {
    private static final Set<Class<?>> DECLARED = Collections.synchronizedSet(new LinkedHashSet<>());

    private LegacyCapabilities() {}

    public static void register(RegisterCapabilitiesEvent event, Class<?> type) {
        if (type == null) throw new NullPointerException("capability type");
        DECLARED.add(type);
    }

    public static Set<Class<?>> declared() {
        synchronized (DECLARED) {
            return Set.copyOf(DECLARED);
        }
    }
}