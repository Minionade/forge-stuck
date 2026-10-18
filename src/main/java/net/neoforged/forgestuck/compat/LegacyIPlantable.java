package net.neoforged.forgestuck.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** Link-compatible contract for legacy plantable blocks; NeoForge no longer uses PlantType for engine behavior. */
public interface LegacyIPlantable {
    BlockState getPlant(BlockGetter level, BlockPos pos);

    default PlantType getPlantType(BlockGetter level, BlockPos pos) {
        return LegacyPlantable.defaultPlantType(this, level, pos);
    }
}
