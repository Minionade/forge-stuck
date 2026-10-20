package net.neoforged.forgestuck.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public final class LegacyBlockPosNbt {
    private LegacyBlockPosNbt() {}

    public static CompoundTag write(BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("X", pos.getX());
        tag.putInt("Y", pos.getY());
        tag.putInt("Z", pos.getZ());
        return tag;
    }

    public static BlockPos read(CompoundTag tag) {
        return new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
    }
}
