package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.registries.Registries;
import net.neoforged.forgestuck.compat.ForgeRegistries;
import net.neoforged.forgestuck.compat.LegacyDeferredRegister;
import org.junit.jupiter.api.Test;

/** Datapack-registry Forge fields (enchantments) link and expose their key; contents fail per call. */
class DatapackRegistryViewTest {
    @Test
    void enchantmentsFieldHoldsKeyButLookupsFailAtTheCall() {
        assertEquals(Registries.ENCHANTMENT, ForgeRegistries.ENCHANTMENTS.getRegistryKey());
        assertEquals(Registries.ENCHANTMENT, ForgeRegistries.Keys.ENCHANTMENTS);
        var ex = assertThrows(UnsupportedOperationException.class, () -> ForgeRegistries.ENCHANTMENTS.getValues());
        assertTrue(ex.getMessage().contains("RegistryAccess"), ex.getMessage());
    }

    @Test
    void deferredRegisterHoldsDatapackObjectsLocally() {
        var register = LegacyDeferredRegister.create(ForgeRegistries.ENCHANTMENTS, "ex");
        var calls = new int[1];
        var object = register.register("thing", () -> {
            calls[0]++;
            return (net.minecraft.world.item.enchantment.Enchantment) null;
        });
        assertEquals("ex:thing", object.getId().toString());
        assertTrue(object.isPresent());
        assertEquals(0, calls[0]);
    }

    @Test
    void enchantmentsAreInTheForgeRegistriesMapping() {
        assertTrue(ForgeOwnerMappings.FORGE_REGISTRY_FIELDS.contains("ENCHANTMENTS"));
    }
}
