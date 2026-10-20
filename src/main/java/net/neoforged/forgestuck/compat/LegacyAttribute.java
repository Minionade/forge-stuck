package net.neoforged.forgestuck.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;

/** Bridges legacy attribute-instance lookups to the holder-based 1.21.1 API. */
public final class LegacyAttribute {
    private LegacyAttribute() {}

    public static double getValue(LivingEntity entity, Attribute attribute) {
        return entity.getAttributeValue(BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute));
    }
}
