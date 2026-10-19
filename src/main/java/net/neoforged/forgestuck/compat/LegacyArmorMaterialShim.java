package net.neoforged.forgestuck.compat;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;

/** Converts a legacy armor material to the registry-independent 1.21.1 material used by ArmorItem. */
public final class LegacyArmorMaterialShim {
    private static final Map<LegacyArmorMaterial, Holder<ArmorMaterial>> MATERIALS = new WeakHashMap<>();

    private LegacyArmorMaterialShim() {}

    public static Holder<ArmorMaterial> asHolder(LegacyArmorMaterial material) {
        synchronized (MATERIALS) {
            return MATERIALS.computeIfAbsent(material, LegacyArmorMaterialShim::create);
        }
    }

    public static Item.Properties setDurability(
            LegacyArmorMaterial material, ArmorItem.Type type, Item.Properties properties) {
        return properties.durability(material.getDurabilityForType(type));
    }

    private static Holder<ArmorMaterial> create(LegacyArmorMaterial legacy) {
        Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        for (ArmorItem.Type type : LegacyArmorTypeShim.removeBody(ArmorItem.Type.values())) {
            defense.put(type, legacy.getDefenseForType(type));
        }
        ResourceLocation name = ResourceLocation.parse(legacy.getName());
        ArmorMaterial material = new ArmorMaterial(defense, legacy.getEnchantmentValue(),
                Holder.direct(legacy.getEquipSound()), legacy::getRepairIngredient,
                List.of(new ArmorMaterial.Layer(name)), legacy.getToughness(), legacy.getKnockbackResistance());
        return Holder.direct(material);
    }
}
