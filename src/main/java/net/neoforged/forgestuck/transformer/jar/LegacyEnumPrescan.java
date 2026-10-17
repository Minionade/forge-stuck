package net.neoforged.forgestuck.transformer.jar;

import java.nio.charset.StandardCharsets;

/** Cheap constant-pool pre-filter so only classes that can mention the enum are parsed. */
final class LegacyEnumPrescan {
    private static final byte[] NEEDLE =
            LegacyEnumExtensionPlanner.ITEM_DISPLAY_CONTEXT.getBytes(StandardCharsets.UTF_8);

    private LegacyEnumPrescan() {}

    static boolean mentionsItemDisplayContext(byte[] classBytes) {
        outer:
        for (int i = 0; i <= classBytes.length - NEEDLE.length; i++) {
            for (int j = 0; j < NEEDLE.length; j++) {
                if (classBytes[i + j] != NEEDLE[j]) continue outer;
            }
            return true;
        }
        return false;
    }
}
