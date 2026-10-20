package net.neoforged.forgestuck.compat;

import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.forgestuck.testsupport.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LegacyLiquidBlockTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.init();
    }

    @Test
    void returnsFlowingFluidFromDefaultLiquidBlockState() {
        assertSame(Fluids.WATER, LegacyLiquidBlock.getFluid((LiquidBlock) Blocks.WATER));
    }
}
