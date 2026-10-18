package net.neoforged.forgestuck.compat;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

/** Mutable compatibility event for Forge's post-mitigation, pre-health-change damage hook. */
public class LegacyLivingDamageEvent extends LivingEvent {
    private final DamageSource source;
    private float amount;

    public LegacyLivingDamageEvent(LivingEntity entity, DamageSource source, float amount) {
        super(entity);
        this.source = source;
        this.amount = amount;
    }

    public DamageSource getSource() {
        return source;
    }

    public float getAmount() {
        return amount;
    }

    public void setAmount(float amount) {
        this.amount = amount;
    }
}
