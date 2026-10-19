package net.neoforged.forgestuck.compat;

import net.minecraft.world.level.storage.loot.LootDataType;

public final class LegacyLootDataType {
    private LegacyLootDataType() {}

    public static String directory(LootDataType<?> type) {
        if (type == LootDataType.TABLE) return "loot_tables";
        if (type == LootDataType.MODIFIER) return "item_modifiers";
        if (type == LootDataType.PREDICATE) return "predicates";
        throw new IllegalArgumentException("No legacy loot directory for " + type);
    }
}
