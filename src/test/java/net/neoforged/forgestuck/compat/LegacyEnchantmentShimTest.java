package net.neoforged.forgestuck.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.forgestuck.testsupport.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LegacyEnchantmentShimTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.init();
    }

    @Test
    void deserializesLegacyEnchantmentListAndSkipsUnknownIds() {
        ListTag tags = new ListTag();
        CompoundTag efficiency = new CompoundTag();
        efficiency.putString("id", "minecraft:efficiency");
        efficiency.putShort("lvl", (short) 4);
        tags.add(efficiency);
        CompoundTag unknown = new CompoundTag();
        unknown.putString("id", "missing:enchantment");
        unknown.putShort("lvl", (short) 1);
        tags.add(unknown);

        var enchantment = LegacyEnchantmentRegistry.ENCHANTMENT.getOptional(
                ResourceLocation.parse("minecraft:efficiency")).orElseThrow();
        assertEquals(java.util.Map.of(enchantment, 4), LegacyEnchantmentShim.deserializeEnchantments(tags));
        assertTrue(LegacyEnchantmentShim.deserializeEnchantments(new ListTag()).isEmpty());
    }
}
