package net.neoforged.forgestuck.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.BonemealEvent;

public final class LegacyBonemealHook {
    private LegacyBonemealHook() {}

    public static int onApplyBonemeal(Player player, Level level, BlockPos pos, BlockState state, ItemStack stack) {
        if (player == null) return 0;

        BonemealEvent event = EventHooks.fireBonemealEvent(player, level, pos, state, stack);
        int result = legacyResult(event);
        if (result != 1) return result;

        if (!level.isClientSide) stack.shrink(1);
        return 1;
    }

    static int legacyResult(BonemealEvent event) {
        if (event.isSuccessful()) return 1;
        return event.isCanceled() ? -1 : 0;
    }
}
