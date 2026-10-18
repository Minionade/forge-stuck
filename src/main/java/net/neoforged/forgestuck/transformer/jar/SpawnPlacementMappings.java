package net.neoforged.forgestuck.transformer.jar;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class SpawnPlacementMappings {
    static final String EVENT = "net/minecraftforge/event/entity/SpawnPlacementRegisterEvent";
    static final String PLACEMENT = "net/minecraft/world/entity/SpawnPlacements$Type";
    static final String FULL_REGISTER = "(Lnet/minecraft/world/entity/EntityType;L" + PLACEMENT
            + ";Lnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/entity/SpawnPlacements$SpawnPredicate;L"
            + EVENT + "$Operation;)V";
    private SpawnPlacementMappings() {}
    static List<ForgeOwnerMappings.Entry> entries() {
        String entity = "Lnet/minecraft/world/entity/EntityType;";
        String predicate = "Lnet/minecraft/world/entity/SpawnPlacements$SpawnPredicate;";
        return List.of(
                new ForgeOwnerMappings.Entry(EVENT, "net/neoforged/neoforge/event/entity/RegisterSpawnPlacementsEvent", Set.of(
                        "register(" + entity + predicate + ")V",
                        "register(" + entity + predicate + "L" + EVENT + "$Operation;)V")),
                new ForgeOwnerMappings.Entry(EVENT + "$Operation",
                        "net/neoforged/neoforge/event/entity/RegisterSpawnPlacementsEvent$Operation",
                        enumMembers(EVENT + "$Operation", "AND", "OR", "REPLACE")),
                new ForgeOwnerMappings.Entry(PLACEMENT, "net/neoforged/forgestuck/compat/LegacySpawnPlacementType",
                        enumMembers(PLACEMENT, "ON_GROUND", "IN_WATER", "NO_RESTRICTIONS", "IN_LAVA")));
    }
    private static Set<String> enumMembers(String owner, String... constants) {
        String desc = "L" + owner + ";";
        Set<String> members = new HashSet<>();
        for (String name : constants) members.add(name + desc);
        members.addAll(Set.of("values()[" + desc, "valueOf(Ljava/lang/String;)" + desc,
                "name()Ljava/lang/String;", "ordinal()I", "toString()Ljava/lang/String;",
                "compareTo(Ljava/lang/Enum;)I", "compareTo(Ljava/lang/Object;)I", "getDeclaringClass()Ljava/lang/Class;"));
        return members;
    }
}
