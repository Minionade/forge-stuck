package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.registries.Registries;
import net.neoforged.forgestuck.compat.ForgeRegistries;
import net.neoforged.forgestuck.compat.LegacyDeferredRegister;
import org.junit.jupiter.api.Test;

/** Datapack-registry Forge fields (enchantments) link and expose their key; contents and registration fail per call. */
class DatapackRegistryViewTest {
    @Test
    void enchantmentsFieldHoldsKeyButLookupsFailAtTheCall() {
        assertEquals(Registries.ENCHANTMENT, ForgeRegistries.ENCHANTMENTS.getRegistryKey());
        assertEquals(Registries.ENCHANTMENT, ForgeRegistries.Keys.ENCHANTMENTS);
        var ex = assertThrows(UnsupportedOperationException.class, () -> ForgeRegistries.ENCHANTMENTS.getValues());
        assertTrue(ex.getMessage().contains("RegistryAccess"), ex.getMessage());
    }

    @Test
    void deferredRegisterCreationLinksButRegistrationFails() {
        var register = LegacyDeferredRegister.create(ForgeRegistries.ENCHANTMENTS, "ex");
        var ex = assertThrows(UnsupportedOperationException.class, () -> register.register("thing", () -> null));
        assertTrue(ex.getMessage().contains("datapack"), ex.getMessage());
    }

    @Test
    void enchantmentsAreInTheForgeRegistriesMapping() {
        assertTrue(ForgeOwnerMappings.FORGE_REGISTRY_FIELDS.contains("ENCHANTMENTS"));
    }
}
