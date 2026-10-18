package net.neoforged.forgestuck.transformer.jar;

import java.util.List;
import java.util.Set;

final class CommonHookMappings {
    private CommonHookMappings() {}

    static List<ForgeOwnerMappings.Entry> entries() {
        return List.of(
                new ForgeOwnerMappings.Entry(
                        "net/minecraftforge/common/ForgeHooks",
                        "net/neoforged/neoforge/common/CommonHooks",
                        Set.of(
                                "getProjectile(Lnet/minecraft/world/entity/LivingEntity;"
                                        + "Lnet/minecraft/world/item/ItemStack;"
                                        + "Lnet/minecraft/world/item/ItemStack;)"
                                        + "Lnet/minecraft/world/item/ItemStack;",
                                "onLivingFall(Lnet/minecraft/world/entity/LivingEntity;FF)[F",
                                "onInteractEntityAt(Lnet/minecraft/world/entity/player/Player;"
                                        + "Lnet/minecraft/world/entity/Entity;"
                                        + "Lnet/minecraft/world/phys/Vec3;"
                                        + "Lnet/minecraft/world/InteractionHand;)"
                                        + "Lnet/minecraft/world/InteractionResult;",
                                "onRightClickBlock(Lnet/minecraft/world/entity/player/Player;"
                                        + "Lnet/minecraft/world/InteractionHand;Lnet/minecraft/core/BlockPos;"
                                        + "Lnet/minecraft/world/phys/BlockHitResult;)"
                                        + "Lnet/minecraftforge/event/entity/player/PlayerInteractEvent$RightClickBlock;",
                                "setCraftingPlayer(Lnet/minecraft/world/entity/player/Player;)V")),
                new ForgeOwnerMappings.Entry(
                        "net/minecraftforge/event/ForgeEventFactory",
                        "net/neoforged/neoforge/event/EventHooks",
                        Set.of(
                                "firePlayerCraftingEvent(Lnet/minecraft/world/entity/player/Player;"
                                        + "Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/Container;)V",
                                "onArrowLoose(Lnet/minecraft/world/item/ItemStack;"
                                        + "Lnet/minecraft/world/level/Level;"
                                        + "Lnet/minecraft/world/entity/player/Player;IZ)I",
                                "onArrowNock(Lnet/minecraft/world/item/ItemStack;"
                                        + "Lnet/minecraft/world/level/Level;"
                                        + "Lnet/minecraft/world/entity/player/Player;"
                                        + "Lnet/minecraft/world/InteractionHand;Z)"
                                        + "Lnet/minecraft/world/InteractionResultHolder;",
                                "onBlockPlace(Lnet/minecraft/world/entity/Entity;"
                                        + "Lnet/minecraftforge/common/util/BlockSnapshot;"
                                        + "Lnet/minecraft/core/Direction;)Z",
                                "onExplosionDetonate(Lnet/minecraft/world/level/Level;"
                                        + "Lnet/minecraft/world/level/Explosion;Ljava/util/List;D)V",
                                "onExplosionStart(Lnet/minecraft/world/level/Level;"
                                        + "Lnet/minecraft/world/level/Explosion;)Z",
                                "onPlayerDestroyItem(Lnet/minecraft/world/entity/player/Player;"
                                        + "Lnet/minecraft/world/item/ItemStack;"
                                        + "Lnet/minecraft/world/InteractionHand;)V",
                                "onProjectileImpact(Lnet/minecraft/world/entity/projectile/Projectile;"
                                        + "Lnet/minecraft/world/phys/HitResult;)Z")),
                new ForgeOwnerMappings.Entry(
                        "net/minecraftforge/event/entity/living/MobEffectEvent$Applicable",
                        "net/neoforged/neoforge/event/entity/living/MobEffectEvent$Applicable",
                        Set.of(
                                "getEffectInstance()Lnet/minecraft/world/effect/MobEffectInstance;",
                                "getEntity()Lnet/minecraft/world/entity/LivingEntity;")),
                new ForgeOwnerMappings.Entry(
                        "net/minecraftforge/network/NetworkHooks",
                        "net/neoforged/forgestuck/compat/LegacyNetworkHooks",
                        Set.of(
                                "openScreen(Lnet/minecraft/server/level/ServerPlayer;"
                                        + "Lnet/minecraft/world/MenuProvider;)V",
                                "openScreen(Lnet/minecraft/server/level/ServerPlayer;"
                                        + "Lnet/minecraft/world/MenuProvider;"
                                        + "Ljava/util/function/Consumer;)V",
                                "openScreen(Lnet/minecraft/server/level/ServerPlayer;"
                                        + "Lnet/minecraft/world/MenuProvider;"
                                        + "Lnet/minecraft/core/BlockPos;)V")),
                new ForgeOwnerMappings.Entry(
                        "net/minecraftforge/server/ServerLifecycleHooks",
                        "net/neoforged/neoforge/server/ServerLifecycleHooks",
                        Set.of("getCurrentServer()Lnet/minecraft/server/MinecraftServer;")),
                new ForgeOwnerMappings.Entry(
                        "net/minecraftforge/event/entity/player/CriticalHitEvent",
                        "net/neoforged/neoforge/event/entity/player/CriticalHitEvent",
                        Set.of(
                                "getEntity()Lnet/minecraft/world/entity/player/Player;",
                                "getTarget()Lnet/minecraft/world/entity/Entity;",
                                "isVanillaCritical()Z")));
    }
}
