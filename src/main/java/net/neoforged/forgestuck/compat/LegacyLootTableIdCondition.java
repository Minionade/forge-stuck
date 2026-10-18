package net.neoforged.forgestuck.compat;

import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.LootTableIdCondition;

public final class LegacyLootTableIdCondition {
    private LegacyLootTableIdCondition() {}

    public static LootItemCondition m_6409_(LootTableIdCondition.Builder builder) {
        return builder.build();
    }
}
