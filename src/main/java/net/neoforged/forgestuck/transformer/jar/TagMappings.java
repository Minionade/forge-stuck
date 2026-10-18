package net.neoforged.forgestuck.transformer.jar;

import java.util.List;
import java.util.Set;

final class TagMappings {
    private static final String TAGS = "net/minecraftforge/registries/tags/";
    private static final String COMPAT = "net/neoforged/forgestuck/compat/";

    private TagMappings() {}

    static List<ForgeOwnerMappings.Entry> entries() {
        return List.of(
                new ForgeOwnerMappings.Entry(TAGS + "ITagManager", COMPAT + "LegacyTagManager", Set.of(
                        "getTag(Lnet/minecraft/tags/TagKey;)L" + TAGS + "ITag;",
                        "getReverseTag(Ljava/lang/Object;)Ljava/util/Optional;")),
                new ForgeOwnerMappings.Entry(TAGS + "ITag", COMPAT + "LegacyTag",
                        Set.of("stream()Ljava/util/stream/Stream;")),
                new ForgeOwnerMappings.Entry(TAGS + "IReverseTag", COMPAT + "LegacyReverseTag", Set.of(
                        "containsTag(Lnet/minecraft/tags/TagKey;)Z",
                        "getTagKeys()Ljava/util/stream/Stream;")));
    }
}
