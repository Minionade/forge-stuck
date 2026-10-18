package net.neoforged.forgestuck.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.CommonHooks;

public final class LegacyCropGrowth {
    private LegacyCropGrowth() {}

    public static boolean onCropsGrowPre(Level level, BlockPos pos, BlockState state, boolean defaultResult) {
        return CommonHooks.canCropGrow(level, pos, state, defaultResult);
    }

    public static void onCropsGrowPost(Level level, BlockPos pos, BlockState state) {
        CommonHooks.fireCropGrowPost(level, pos, state);
    }
}
