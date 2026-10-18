package net.neoforged.forgestuck.compat;

import net.minecraft.world.item.Tier;
import net.minecraft.world.level.block.state.BlockState;

public final class LegacyTierSortingRegistry {
    private LegacyTierSortingRegistry() {}

    public static boolean isCorrectTierForDrops(Tier tier, BlockState state) {
        return !state.is(tier.getIncorrectBlocksForDrops());
    }
}
