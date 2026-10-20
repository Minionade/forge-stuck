package net.neoforged.forgestuck.compat;

import net.neoforged.neoforge.event.level.BlockEvent;

public final class LegacyBlockBreakEvent {
    private LegacyBlockBreakEvent() {}

    public static int getExpToDrop(BlockEvent.BreakEvent event) {
        return 0;
    }

    public static void setExpToDrop(BlockEvent.BreakEvent event, int experience) {}
}
