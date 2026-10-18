package net.neoforged.forgestuck.compat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class LegacyMissingMappingsEventTest {
    @Test
    void unavailableMissingMappingsEventReturnsNoMappings() {
        ResourceKey<?> registry = ResourceKey.createRegistryKey(ResourceLocation.parse("test:registry"));
        assertTrue(new LegacyMissingMappingsEvent().getAllMappings(registry).isEmpty());
    }
}
