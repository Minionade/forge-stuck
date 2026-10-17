package net.neoforged.forgestuck.transformer.jar;

/** Points transformed classes at the game-layer copy of the compat package; see {@link CompatRelocation}. */
public final class CompatRelocationStep implements ClassTransformStep {
    @Override
    public byte[] apply(String className, byte[] bytes) {
        return CompatRelocation.relocate(bytes);
    }
}
