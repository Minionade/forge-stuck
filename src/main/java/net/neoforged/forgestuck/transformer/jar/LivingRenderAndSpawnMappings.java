package net.neoforged.forgestuck.transformer.jar;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class LivingRenderAndSpawnMappings {
    private LivingRenderAndSpawnMappings() {}
    static List<ForgeOwnerMappings.Entry> entries() {
        var entries = new ArrayList<ForgeOwnerMappings.Entry>();
        Set<String> render = Set.of(
                "getEntity()Lnet/minecraft/world/entity/LivingEntity;",
                "getRenderer()Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;",
                "getPartialTick()F", "getPoseStack()Lcom/mojang/blaze3d/vertex/PoseStack;",
                "getMultiBufferSource()Lnet/minecraft/client/renderer/MultiBufferSource;", "getPackedLight()I",
                "<init>(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V");
        for (String suffix : List.of("", "$Pre", "$Post")) {
            Set<String> members = new HashSet<>(render);
            if (suffix.equals("$Pre")) members.addAll(Set.of("isCanceled()Z", "setCanceled(Z)V"));
            entries.add(new ForgeOwnerMappings.Entry("net/minecraftforge/client/event/RenderLivingEvent" + suffix,
                    "net/neoforged/neoforge/client/event/RenderLivingEvent" + suffix, members));
        }
        entries.add(new ForgeOwnerMappings.Entry("net/minecraftforge/event/entity/living/MobSpawnEvent$FinalizeSpawn",
                "net/neoforged/neoforge/event/entity/living/FinalizeSpawnEvent", Set.of(
                "getEntity()Lnet/minecraft/world/entity/Mob;", "getEntity()Lnet/minecraft/world/entity/Entity;",
                "getLevel()Lnet/minecraft/world/level/ServerLevelAccessor;", "getX()D", "getY()D", "getZ()D",
                "getDifficulty()Lnet/minecraft/world/DifficultyInstance;", "setDifficulty(Lnet/minecraft/world/DifficultyInstance;)V",
                "getSpawnType()Lnet/minecraft/world/entity/MobSpawnType;",
                "getSpawnData()Lnet/minecraft/world/entity/SpawnGroupData;", "setSpawnData(Lnet/minecraft/world/entity/SpawnGroupData;)V",
                "isCanceled()Z", "setCanceled(Z)V", "isSpawnCancelled()Z", "setSpawnCancelled(Z)V")));
        return entries;
    }
}
