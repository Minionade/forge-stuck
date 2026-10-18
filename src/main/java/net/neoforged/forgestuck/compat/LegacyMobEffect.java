package net.neoforged.forgestuck.compat;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/** Adapts Forge 1.20.1's UUID-string effect modifier API to the holder/id-based 1.21.1 API. */
public final class LegacyMobEffect {
    private LegacyMobEffect() {}

    public static MobEffect addAttributeModifier(MobEffect effect, Attribute attribute, String uuid, double amount,
            AttributeModifier.Operation operation) {
        Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute);
        return effect.addAttributeModifier(holder, ResourceLocation.parse(uuid), amount, operation);
    }
}
