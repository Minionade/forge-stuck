package net.neoforged.forgestuck.compat;

import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StemBlock;

/** The 1.20.1 {@code StemGrownBlock}, which no longer exists in 1.21.1. */
public abstract class LegacyStemGrownBlock extends Block {
    public LegacyStemGrownBlock(Properties properties) {
        super(properties);
    }

    public abstract StemBlock getStem();

    public abstract AttachedStemBlock getAttachedStem();
}
