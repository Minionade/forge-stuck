package net.neoforged.forgestuck.compat;

import java.util.stream.Stream;
import net.minecraft.tags.TagKey;

public interface LegacyReverseTag<T> {
    boolean containsTag(TagKey<T> tag);

    Stream<TagKey<T>> getTagKeys();
}
