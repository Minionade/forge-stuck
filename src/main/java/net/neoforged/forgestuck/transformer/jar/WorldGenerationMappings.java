package net.neoforged.forgestuck.transformer.jar;

import java.util.List;
import java.util.Set;

final class WorldGenerationMappings {
    private WorldGenerationMappings() {}

    static List<ForgeOwnerMappings.Entry> entries() {
        String holderSet = "Lnet/minecraft/core/HolderSet;";
        return List.of(
                new ForgeOwnerMappings.Entry(
                        "net/minecraftforge/common/world/ForgeBiomeModifiers$AddFeaturesBiomeModifier",
                        "net/neoforged/neoforge/common/world/BiomeModifiers$AddFeaturesBiomeModifier",
                        Set.of("<init>(" + holderSet + holderSet
                                + "Lnet/minecraft/world/level/levelgen/GenerationStep$Decoration;)V")),
                new ForgeOwnerMappings.Entry(
                        "net/minecraftforge/common/world/ForgeBiomeModifiers$AddSpawnsBiomeModifier",
                        "net/neoforged/neoforge/common/world/BiomeModifiers$AddSpawnsBiomeModifier",
                        Set.of("<init>(" + holderSet + "Ljava/util/List;)V")),
                new ForgeOwnerMappings.Entry(
                        "net/minecraftforge/entity/PartEntity",
                        "net/neoforged/neoforge/entity/PartEntity",
                        Set.of("getParent()Lnet/minecraft/world/entity/Entity;")),
                new ForgeOwnerMappings.Entry(
                        "net/minecraftforge/common/util/BlockSnapshot",
                        "net/neoforged/neoforge/common/util/BlockSnapshot",
                        Set.of("create(Lnet/minecraft/resources/ResourceKey;"
                                + "Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;)"
                                + "Lnet/minecraftforge/common/util/BlockSnapshot;")));
    }
}
