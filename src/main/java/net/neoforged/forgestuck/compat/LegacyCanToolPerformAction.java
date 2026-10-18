package net.neoforged.forgestuck.compat;

import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.common.loot.CanItemPerformAbility;

public final class LegacyCanToolPerformAction {
    private LegacyCanToolPerformAction() {}

    public static LootItemCondition.Builder canToolPerformAction(ItemAbility action) {
        return CanItemPerformAbility.canItemPerformAbility(action);
    }
}
