package net.neoforged.forgestuck.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.forgestuck.testsupport.TestBootstrap;
import net.neoforged.neoforge.event.entity.living.EffectParticleModificationEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LegacyPotionColorCalculationEventTest {
    private static final class CustomParticleEffectTest extends MobEffect {
        private CustomParticleEffectTest(ParticleOptions particle) {
            super(MobEffectCategory.BENEFICIAL, 0x123456, particle);
        }
    }

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.init();
    }

    @Test
    void exposesAnImmutableSnapshotAndMutableColorAndVisibility() {
        MobEffectInstance effect = new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100);
        LegacyPotionColorCalculationEvent event =
                new LegacyPotionColorCalculationEvent(null, 0x123456, false, List.of(effect));

        assertEquals(List.of(effect), event.getEffects());
        assertEquals(0x123456, event.getColor());
        assertFalse(event.areParticlesHidden());
        assertThrows(UnsupportedOperationException.class, () -> event.getEffects().clear());

        event.setColor(0xABCDEF);
        event.shouldHideParticles(true);

        assertEquals(0xABCDEF, event.getColor());
        assertTrue(event.areParticlesHidden());
        assertTrue(event.wasColorSet());
        assertTrue(event.wasParticleVisibilitySet());
    }

    @Test
    void calculatesTheVanillaWeightedColorFromVisibleEffects() {
        MobEffectInstance speed = new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 0);
        MobEffectInstance strength = new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, 1);
        MobEffectInstance hidden = new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0, false, false, true);
        int speedColor = speed.getEffect().value().getColor();
        int strengthColor = strength.getEffect().value().getColor();
        int expected = (((speedColor >> 16 & 0xFF) + 2 * (strengthColor >> 16 & 0xFF)) / 3 << 16)
                | (((speedColor >> 8 & 0xFF) + 2 * (strengthColor >> 8 & 0xFF)) / 3 << 8)
                | ((speedColor & 0xFF) + 2 * (strengthColor & 0xFF)) / 3;

        assertEquals(expected, LegacyPotionColorAdapters.calculateColor(List.of(speed, strength, hidden)));
        assertEquals(0, LegacyPotionColorAdapters.calculateColor(List.of(hidden)));
    }

    @Test
    void appliesLegacyColorAndVisibilityOnlyToVanillaSwirlParticles() {
        MobEffectInstance effect = new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100);
        EffectParticleModificationEvent particleEvent = new EffectParticleModificationEvent(null, effect);
        LegacyPotionColorCalculationEvent legacyEvent =
                new LegacyPotionColorCalculationEvent(null, 0, false, List.of(effect));
        legacyEvent.setColor(0x123456);
        legacyEvent.shouldHideParticles(true);

        LegacyPotionColorAdapters.applyOverrides(particleEvent, legacyEvent);

        assertFalse(particleEvent.isVisible());
        ColorParticleOption option = (ColorParticleOption) particleEvent.getParticleOptions();
        assertEquals(0x12 / 255.0F, option.getRed(), 0.001F);
        assertEquals(0x34 / 255.0F, option.getGreen(), 0.001F);
        assertEquals(0x56 / 255.0F, option.getBlue(), 0.001F);
        assertEquals(1.0F, option.getAlpha(), 0.001F);
        assertNotSame(effect.getParticleOptions(), option);
        assertSame(ParticleTypes.ENTITY_EFFECT, option.getType());
    }

    @Test
    void leavesVanillaParticlesUntouchedWhenListenersDoNotOverrideThem() {
        MobEffectInstance effect = new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100);
        EffectParticleModificationEvent particleEvent = new EffectParticleModificationEvent(null, effect);
        var original = particleEvent.getOriginalParticleOptions();
        LegacyPotionColorCalculationEvent legacyEvent =
                new LegacyPotionColorCalculationEvent(null, 0, false, List.of(effect));

        LegacyPotionColorAdapters.applyOverrides(particleEvent, legacyEvent);

        assertSame(original, particleEvent.getParticleOptions());
        assertTrue(particleEvent.isVisible());
    }

    @Test
    void keepsCustomParticleOptionsVisibleAndUnchanged() {
        MobEffectInstance effect = new MobEffectInstance(
                Holder.direct(new CustomParticleEffectTest(ParticleTypes.FLAME)), 100);
        EffectParticleModificationEvent particleEvent = new EffectParticleModificationEvent(null, effect);
        var original = particleEvent.getOriginalParticleOptions();
        LegacyPotionColorCalculationEvent legacyEvent =
                new LegacyPotionColorCalculationEvent(null, 0, false, List.of(effect));
        legacyEvent.setColor(0xABCDEF);
        legacyEvent.shouldHideParticles(true);

        LegacyPotionColorAdapters.applyOverrides(particleEvent, legacyEvent);

        assertSame(original, particleEvent.getParticleOptions());
        assertTrue(particleEvent.isVisible());
    }
}
