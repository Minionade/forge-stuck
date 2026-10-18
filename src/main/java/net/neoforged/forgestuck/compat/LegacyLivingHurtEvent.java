package net.neoforged.forgestuck.compat;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

/** Separate event type: attack and hurt handler overloads must not collapse to one descriptor. */
public class LegacyLivingHurtEvent extends LivingEvent implements ICancellableEvent {
    private final DamageSource source;
    private float amount;

    public LegacyLivingHurtEvent(LivingEntity entity, DamageSource source, float amount) {
        super(entity);
        this.source = source;
        this.amount = amount;
    }
    public DamageSource getSource() { return source; }
    public float getAmount() { return amount; }
    public void setAmount(float amount) { this.amount = amount; }
    public boolean isCancelable() { return true; }
    public boolean hasResult() { return false; }
}
