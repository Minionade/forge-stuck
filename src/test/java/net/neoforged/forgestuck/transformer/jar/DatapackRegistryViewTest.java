package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.registries.Registries;
import net.neoforged.forgestuck.compat.ForgeRegistries;
import net.neoforged.forgestuck.compat.LegacyDeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Datapack-registry Forge fields (enchantments) link and expose their key; contents fail per call. */
class DatapackRegistryViewTest {
    @BeforeAll
    static void bootstrap() {
        net.neoforged.forgestuck.testsupport.TestBootstrap.init();
    }

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

    @Test
    void fluidTypesRegistryIsAvailableThroughTheLegacySupplierField() {
        assertEquals(NeoForgeRegistries.Keys.FLUID_TYPES, ForgeRegistries.FLUID_TYPES.get().getRegistryKey());
        assertTrue(ForgeOwnerMappings.defaults().supportsMember("net/minecraftforge/registries/ForgeRegistries",
                "FLUID_TYPES", "Ljava/util/function/Supplier;"));
    }
}
