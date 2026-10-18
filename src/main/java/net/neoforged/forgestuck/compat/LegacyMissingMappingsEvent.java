package net.neoforged.forgestuck.compat;

import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.Event;

/**
 * Stand-in for Forge's {@code MissingMappingsEvent}. NeoForge 1.21.1 has no equivalent (registry remaps use datafixers),
 * so this event is never fired; legacy listeners register fine and simply never run.
 */
public class LegacyMissingMappingsEvent extends Event {
    LegacyMissingMappingsEvent() {}

    public List<?> getAllMappings(ResourceKey<?> registryKey) {
        return List.of();
    }

    public static final class Mapping {
        private Mapping() {}

        public net.minecraft.resources.ResourceLocation getKey() {
            throw new UnsupportedOperationException("NeoForge does not fire legacy missing-mapping events");
        }

        public void remap(Object value) {
            throw new UnsupportedOperationException("NeoForge does not fire legacy missing-mapping events");
        }
    }
}
