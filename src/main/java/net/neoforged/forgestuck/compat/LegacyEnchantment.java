package net.neoforged.forgestuck.compat;

import net.minecraft.world.entity.EquipmentSlot;

/**
 * Non-final base for legacy {@code Enchantment} subclasses. NeoForge 1.21.1's {@code Enchantment} is a final record
 * and enchantments are datapack entries, so legacy mod classes that extend it are re-parented onto this class by
 * {@code LegacyEnchantmentSubclassStep}. Instances keep their legacy fields and logic and can be held and called by
 * the mod, but they are not game enchantments: nothing registers them and the engine never consults them.
 */
public class LegacyEnchantment {
    private final LegacyEnchantmentRarity rarity;
    private final LegacyEnchantmentCategory category;
    private final EquipmentSlot[] slots;

    protected LegacyEnchantment(LegacyEnchantmentRarity rarity, LegacyEnchantmentCategory category,
            EquipmentSlot[] slots) {
        this.rarity = rarity;
        this.category = category;
        this.slots = slots;
    }

    /** Target of {@code super.canEnchant(stack)} from legacy subclasses (SRG {@code m_6081_}). */
    public boolean canEnchant(net.minecraft.world.item.ItemStack stack) {
        return true;
    }

    public LegacyEnchantmentRarity getRarity() {
        return rarity;
    }

    public LegacyEnchantmentCategory getCategory() {
        return category;
    }

    public EquipmentSlot[] getSlots() {
        return slots.clone();
    }
}
