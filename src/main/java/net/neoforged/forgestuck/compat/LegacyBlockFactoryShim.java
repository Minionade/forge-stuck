package net.neoforged.forgestuck.compat;

import net.minecraft.core.Direction;
import net.minecraft.world.flag.FeatureFlag;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class LegacyBlockFactoryShim {
    private LegacyBlockFactoryShim() {}

    public static RotatedPillarBlock log(MapColor endColor, MapColor sideColor) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .mapColor(state -> state.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y
                        ? endColor : sideColor)
                .instrument(NoteBlockInstrument.BASS)
                .strength(2.0F)
                .sound(SoundType.WOOD)
                .ignitedByLava();
        return new RotatedPillarBlock(properties);
    }

    public static LeavesBlock leaves(SoundType sound) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .mapColor(MapColor.PLANT)
                .strength(0.2F)
                .randomTicks()
                .sound(sound)
                .noOcclusion()
                .isValidSpawn((state, level, pos, entityType) -> false)
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false)
                .ignitedByLava()
                .pushReaction(PushReaction.DESTROY)
                .isRedstoneConductor((state, level, pos) -> false);
        return new LeavesBlock(properties);
    }

    public static ButtonBlock button(BlockSetType type, FeatureFlag... requiredFeatures) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .noCollission()
                .strength(0.5F)
                .pushReaction(PushReaction.DESTROY)
                .requiredFeatures(requiredFeatures);
        return new ButtonBlock(type, 30, properties);
    }
}
