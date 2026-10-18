package net.neoforged.forgestuck.compat;

import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/** Forge exposed cancellation on the base Event even for non-cancellable subclasses. */
public final class LegacyRenderLivingAccess {
    private LegacyRenderLivingAccess() {}
    public static boolean isCancelable(RenderLivingEvent<?, ?> event) { return event instanceof ICancellableEvent; }
    public static boolean isCanceled(RenderLivingEvent<?, ?> event) {
        return event instanceof ICancellableEvent cancellable && cancellable.isCanceled();
    }
    public static void setCanceled(RenderLivingEvent<?, ?> event, boolean value) {
        if (!(event instanceof ICancellableEvent cancellable)) {
            throw new UnsupportedOperationException("RenderLivingEvent.Post is not cancellable");
        }
        cancellable.setCanceled(value);
    }
    public static boolean hasResult(RenderLivingEvent<?, ?> event) { return false; }
}
