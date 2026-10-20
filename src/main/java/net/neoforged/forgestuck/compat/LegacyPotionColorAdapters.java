package net.neoforged.forgestuck.compat;

import java.util.Collection;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.FastColor;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.event.entity.living.EffectParticleModificationEvent;

/** Implements the legacy aggregate color and visibility values on NeoForge's per-effect particle options. */
public final class LegacyPotionColorAdapters {
    private LegacyPotionColorAdapters() {}

    public static int calculateColor(Collection<MobEffectInstance> effects) {
        long red = 0;
        long green = 0;
        long blue = 0;
        long weightTotal = 0;
        for (MobEffectInstance effect : effects) {
            if (!effect.isVisible()) continue;
            int weight = effect.getAmplifier() + 1;
            int color = effect.getEffect().value().getColor();
            red += weight * ((color >> 16) & 0xFF);
            green += weight * ((color >> 8) & 0xFF);
            blue += weight * (color & 0xFF);
            weightTotal += weight;
        }
        if (weightTotal == 0) return 0;
        return ((int) (red / weightTotal) << 16)
                | ((int) (green / weightTotal) << 8)
                | (int) (blue / weightTotal);
    }

    public static void applyOverrides(EffectParticleModificationEvent event,
            LegacyPotionColorCalculationEvent legacy) {
        ParticleOptions original = event.getOriginalParticleOptions();
        if (!(original instanceof ColorParticleOption colorOption)
                || colorOption.getType() != ParticleTypes.ENTITY_EFFECT) {
            return;
        }
        if (legacy.wasParticleVisibilitySet()) {
            event.setVisible(!legacy.areParticlesHidden());
        }
        if (legacy.wasColorSet()) {
            int color = legacy.getColor();
            event.setParticleOptions(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT,
                    FastColor.ARGB32.colorFromFloat(colorOption.getAlpha(),
                            ((color >> 16) & 0xFF) / 255.0F,
                            ((color >> 8) & 0xFF) / 255.0F,
                            (color & 0xFF) / 255.0F)));
        }
    }
}
