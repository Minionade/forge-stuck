package net.neoforged.forgestuck.compat;

import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client half of {@link LegacyTickBridge}; separate so dedicated servers never load client event classes. */
final class LegacyClientTickBridge {
    private LegacyClientTickBridge() {}

    static void install() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(RenderFrameEvent.Pre.class, e -> bus.post(
                new LegacyTickEvent.RenderTickEvent(LegacyTickEvent.Phase.START,
                        e.getPartialTick().getGameTimeDeltaPartialTick(false))));
        bus.addListener(RenderFrameEvent.Post.class, e -> bus.post(
                new LegacyTickEvent.RenderTickEvent(LegacyTickEvent.Phase.END,
                        e.getPartialTick().getGameTimeDeltaPartialTick(false))));
        bus.addListener(ClientTickEvent.Pre.class, e -> bus.post(
                new LegacyTickEvent.ClientTickEvent(LegacyTickEvent.Phase.START)));
        bus.addListener(ClientTickEvent.Post.class, e -> bus.post(
                new LegacyTickEvent.ClientTickEvent(LegacyTickEvent.Phase.END)));
    }
}
