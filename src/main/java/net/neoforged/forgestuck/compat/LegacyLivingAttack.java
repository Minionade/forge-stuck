package net.neoforged.forgestuck.compat;

import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** Forge attack amounts are immutable; expose the original amount, not modified incoming damage. */
public final class LegacyLivingAttack {
    private LegacyLivingAttack() {}
    public static DamageContainer container(DamageSource source, float amount) {
        return new DamageContainer(source, amount);
    }
    public static float getAmount(LivingIncomingDamageEvent event) { return event.getOriginalAmount(); }
    public static boolean isCancelable(LivingIncomingDamageEvent event) { return true; }
    public static boolean hasResult(LivingIncomingDamageEvent event) { return false; }
}
