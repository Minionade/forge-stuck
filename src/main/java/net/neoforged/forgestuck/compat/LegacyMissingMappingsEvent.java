package net.neoforged.forgestuck.compat;

import net.neoforged.bus.api.Event;

/**
 * Stand-in for Forge's {@code MissingMappingsEvent}. NeoForge 1.21.1 has no equivalent (registry remaps use datafixers),
 * so this event is never fired; legacy listeners register fine and simply never run.
 */
public class LegacyMissingMappingsEvent extends Event {
    LegacyMissingMappingsEvent() {}
}
