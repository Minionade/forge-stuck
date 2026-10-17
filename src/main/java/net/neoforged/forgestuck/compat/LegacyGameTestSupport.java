package net.neoforged.forgestuck.compat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Used only by guarded gametest subscribers in transformed legacy jars. */
public final class LegacyGameTestSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgestuck");

    private LegacyGameTestSupport() {}

    public static boolean shouldSkip(boolean production, Class<?> owner, String testClass) {
        if (production) return false;
        try {
            Class.forName(testClass, false, owner.getClassLoader());
            return false;
        } catch (ClassNotFoundException e) {
            LOGGER.warn("[Forge Stuck!!] skipping transformed legacy gametest hook in {}: development class {} is absent",
                    owner.getName(), testClass);
            return true;
        }
    }
}
