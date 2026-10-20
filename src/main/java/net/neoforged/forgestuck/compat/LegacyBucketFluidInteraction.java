package net.neoforged.forgestuck.compat;

import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Restores Forge's bucket interaction with block entities exposing legacy fluid capabilities. */
public final class LegacyBucketFluidInteraction {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private LegacyBucketFluidInteraction() {}

    public static void install() {
        if (INSTALLED.compareAndSet(false, true)) {
            NeoForge.EVENT_BUS.addListener(LegacyBucketFluidInteraction::onRightClickBlock);
        }
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        var player = event.getEntity();
        var level = event.getLevel();
        BlockEntity blockEntity = level.getBlockEntity(event.getPos());
        if (blockEntity == null) return;

        LazyOptional<IFluidHandler> handler = LegacyCapabilities.getCapability(
                blockEntity, LegacyForgeCapabilities.FLUID_HANDLER, event.getFace());
        IFluidHandler fluidHandler = handler.resolve().orElse(null);
        if (fluidHandler != null
                && FluidUtil.interactWithFluidHandler(player, event.getHand(), fluidHandler)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
