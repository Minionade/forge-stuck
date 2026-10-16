package net.neoforged.forgestuck.transformer.jar;

import net.neoforged.forgestuck.transformer.ForgeClassTransformer;

/** Adapts the existing ASM {@link ForgeClassTransformer} to the pluggable step list. */
public class ForgeClassTransformStep implements ClassTransformStep {
    @Override
    public byte[] apply(String className, byte[] bytes) {
        return ForgeClassTransformer.transform(className, bytes);
    }
}
