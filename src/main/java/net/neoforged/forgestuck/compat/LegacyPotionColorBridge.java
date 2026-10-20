package net.neoforged.forgestuck.compat;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.EffectParticleModificationEvent;

/** Re-emits Forge's aggregate color event from NeoForge's per-effect particle update hook. */
public final class LegacyPotionColorBridge {
    private static boolean installed;

    private LegacyPotionColorBridge() {}

    public static synchronized void install() {
        if (installed) return;
        NeoForge.EVENT_BUS.addListener(EffectParticleModificationEvent.class,
                LegacyPotionColorBridge::onParticleModification);
        installed = true;
    }

    private static void onParticleModification(EffectParticleModificationEvent event) {
        LivingEntity entity = event.getEntity();
        var effects = entity.getActiveEffects();
        LegacyPotionColorCalculationEvent legacy = new LegacyPotionColorCalculationEvent(
                entity, LegacyPotionColorAdapters.calculateColor(effects),
                effects.stream().noneMatch(net.minecraft.world.effect.MobEffectInstance::isVisible), effects);
        NeoForge.EVENT_BUS.post(legacy);
        LegacyPotionColorAdapters.applyOverrides(event, legacy);
    }
}
