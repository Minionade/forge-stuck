package net.neoforged.forgestuck.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.CreateFluidSourceEvent;

public final class LegacyFluidSourceEvent {
    private LegacyFluidSourceEvent() {}

    public static boolean canCreateFluidSource(Level level, BlockPos pos, BlockState state, boolean defaultResult) {
        CreateFluidSourceEvent event = new CreateFluidSourceEvent(level, pos, state);
        event.setCanConvert(defaultResult);
        NeoForge.EVENT_BUS.post(event);
        return event.canConvert();
    }
}
