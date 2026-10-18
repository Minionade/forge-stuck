package net.neoforged.forgestuck.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

class LegacyArmorTextureHooksTest {
    private static final class LegacyArmorTextureTestItemTest {
        public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
            return "example:armor_" + type;
        }
    }

    private static final class NullArmorTextureTest {
        public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
            return null;
        }
    }

    @Test
    void invokesLegacyItemOverrideAndFallsBackOnNull() {
        assertEquals("example:armor_overlay", LegacyArmorTextureHooks.getLegacyArmorTexture(
                new LegacyArmorTextureTestItemTest(), null, null, "default:texture", null, "overlay"));
        assertEquals("default:texture", LegacyArmorTextureHooks.getLegacyArmorTexture(
                new NullArmorTextureTest(), null, null, "default:texture", null, "overlay"));
    }
}
