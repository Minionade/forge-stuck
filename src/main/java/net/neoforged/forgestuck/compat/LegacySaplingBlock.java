package net.neoforged.forgestuck.compat;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Used only for construction sites with the removed AbstractTreeGrower constructor. */
public class LegacySaplingBlock extends SaplingBlock {
    private final LegacyAbstractTreeGrower legacyGrower;

    public LegacySaplingBlock(LegacyAbstractTreeGrower grower, BlockBehaviour.Properties properties) {
        // The native grower is never used by advanceTree for a legacy instance.
        super(TreeGrower.OAK, properties);
        legacyGrower = java.util.Objects.requireNonNull(grower, "grower");
    }

    public LegacySaplingBlock(TreeGrower grower, BlockBehaviour.Properties properties) {
        super(grower, properties);
        legacyGrower = null;
    }

    @Override
    public void advanceTree(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (legacyGrower == null) {
            super.advanceTree(level, pos, state, random);
        } else if (state.getValue(STAGE) == 0) {
            level.setBlock(pos, state.cycle(STAGE), 4);
        } else {
            legacyGrower.growTree(level, level.getChunkSource().getGenerator(), pos, state, random);
        }
    }

    @Override
    public MapCodec<? extends SaplingBlock> codec() {
        if (legacyGrower != null) {
            throw new UnsupportedOperationException("Serializing a legacy custom tree grower needs a mod-specific codec");
        }
        return super.codec();
    }
}
