package net.neoforged.forgestuck.compat;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

/**
 * Forge's cancelable {@code EntityItemPickupEvent} maps to NeoForge's {@code ItemEntityPickupEvent.Pre}.
 * Cancelling denies the pickup; un-cancelling restores the default rules. Forge results are not modelled.
 */
public final class LegacyItemPickup {
    private LegacyItemPickup() {}

    public static Player getEntity(ItemEntityPickupEvent.Pre event) {
        return event.getPlayer();
    }

    public static ItemEntity getItem(ItemEntityPickupEvent.Pre event) {
        return event.getItemEntity();
    }

    public static void setCanceled(ItemEntityPickupEvent.Pre event, boolean canceled) {
        event.setCanPickup(canceled ? TriState.FALSE : TriState.DEFAULT);
    }

    public static boolean isCanceled(ItemEntityPickupEvent.Pre event) {
        return event.canPickup() == TriState.FALSE;
    }

    public static boolean isCancelable(ItemEntityPickupEvent.Pre event) {
        return true;
    }

    public static boolean hasResult(ItemEntityPickupEvent.Pre event) {
        return false;
    }
}
