package net.neoforged.forgestuck.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.neoforged.neoforge.event.EventHooks;

/** Legacy 2x2 sapling grower: places the mega feature when four matching saplings form a square. */
public abstract class LegacyAbstractMegaTreeGrower extends LegacyAbstractTreeGrower {
    public LegacyAbstractMegaTreeGrower() {}

    protected abstract ResourceKey<ConfiguredFeature<?, ?>> getConfiguredMegaFeature(RandomSource random);

    @Override
    public boolean growTree(ServerLevel level, ChunkGenerator generator, BlockPos pos,
                            BlockState sapling, RandomSource random) {
        for (int i = 0; i >= -1; --i) {
            for (int j = 0; j >= -1; --j) {
                if (isTwoByTwoSapling(sapling, level, pos, i, j)) {
                    return placeMega(level, generator, pos, sapling, random, i, j);
                }
            }
        }
        return super.growTree(level, generator, pos, sapling, random);
    }

    public boolean placeMega(ServerLevel level, ChunkGenerator generator, BlockPos pos, BlockState sapling,
                             RandomSource random, int i, int j) {
        ResourceKey<ConfiguredFeature<?, ?>> key = getConfiguredMegaFeature(random);
        if (key == null) return false;
        Holder<ConfiguredFeature<?, ?>> feature = level.registryAccess()
                .registryOrThrow(Registries.CONFIGURED_FEATURE).getHolder(key).orElse(null);
        if (feature == null) return false;
        var event = EventHooks.fireBlockGrowFeature(level, random, pos, feature);
        if (event.isCanceled() || event.getFeature() == null) return false;
        BlockState air = Blocks.AIR.defaultBlockState();
        level.setBlock(pos.offset(i, 0, j), air, 4);
        level.setBlock(pos.offset(i + 1, 0, j), air, 4);
        level.setBlock(pos.offset(i, 0, j + 1), air, 4);
        level.setBlock(pos.offset(i + 1, 0, j + 1), air, 4);
        if (event.getFeature().value().place(level, generator, random, pos.offset(i, 0, j))) return true;
        level.setBlock(pos.offset(i, 0, j), sapling, 4);
        level.setBlock(pos.offset(i + 1, 0, j), sapling, 4);
        level.setBlock(pos.offset(i, 0, j + 1), sapling, 4);
        level.setBlock(pos.offset(i + 1, 0, j + 1), sapling, 4);
        return false;
    }

    public static boolean isTwoByTwoSapling(BlockState state, BlockGetter level, BlockPos pos, int i, int j) {
        return level.getBlockState(pos.offset(i, 0, j)).is(state.getBlock())
                && level.getBlockState(pos.offset(i + 1, 0, j)).is(state.getBlock())
                && level.getBlockState(pos.offset(i, 0, j + 1)).is(state.getBlock())
                && level.getBlockState(pos.offset(i + 1, 0, j + 1)).is(state.getBlock());
    }
}
