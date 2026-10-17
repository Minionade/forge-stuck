package net.neoforged.forgestuck.transformer.jar;

/** Observer for {@link JarTransformer#transformCached}; all callbacks default to no-ops. */
public interface TransformProgress {
    TransformProgress NONE = new TransformProgress() {};

    /** The source jar's transformed output already exists in the cache. */
    default void cached(String jar) {}

    /** The source jar is about to be transformed (cache miss). */
    default void transforming(String jar) {}

    /** A nested legacy jar inside {@code outer} is being transformed. */
    default void nested(String outer, String nestedEntry) {}

    /** Transformation finished and the output was stored. */
    default void transformed(String jar, long millis) {}
}
