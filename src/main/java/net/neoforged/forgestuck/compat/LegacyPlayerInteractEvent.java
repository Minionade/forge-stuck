package net.neoforged.forgestuck.compat;

import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class LegacyPlayerInteractEvent {
    private LegacyPlayerInteractEvent() {}

    public static LegacyEventResult getUseBlock(PlayerInteractEvent.RightClickBlock event) {
        return result(event.getUseBlock());
    }

    public static LegacyEventResult getUseItem(PlayerInteractEvent.RightClickBlock event) {
        return result(event.getUseItem());
    }

    private static LegacyEventResult result(TriState state) {
        if (state.isTrue()) return LegacyEventResult.ALLOW;
        if (state.isFalse()) return LegacyEventResult.DENY;
        return LegacyEventResult.DEFAULT;
    }
}
