package net.neoforged.forgestuck.transformer.jar;

/**
 * A pluggable bytecode rewrite step applied by {@link JarTransformer} to every {@code .class} entry.
 */
@FunctionalInterface
public interface ClassTransformStep {
    /**
     * @param className dotted class name, e.g. {@code com.example.Foo}
     * @param bytes     current class bytes
     * @return the rewritten bytes, or the very same array instance when nothing changed
     */
    byte[] apply(String className, byte[] bytes);
}
