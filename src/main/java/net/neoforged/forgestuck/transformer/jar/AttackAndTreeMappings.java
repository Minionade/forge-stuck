package net.neoforged.forgestuck.transformer.jar;

import java.util.List;
import java.util.Set;

final class AttackAndTreeMappings {
    private AttackAndTreeMappings() {}
    static List<ForgeOwnerMappings.Entry> entries() {
        String select = "(Lnet/minecraft/util/RandomSource;Z)Lnet/minecraft/resources/ResourceKey;";
        String grow = "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/util/RandomSource;)Z";
        return List.of(
                new ForgeOwnerMappings.Entry("net/minecraftforge/event/entity/living/LivingAttackEvent",
                        "net/neoforged/neoforge/event/entity/living/LivingIncomingDamageEvent", Set.of(
                        "getEntity()Lnet/minecraft/world/entity/LivingEntity;",
                        "getEntity()Lnet/minecraft/world/entity/Entity;",
                        "getSource()Lnet/minecraft/world/damagesource/DamageSource;",
                        "isCanceled()Z", "setCanceled(Z)V",
                        "<init>(Lnet/minecraft/world/entity/LivingEntity;Lnet/neoforged/neoforge/common/damagesource/DamageContainer;)V")),
                new ForgeOwnerMappings.Entry("net/minecraft/world/level/block/grower/AbstractTreeGrower",
                        "net/neoforged/forgestuck/compat/LegacyAbstractTreeGrower", Set.of(
                        "<init>()V", "getConfiguredFeature" + select, "growTree" + grow)),
                new ForgeOwnerMappings.Entry("net/minecraft/world/level/block/grower/AbstractMegaTreeGrower",
                        "net/neoforged/forgestuck/compat/LegacyAbstractMegaTreeGrower", Set.of(
                        "<init>()V", "getConfiguredFeature" + select, "growTree" + grow,
                        "getConfiguredMegaFeature(Lnet/minecraft/util/RandomSource;)Lnet/minecraft/resources/ResourceKey;",
                        "placeMega(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;"
                                + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;"
                                + "Lnet/minecraft/util/RandomSource;II)Z",
                        "isTwoByTwoSapling(Lnet/minecraft/world/level/block/state/BlockState;"
                                + "Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;II)Z")));
    }
}
