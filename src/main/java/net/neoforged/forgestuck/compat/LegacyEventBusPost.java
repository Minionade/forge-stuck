package net.neoforged.forgestuck.compat;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.IEventBus;

public final class LegacyEventBusPost {
    private LegacyEventBusPost() {}

    public static boolean post(IEventBus bus, Event event) {
        Event posted = bus.post(event);
        return posted instanceof ICancellableEvent cancellable && cancellable.isCanceled();
    }
}
