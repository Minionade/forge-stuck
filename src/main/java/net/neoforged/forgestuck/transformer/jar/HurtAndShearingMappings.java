package net.neoforged.forgestuck.transformer.jar;

import java.util.List;
import java.util.Set;

final class HurtAndShearingMappings {
    private HurtAndShearingMappings() {}
    static List<ForgeOwnerMappings.Entry> entries() {
        return List.of(
                new ForgeOwnerMappings.Entry("net/minecraftforge/event/entity/living/LivingDamageEvent",
                        "net/neoforged/forgestuck/compat/LegacyLivingDamageEvent", Set.of(
                        "getEntity()Lnet/minecraft/world/entity/LivingEntity;",
                        "getEntity()Lnet/minecraft/world/entity/Entity;",
                        "getSource()Lnet/minecraft/world/damagesource/DamageSource;",
                        "getAmount()F", "setAmount(F)V")),
                new ForgeOwnerMappings.Entry("net/minecraftforge/event/entity/living/LivingHurtEvent",
                        "net/neoforged/forgestuck/compat/LegacyLivingHurtEvent", Set.of(
                        "<init>(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;F)V",
                        "getEntity()Lnet/minecraft/world/entity/LivingEntity;",
                        "getEntity()Lnet/minecraft/world/entity/Entity;",
                        "getSource()Lnet/minecraft/world/damagesource/DamageSource;",
                        "getAmount()F", "setAmount(F)V", "isCanceled()Z", "setCanceled(Z)V",
                        "isCancelable()Z", "hasResult()Z")),
                // Declarations/casts must also accept native shearables. Implementation interfaces
                // and calls with the old signatures are adapted separately before owner remapping.
                new ForgeOwnerMappings.Entry("net/minecraftforge/common/IForgeShearable",
                        "net/neoforged/neoforge/common/IShearable", Set.of()));
    }
}
