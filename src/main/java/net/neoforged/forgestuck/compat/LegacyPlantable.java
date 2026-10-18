package net.neoforged.forgestuck.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class LegacyPlantable {
    private LegacyPlantable() {}

    public static BlockState getPlant(LegacyIPlantable plantable, BlockGetter level, BlockPos pos) {
        return getPlant((Object) plantable, level, pos);
    }

    public static PlantType getPlantType(LegacyIPlantable plantable, BlockGetter level, BlockPos pos) {
        return getPlantType((Object) plantable, level, pos);
    }

    public static BlockState getPlant(Object plantable, BlockGetter level, BlockPos pos) {
        if (plantable instanceof LegacyIPlantable legacy) return legacy.getPlant(level, pos);
        if (plantable instanceof Block block) return block.defaultBlockState();
        throw new IllegalStateException("Unsupported legacy plantable object: " + plantable.getClass().getName());
    }

    public static PlantType getPlantType(Object plantable, BlockGetter level, BlockPos pos) {
        if (plantable instanceof LegacyIPlantable legacy) return legacy.getPlantType(level, pos);
        return defaultPlantType(plantable, level, pos);
    }

    public static PlantType defaultPlantType(Object plantable, BlockGetter level, BlockPos pos) {
        if (plantable instanceof AttachedStemBlock || plantable instanceof CropBlock) return PlantType.CROP;
        if (plantable instanceof SaplingBlock || plantable instanceof FlowerBlock) return PlantType.PLAINS;
        if (plantable == net.minecraft.world.level.block.Blocks.PITCHER_CROP) return PlantType.CROP;
        if (plantable == net.minecraft.world.level.block.Blocks.DEAD_BUSH) return PlantType.DESERT;
        if (plantable == net.minecraft.world.level.block.Blocks.LILY_PAD) return PlantType.WATER;
        if (plantable == net.minecraft.world.level.block.Blocks.RED_MUSHROOM
                || plantable == net.minecraft.world.level.block.Blocks.BROWN_MUSHROOM) return PlantType.CAVE;
        if (plantable == net.minecraft.world.level.block.Blocks.NETHER_WART) return PlantType.NETHER;
        return PlantType.PLAINS;
    }
}
