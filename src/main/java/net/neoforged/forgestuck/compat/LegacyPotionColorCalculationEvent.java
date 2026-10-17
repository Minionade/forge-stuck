package net.neoforged.forgestuck.compat;

import net.neoforged.bus.api.Event;

/**
 * Stand-in for Forge's {@code PotionColorCalculationEvent}, which NeoForge 1.21.1 removed. Never fired; legacy
 * listeners register fine and simply never run.
 */
public class LegacyPotionColorCalculationEvent extends Event {
    LegacyPotionColorCalculationEvent() {}
}
