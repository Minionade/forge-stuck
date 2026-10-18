package net.neoforged.forgestuck.compat;

import net.neoforged.neoforge.client.event.RenderNameTagEvent;

public final class LegacyRenderNameTagEvent {
    private LegacyRenderNameTagEvent() {}

    public static LegacyEventResult getResult(RenderNameTagEvent event) {
        return switch (event.canRender()) {
            case TRUE -> LegacyEventResult.ALLOW;
            case FALSE -> LegacyEventResult.DENY;
            case DEFAULT -> LegacyEventResult.DEFAULT;
        };
    }
}
