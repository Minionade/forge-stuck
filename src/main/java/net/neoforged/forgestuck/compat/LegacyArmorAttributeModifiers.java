package net.neoforged.forgestuck.compat;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;

public final class LegacyArmorAttributeModifiers {
    private LegacyArmorAttributeModifiers() {}

    public static Multimap<Attribute, AttributeModifier> get(Item item, EquipmentSlot slot) {
        ImmutableMultimap.Builder<Attribute, AttributeModifier> modifiers = ImmutableMultimap.builder();
        item.getDefaultAttributeModifiers().forEach(slot,
                (attribute, modifier) -> modifiers.put(attribute.value(), modifier));
        return modifiers.build();
    }
}
