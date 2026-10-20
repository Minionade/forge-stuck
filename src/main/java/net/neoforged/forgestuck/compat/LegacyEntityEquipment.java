package net.neoforged.forgestuck.compat;

import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Exposes the legacy Entity armor-slot accessor now declared on LivingEntity. */
public final class LegacyEntityEquipment {
    private LegacyEntityEquipment() {}

    public static Iterable<ItemStack> getArmorSlots(Entity entity) {
        return entity instanceof LivingEntity living ? living.getArmorSlots() : List.of();
    }
}
