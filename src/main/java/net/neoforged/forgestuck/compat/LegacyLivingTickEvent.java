package net.neoforged.forgestuck.compat;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

/** Forge's per-living-entity tick event, forwarded from NeoForge's entity post-tick event. */
public final class LegacyLivingTickEvent extends LivingEvent {
    public LegacyLivingTickEvent(LivingEntity entity) {
        super(entity);
    }
}
