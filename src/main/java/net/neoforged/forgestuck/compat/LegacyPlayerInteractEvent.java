package net.neoforged.forgestuck.compat;

import net.minecraft.world.InteractionResult;
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

    // NeoForge's empty-click hook discards event results, so Forge's cancellation result has no direct equivalent.
    public static InteractionResult getCancellationResult(PlayerInteractEvent.RightClickEmpty event) {
        return InteractionResult.PASS;
    }

    public static void setCancellationResult(PlayerInteractEvent.RightClickEmpty event, InteractionResult result) {}

    public static InteractionResult getCancellationResult(PlayerInteractEvent.LeftClickEmpty event) {
        return InteractionResult.PASS;
    }

    public static void setCancellationResult(PlayerInteractEvent.LeftClickEmpty event, InteractionResult result) {}

    private static LegacyEventResult result(TriState state) {
        if (state.isTrue()) return LegacyEventResult.ALLOW;
        if (state.isFalse()) return LegacyEventResult.DENY;
        return LegacyEventResult.DEFAULT;
    }
}
