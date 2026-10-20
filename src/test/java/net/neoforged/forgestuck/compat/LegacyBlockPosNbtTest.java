package net.neoforged.forgestuck.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.forgestuck.testsupport.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LegacyBlockPosNbtTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.init();
    }

    @Test
    void roundTripsLegacyCompoundBlockPosition() {
        BlockPos expected = new BlockPos(12, 34, -56);

        assertEquals(expected, LegacyBlockPosNbt.read(LegacyBlockPosNbt.write(expected)));
    }

    @Test
    void readsMissingCoordinatesAsZero() {
        assertEquals(BlockPos.ZERO, LegacyBlockPosNbt.read(new CompoundTag()));
    }
}
