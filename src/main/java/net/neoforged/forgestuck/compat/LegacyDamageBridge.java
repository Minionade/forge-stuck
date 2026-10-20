package net.neoforged.forgestuck.compat;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/** Relays the mutable NeoForge pre-damage event through Forge's legacy LivingDamageEvent shape. */
public final class LegacyDamageBridge {
    private static boolean installed;

    private LegacyDamageBridge() {}

    public static synchronized void install() {
        if (installed) return;
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false,
                LivingDamageEvent.Pre.class, LegacyDamageBridge::onPreDamage);
        installed = true;
    }

    private static void onPreDamage(LivingDamageEvent.Pre incoming) {
        LegacyLivingDamageEvent legacy = new LegacyLivingDamageEvent(
                incoming.getEntity(), incoming.getSource(), incoming.getNewDamage());
        NeoForge.EVENT_BUS.post(legacy);
        incoming.setNewDamage(legacy.isCanceled() ? 0 : legacy.getAmount());
    }
}
