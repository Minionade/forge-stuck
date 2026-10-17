package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.neoforged.forgestuck.compat.LegacyForgeMod;
import net.neoforged.neoforge.common.NeoForgeMod;
import org.junit.jupiter.api.Test;

class LegacyForgeModTest {
    @Test
    void fieldsPointAtTheNeoForgeCounterparts() {
        assertEquals(NeoForgeMod.LAVA_TYPE.getKey(), LegacyForgeMod.LAVA_TYPE.getKey());
        assertEquals(NeoForgeMod.WATER_TYPE.getKey(), LegacyForgeMod.WATER_TYPE.getKey());
        assertEquals(NeoForgeMod.SWIM_SPEED.getKey(), LegacyForgeMod.SWIM_SPEED.getKey());
        assertEquals(NeoForgeMod.NAMETAG_DISTANCE.getKey(), LegacyForgeMod.NAMETAG_DISTANCE.getKey());
    }
}