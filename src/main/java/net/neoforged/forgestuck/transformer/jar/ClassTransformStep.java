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

    /** Variant that also records per-jar statistics; steps without statistics just delegate. */
    default byte[] apply(String className, byte[] bytes, OwnerRemapReport report) {
        return apply(className, bytes);
    }

    /**
     * Optional pre-pass over the original bytes of every class of a jar, run before any {@link #apply}. Lets a step
     * collect jar-wide facts so all classes of one jar are rewritten consistently.
     */
    default void scan(String className, byte[] bytes, OwnerRemapReport report) {}
}
