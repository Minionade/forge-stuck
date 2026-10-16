package net.neoforged.forgestuck.shim;

/**
 * Minimal shim stub - implements signatures used by ForgeStuckMod but returns harmless defaults.
 */
public final class LegacyItemStackShim {
    private LegacyItemStackShim() {}

    public static Object getTag(Object stack) { return null; }
    public static Object getOrCreateTag(Object stack) { return null; }
    public static boolean hasTag(Object stack) { return false; }
    public static void setTag(Object stack, Object tag) { /* no-op */ }
    public static void syncTag(Object stack, Object tag) { /* no-op */ }
}
