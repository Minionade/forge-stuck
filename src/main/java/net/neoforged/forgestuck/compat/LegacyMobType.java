package net.neoforged.forgestuck.compat;

import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.LivingEntity;

/** Replaces the removed 1.20.1 MobType enum with the corresponding 1.21.1 entity tags. */
public enum LegacyMobType {
    UNDEFINED,
    UNDEAD,
    ARTHROPOD,
    ILLAGER,
    WATER;

    public static LegacyMobType getMobType(LivingEntity entity) {
        if (entity.getType().is(EntityTypeTags.UNDEAD)) return UNDEAD;
        if (entity.getType().is(EntityTypeTags.ARTHROPOD)) return ARTHROPOD;
        if (entity.getType().is(EntityTypeTags.ILLAGER)) return ILLAGER;
        if (entity.getType().is(EntityTypeTags.AQUATIC)) return WATER;
        return UNDEFINED;
    }
}
