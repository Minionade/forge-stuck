package net.neoforged.forgestuck.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.forgestuck.compat.ForgeRegistries.Keys;
import org.junit.jupiter.api.Test;

class DisplayContextRegistryViewTest {
    @Test
    void forgeDisplayContextRegistryKeyAndViewResolveToGeneratedEnumContexts() {
        assertEquals(ResourceLocation.fromNamespaceAndPath("forge", "display_contexts"),
                Keys.DISPLAY_CONTEXTS.location());
        assertEquals(Keys.DISPLAY_CONTEXTS, ForgeRegistries.DISPLAY_CONTEXTS.get().getRegistryKey());
    }
}
