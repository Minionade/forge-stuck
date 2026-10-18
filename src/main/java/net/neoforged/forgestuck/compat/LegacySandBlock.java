package net.neoforged.forgestuck.compat;

import net.minecraft.util.ColorRGBA;
import net.minecraft.world.level.block.ColoredFallingBlock;

/** The 1.20.1 {@code SandBlock(int, Properties)}, now {@code ColoredFallingBlock(ColorRGBA, Properties)}. */
public class LegacySandBlock extends ColoredFallingBlock {
    public LegacySandBlock(int dustColor, Properties properties) {
        super(new ColorRGBA(dustColor), properties);
    }
}
