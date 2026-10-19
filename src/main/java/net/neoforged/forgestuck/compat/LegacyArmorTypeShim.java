package net.neoforged.forgestuck.compat;

import net.minecraft.world.item.ArmorItem;

/** Filters the 1.21.1 BODY armor type from 1.20.1 armor-type iteration. */
public final class LegacyArmorTypeShim {
    private LegacyArmorTypeShim() {}

    public static ArmorItem.Type[] values() {
        return removeBody(ArmorItem.Type.values());
    }

    public static ArmorItem.Type[] removeBody(ArmorItem.Type[] types) {
        int count = 0;
        for (ArmorItem.Type type : types) {
            if (!type.name().equals("BODY")) count++;
        }
        ArmorItem.Type[] legacyTypes = new ArmorItem.Type[count];
        int index = 0;
        for (ArmorItem.Type type : types) {
            if (!type.name().equals("BODY")) legacyTypes[index++] = type;
        }
        return legacyTypes;
    }
}
