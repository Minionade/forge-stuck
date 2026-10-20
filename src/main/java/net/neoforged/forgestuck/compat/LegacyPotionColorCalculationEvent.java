package net.neoforged.forgestuck.compat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

/** Forge's aggregate effect-color event, adapted to NeoForge's per-effect particle pipeline. */
public class LegacyPotionColorCalculationEvent extends LivingEvent {
    private final Collection<MobEffectInstance> effects;
    private int color;
    private boolean hideParticles;
    private boolean colorSet;
    private boolean particleVisibilitySet;

    public LegacyPotionColorCalculationEvent(LivingEntity entity, int color, boolean hideParticles,
            Collection<MobEffectInstance> effects) {
        super(entity);
        this.color = color;
        this.hideParticles = hideParticles;
        this.effects = Collections.unmodifiableList(new ArrayList<>(effects));
    }

    public Collection<MobEffectInstance> getEffects() {
        return effects;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
        colorSet = true;
    }

    public boolean areParticlesHidden() {
        return hideParticles;
    }

    public void shouldHideParticles(boolean hideParticles) {
        this.hideParticles = hideParticles;
        particleVisibilitySet = true;
    }

    boolean wasColorSet() {
        return colorSet;
    }

    boolean wasParticleVisibilitySet() {
        return particleVisibilitySet;
    }
}
