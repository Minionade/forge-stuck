package net.neoforged.forgestuck.transformer.jar;

import java.util.List;
import java.util.Set;

/** Exact 1.20.1 growth event and spawn-message mappings. */
final class GrowthAndSpawnMappings {
    private GrowthAndSpawnMappings() {}
    static List<ForgeOwnerMappings.Entry> entries() {
        String result = "Lnet/minecraftforge/eventbus/api/Event$Result;";
        return List.of(
                new ForgeOwnerMappings.Entry("net/minecraftforge/event/level/SaplingGrowTreeEvent",
                        "net/neoforged/neoforge/event/level/BlockGrowFeatureEvent", Set.of(
                        "<init>(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Holder;)V",
                        "getLevel()Lnet/minecraft/world/level/LevelAccessor;",
                        "getPos()Lnet/minecraft/core/BlockPos;",
                        "getFeature()Lnet/minecraft/core/Holder;",
                        "setFeature(Lnet/minecraft/core/Holder;)V",
                        "setFeature(Lnet/minecraft/resources/ResourceKey;)V")),
                new ForgeOwnerMappings.Entry("net/minecraftforge/eventbus/api/Event$Result",
                        "net/neoforged/forgestuck/compat/LegacyEventResult", Set.of(
                        "DENY" + result, "DEFAULT" + result, "ALLOW" + result,
                        "values()[" + result, "valueOf(Ljava/lang/String;)" + result)),
                new ForgeOwnerMappings.Entry("net/minecraftforge/network/PlayMessages$SpawnEntity",
                        "net/neoforged/forgestuck/compat/LegacySpawnEntity", Set.of(
                        "decode(Lnet/minecraft/network/FriendlyByteBuf;)Lnet/minecraftforge/network/PlayMessages$SpawnEntity;",
                        "getEntity()Lnet/minecraft/world/entity/Entity;",
                        "getTypeId()I", "getEntityId()I", "getUuid()Ljava/util/UUID;",
                        "getPosX()D", "getPosY()D", "getPosZ()D",
                        "getPitch()B", "getYaw()B", "getHeadYaw()B",
                        "getVelX()I", "getVelY()I", "getVelZ()I",
                        "getAdditionalData()Lnet/minecraft/network/FriendlyByteBuf;")));
    }
}
