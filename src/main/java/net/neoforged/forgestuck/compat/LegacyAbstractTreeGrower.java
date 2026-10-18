package net.neoforged.forgestuck.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.neoforged.neoforge.event.EventHooks;

/** Legacy single-sapling grower. Keeps named and production-SRG subclass callbacks virtual. */
public abstract class LegacyAbstractTreeGrower {
    public LegacyAbstractTreeGrower() {}

    protected abstract ResourceKey<ConfiguredFeature<?, ?>> getConfiguredFeature(RandomSource random, boolean flowers);

    public boolean growTree(ServerLevel level, ChunkGenerator generator, BlockPos pos,
                            BlockState sapling, RandomSource random) {
        ResourceKey<ConfiguredFeature<?, ?>> key = getConfiguredFeature(random, hasFlowers(level, pos));
        if (key == null) return false;
        Holder<ConfiguredFeature<?, ?>> feature = level.registryAccess()
                .registryOrThrow(Registries.CONFIGURED_FEATURE).getHolder(key).orElse(null);
        var event = EventHooks.fireBlockGrowFeature(level, random, pos, feature);
        if (event.isCanceled() || event.getFeature() == null) return false;
        BlockState fluid = level.getFluidState(pos).createLegacyBlock();
        level.setBlock(pos, fluid, 4);
        if (event.getFeature().value().place(level, generator, random, pos)) {
            if (level.getBlockState(pos) == fluid) level.sendBlockUpdated(pos, sapling, fluid, 2);
            return true;
        }
        level.setBlock(pos, sapling, 4);
        return false;
    }

    private boolean hasFlowers(LevelAccessor level, BlockPos pos) {
        for (BlockPos nearby : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, 1, 2))) {
            if (level.getBlockState(nearby).is(BlockTags.FLOWERS)) return true;
        }
        return false;
    }
}
