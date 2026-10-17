package net.neoforged.forgestuck.compat;

/**
 * Target of call sites the transformer rewrote because a legacy Forge member has no verified NeoForge equivalent.
 * Linking succeeds so unrelated code paths work; executing the exact unsupported call fails loudly and precisely.
 */
public final class UnsupportedForgeApi {
    private UnsupportedForgeApi() {}

    /** Always throws; declared void so the rewritten call site keeps a verifiable stack shape. */
    public static void fail(String member, String caller) {
        throw new UnsupportedOperationException("Forge API " + member
                + " has no verified NeoForge 1.21.1 equivalent (called from " + caller + "); see Forge Stuck compat docs");
    }
}
