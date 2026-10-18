package net.neoforged.forgestuck.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;

public final class LegacyEntityAttributeModification {
    private LegacyEntityAttributeModification() {}

    public static void add(EntityAttributeModificationEvent event, EntityType<? extends LivingEntity> entityType,
            Attribute attribute) {
        event.add(entityType, BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute));
    }

    public static void add(EntityAttributeModificationEvent event, EntityType<? extends LivingEntity> entityType,
            Attribute attribute, double value) {
        event.add(entityType, BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute), value);
    }
}
