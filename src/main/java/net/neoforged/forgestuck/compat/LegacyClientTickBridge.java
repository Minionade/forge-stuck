package net.neoforged.forgestuck.compat;

import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client half of {@link LegacyTickBridge}; separate so dedicated servers never load client event classes. */
final class LegacyClientTickBridge {
    private LegacyClientTickBridge() {}

    static void install() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(ClientTickEvent.Pre.class, e -> bus.post(
                new LegacyTickEvent.ClientTickEvent(LegacyTickEvent.Phase.START)));
        bus.addListener(ClientTickEvent.Post.class, e -> bus.post(
                new LegacyTickEvent.ClientTickEvent(LegacyTickEvent.Phase.END)));
    }
}
