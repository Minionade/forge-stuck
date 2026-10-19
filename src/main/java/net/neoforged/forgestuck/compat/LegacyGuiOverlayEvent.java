package net.neoforged.forgestuck.compat;

import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class LegacyGuiOverlayEvent {
    private LegacyGuiOverlayEvent() {}

    public static LegacyNamedGuiOverlay getOverlay(RenderGuiLayerEvent.Post event) {
        return getOverlayByName(event);
    }

    public static LegacyNamedGuiOverlay getOverlay(RenderGuiLayerEvent.Pre event) {
        return getOverlayByName(event);
    }

    public static boolean postAfterHealthbar(RenderGuiLayerEvent.Pre event) {
        return LegacyEventBusPost.post(NeoForge.EVENT_BUS, new RenderGuiLayerEvent.Post(event.getGuiGraphics(),
                event.getPartialTick(), event.getName(), event.getLayer()));
    }

    private static LegacyNamedGuiOverlay getOverlayByName(RenderGuiLayerEvent event) {
        return LegacyNamedGuiOverlay.of(event.getName());
    }
}
