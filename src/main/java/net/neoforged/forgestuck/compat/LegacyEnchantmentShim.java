package net.neoforged.forgestuck.compat;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

/** Reads Forge-era enchantment lists after enchantments moved to registry-backed item components. */
public final class LegacyEnchantmentShim {
    private LegacyEnchantmentShim() {}

    public static Map<Enchantment, Integer> deserializeEnchantments(ListTag tags) {
        Map<Enchantment, Integer> enchantments = new HashMap<>();
        for (int index = 0; index < tags.size(); index++) {
            if (!(tags.get(index) instanceof CompoundTag tag) || !tag.contains("id")) continue;
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("id"));
            if (id == null) continue;
            LegacyEnchantmentRegistry.ENCHANTMENT.getOptional(id)
                    .ifPresent(enchantment -> enchantments.put(enchantment, (int) tag.getShort("lvl")));
        }
        return enchantments;
    }
}
