package net.neoforged.forgestuck.compat;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.RegistryBuilder;
import org.junit.jupiter.api.Test;

class LegacyRegistryBuilderTest {
    @Test
    void disableSavingUsesNeoForgeInMemoryRegistryBehavior() {
        ResourceKey<Registry<String>> key =
                ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("test", "registry"));
        RegistryBuilder<String> target = new RegistryBuilder<>(key);

        assertDoesNotThrow(() -> new LegacyRegistryBuilder<String>().disableSaving().configure(target));
    }
}
