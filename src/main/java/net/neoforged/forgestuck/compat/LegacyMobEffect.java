package net.neoforged.forgestuck.compat;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.LivingEntity;

/** Adapts Forge 1.20.1's UUID-string effect modifier API to the holder/id-based 1.21.1 API. */
public final class LegacyMobEffect {
    private LegacyMobEffect() {}

    public static MobEffect addAttributeModifier(MobEffect effect, Attribute attribute, String uuid, double amount,
            AttributeModifier.Operation operation) {
        Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute);
        return effect.addAttributeModifier(holder, ResourceLocation.parse(uuid), amount, operation);
    }

    public static boolean hasEffect(LivingEntity entity, MobEffect effect) {
        return entity.hasEffect(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect));
    }

    public static MobEffectInstance getEffect(LivingEntity entity, MobEffect effect) {
        return entity.getEffect(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect));
    }

    public static boolean removeEffect(LivingEntity entity, MobEffect effect) {
        return entity.removeEffect(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect));
    }
}
