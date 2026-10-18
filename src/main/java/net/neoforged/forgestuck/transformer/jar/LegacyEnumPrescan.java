package net.neoforged.forgestuck.transformer.jar;

import java.nio.charset.StandardCharsets;

/** Cheap constant-pool pre-filter so only classes that can mention a supported extensible enum are parsed. */
final class LegacyEnumPrescan {
    private static final byte[] NEEDLE =
            LegacyEnumExtensionPlanner.ITEM_DISPLAY_CONTEXT.getBytes(StandardCharsets.UTF_8);
    private static final byte[] PLACEMENT_NEEDLE =
            LegacyEnumExtensionPlanner.SPAWN_PLACEMENT_TYPE.getBytes(StandardCharsets.UTF_8);

    private LegacyEnumPrescan() {}

    static boolean mentionsLegacyExtensibleEnum(byte[] classBytes) {
        return contains(classBytes, NEEDLE) || contains(classBytes, PLACEMENT_NEEDLE);
    }

    static boolean mentionsItemDisplayContext(byte[] classBytes) {
        return contains(classBytes, NEEDLE);
    }

    private static boolean contains(byte[] classBytes, byte[] needle) {
        outer:
        for (int i = 0; i <= classBytes.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (classBytes[i + j] != needle[j]) continue outer;
            }
            return true;
        }
        return false;
    }
}
