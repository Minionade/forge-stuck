package net.neoforged.forgestuck.transformer.jar;

import java.util.List;
import java.util.Set;

final class KeyConflictMappings {
    private KeyConflictMappings() {}

    static List<ForgeOwnerMappings.Entry> entries() {
        String context = "net/minecraftforge/client/settings/IKeyConflictContext";
        String keyConflictContext = "net/minecraftforge/client/settings/KeyConflictContext";
        String contextDesc = "L" + context + ";";
        String keyConflictContextDesc = "L" + keyConflictContext + ";";
        return List.of(
                new ForgeOwnerMappings.Entry(context, "net/neoforged/neoforge/client/settings/IKeyConflictContext",
                        Set.of("isActive()Z", "conflicts(" + contextDesc + ")Z")),
                new ForgeOwnerMappings.Entry(keyConflictContext,
                        "net/neoforged/neoforge/client/settings/KeyConflictContext",
                        Set.of("UNIVERSAL" + keyConflictContextDesc, "GUI" + keyConflictContextDesc,
                                "IN_GAME" + keyConflictContextDesc, "values()[" + keyConflictContextDesc,
                                "valueOf(Ljava/lang/String;)" + keyConflictContextDesc,
                                "isActive()Z", "conflicts(" + contextDesc + ")Z",
                                "name()Ljava/lang/String;", "ordinal()I", "toString()Ljava/lang/String;")));
    }
}
