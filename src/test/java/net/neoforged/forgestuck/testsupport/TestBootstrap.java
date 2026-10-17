package net.neoforged.forgestuck.testsupport;

import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Bootstraps vanilla registries inside a unit test where FML did not run mod discovery. */
public final class TestBootstrap {
    private static boolean done;

    private TestBootstrap() {}

    public static synchronized void init() {
        if (done) return;
        try {
            // Resolve LoadingModList through the loader Minecraft/NeoForge classes use; the test layer may see another copy.
            Class<?> lml = Class.forName("net.neoforged.fml.loading.LoadingModList", true,
                    net.neoforged.neoforge.common.util.flag.FeatureFlagLoader.class.getClassLoader());
            if (lml.getMethod("get").invoke(null) == null) {
                for (java.lang.reflect.Method m : lml.getMethods()) {
                    if (m.getName().equals("of") && m.getParameterCount() == 5) {
                        m.invoke(null, List.of(), List.of(), List.of(), List.of(), Map.of());
                    }
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        done = true;
    }
}
