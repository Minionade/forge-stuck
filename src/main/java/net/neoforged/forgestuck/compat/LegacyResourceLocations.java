package net.neoforged.forgestuck.compat;

import net.minecraft.resources.ResourceLocation;

/** Helpers for rewritten {@code super(...)} calls of classes extending the (1.20) non-final ResourceLocation. */
public final class LegacyResourceLocations {
    private LegacyResourceLocations() {}

    /** Namespace of a legacy {@code ResourceLocation(String)} argument; throws like the 1.20 constructor on bad input. */
    public static String namespace(String id) {
        return ResourceLocation.parse(id).getNamespace();
    }

    public static String path(String id) {
        return ResourceLocation.parse(id).getPath();
    }

    public static String[] split(String id, char separator) {
        int index = id.indexOf(separator);
        if (index <= 0) return new String[] {ResourceLocation.DEFAULT_NAMESPACE, id};
        return new String[] {id.substring(0, index), id.substring(index + 1)};
    }

    /** Legacy {@code ResourceLocation(String,String)} validated both parts; the 1.21 private constructor does not. */
    public static void validate(String namespace, String path) {
        ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
