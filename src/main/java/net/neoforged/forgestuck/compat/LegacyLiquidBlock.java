package net.neoforged.forgestuck.compat;

import java.util.Objects;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FlowingFluid;

/** Restores the Forge-era LiquidBlock fluid accessor on runtimes without that method. */
public final class LegacyLiquidBlock {
    private LegacyLiquidBlock() {}

    public static FlowingFluid getFluid(LiquidBlock block) {
        Objects.requireNonNull(block, "block");
        if (block.defaultBlockState().getFluidState().getType() instanceof FlowingFluid fluid) {
            return fluid;
        }
        throw new IllegalStateException("LiquidBlock default state does not contain a FlowingFluid: "
                + block.getClass().getName());
    }
}
