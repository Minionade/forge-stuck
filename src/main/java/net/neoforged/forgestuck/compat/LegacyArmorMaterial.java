package net.neoforged.forgestuck.compat;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * The 1.20.1 armor-material interface, which legacy mods can still implement.
 * Implementations are not 1.21.1 registry-backed armor materials.
 */
public interface LegacyArmorMaterial {
    int getDurabilityForType(ArmorItem.Type type);

    int getDefenseForType(ArmorItem.Type type);

    int getEnchantmentValue();

    SoundEvent getEquipSound();

    Ingredient getRepairIngredient();

    String getName();

    float getToughness();

    float getKnockbackResistance();
}
