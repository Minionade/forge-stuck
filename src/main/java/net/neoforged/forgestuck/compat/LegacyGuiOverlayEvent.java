package net.neoforged.forgestuck.compat;

import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

public final class LegacyGuiOverlayEvent {
    private LegacyGuiOverlayEvent() {}

    public static LegacyNamedGuiOverlay getOverlay(RenderGuiLayerEvent.Post event) {
        return getOverlayByName(event);
    }

    public static LegacyNamedGuiOverlay getOverlay(RenderGuiLayerEvent.Pre event) {
        return getOverlayByName(event);
    }

    private static LegacyNamedGuiOverlay getOverlayByName(RenderGuiLayerEvent event) {
        return LegacyNamedGuiOverlay.of(event.getName());
    }
}
