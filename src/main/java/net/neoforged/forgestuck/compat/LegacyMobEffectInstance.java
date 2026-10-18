package net.neoforged.forgestuck.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/** Adapts the removed direct-MobEffect constructor to the holder-based 1.21.1 API. */
public final class LegacyMobEffectInstance extends MobEffectInstance {
    public LegacyMobEffectInstance(MobEffect effect, int duration, int amplifier) {
        super(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect), duration, amplifier);
    }
}
