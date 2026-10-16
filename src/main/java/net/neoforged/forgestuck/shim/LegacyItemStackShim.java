package net.neoforged.forgestuck.shim;

/**
 * Conservative shim stub for legacy ItemStack tag access.
 *
 * Use Object signatures and no-op/reflectionless implementations to avoid compile-time
 * dependencies on varying Minecraft mappings across NeoForge versions. We'll expand
 * this with safe reflection-based access or mapping-specific adapters once we know
 * which methods are present at runtime.
 */
public final class LegacyItemStackShim {
    private LegacyItemStackShim() {}

    public static Object getTag(Object stack) { return null; }
    public static Object getOrCreateTag(Object stack) { return null; }
    public static boolean hasTag(Object stack) { return false; }
    public static void setTag(Object stack, Object tag) { /* no-op */ }
    public static void syncTag(Object stack, Object tag) { /* no-op */ }
}
