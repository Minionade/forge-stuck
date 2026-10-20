package net.neoforged.forgestuck.compat;

import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** NeoForge's StartTracking event is not cancellable. */
public final class LegacyStartTrackingEvent {
    private LegacyStartTrackingEvent() {}

    public static boolean isCanceled(PlayerEvent.StartTracking event) {
        return false;
    }
}
