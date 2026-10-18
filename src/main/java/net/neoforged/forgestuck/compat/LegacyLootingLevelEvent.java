package net.neoforged.forgestuck.compat;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;

/**
 * Stand-in for Forge's {@code LootingLevelEvent}. 1.21.1 computes looting through data-driven enchantments and never
 * fires this event, so legacy listeners link and register but are not invoked.
 */
public class LegacyLootingLevelEvent extends Event {
    private final LivingEntity entity;
    private final DamageSource damageSource;
    private int lootingLevel;

    public LegacyLootingLevelEvent(LivingEntity entity, DamageSource damageSource, int lootingLevel) {
        this.entity = entity;
        this.damageSource = damageSource;
        this.lootingLevel = lootingLevel;
    }

    public LivingEntity getEntity() {
        return entity;
    }

    public DamageSource getDamageSource() {
        return damageSource;
    }

    public int getLootingLevel() {
        return lootingLevel;
    }

    public void setLootingLevel(int lootingLevel) {
        this.lootingLevel = lootingLevel;
    }
}
