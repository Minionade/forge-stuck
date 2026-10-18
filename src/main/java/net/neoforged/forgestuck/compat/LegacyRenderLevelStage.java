package net.neoforged.forgestuck.compat;

import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class LegacyRenderLevelStage {
    private LegacyRenderLevelStage() {}

    public static float getPartialTick(RenderLevelStageEvent event) {
        return event.getPartialTick().getGameTimeDeltaPartialTick(false);
    }
}
