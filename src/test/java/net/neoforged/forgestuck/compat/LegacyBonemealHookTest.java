package net.neoforged.forgestuck.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.player.BonemealEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LegacyBonemealHookTest {
    @BeforeAll
    static void bootstrap() {
        net.neoforged.forgestuck.testsupport.TestBootstrap.init();
    }

    @Test
    void successfulTargetEventMapsToForgeHandledResult() {
        var event = new BonemealEvent(null, null, null, Blocks.STONE.defaultBlockState(),
                new ItemStack(Items.BONE_MEAL));
        event.setSuccessful(true);
        assertEquals(1, LegacyBonemealHook.legacyResult(event));
    }

    @Test
    void cancelledUnsuccessfulTargetEventMapsToForgeCancellation() {
        var event = new BonemealEvent(null, null, null, Blocks.STONE.defaultBlockState(),
                new ItemStack(Items.BONE_MEAL));
        event.setCanceled(true);
        assertEquals(-1, LegacyBonemealHook.legacyResult(event));
    }

    @Test
    void untouchedTargetEventMapsToForgeDefaultResult() {
        var event = new BonemealEvent(null, null, null, Blocks.STONE.defaultBlockState(),
                new ItemStack(Items.BONE_MEAL));
        assertEquals(0, LegacyBonemealHook.legacyResult(event));
    }
}
