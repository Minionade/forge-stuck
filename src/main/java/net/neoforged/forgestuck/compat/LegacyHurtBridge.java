package net.neoforged.forgestuck.compat;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** Relays pre-mitigation damage into the legacy hurt event; not an exact Forge actuallyHurt hook. */
public final class LegacyHurtBridge {
    private static boolean installed;
    private LegacyHurtBridge() {}

    public static synchronized void install() {
        if (installed) return;
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false,
                LivingIncomingDamageEvent.class, LegacyHurtBridge::onIncoming);
        installed = true;
    }

    private static void onIncoming(LivingIncomingDamageEvent incoming) {
        var hurt = new LegacyLivingHurtEvent(incoming.getEntity(), incoming.getSource(), incoming.getAmount());
        NeoForge.EVENT_BUS.post(hurt);
        incoming.setAmount(hurt.getAmount());
        if (hurt.isCanceled()) incoming.setCanceled(true);
    }
}
