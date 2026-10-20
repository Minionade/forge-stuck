package net.neoforged.forgestuck.compat;

import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.block.state.BlockState;

public final class LegacyTierSortingRegistry {
    private static final Map<ResourceLocation, Tier> TIERS_BY_NAME = Map.of(
            id("wood"), Tiers.WOOD,
            id("gold"), Tiers.GOLD,
            id("stone"), Tiers.STONE,
            id("iron"), Tiers.IRON,
            id("diamond"), Tiers.DIAMOND,
            id("netherite"), Tiers.NETHERITE);
    private static final Map<Tier, ResourceLocation> NAMES_BY_TIER = Map.of(
            Tiers.WOOD, id("wood"),
            Tiers.GOLD, id("gold"),
            Tiers.STONE, id("stone"),
            Tiers.IRON, id("iron"),
            Tiers.DIAMOND, id("diamond"),
            Tiers.NETHERITE, id("netherite"));
    private static final List<Tier> SORTED_TIERS =
            List.of(Tiers.WOOD, Tiers.GOLD, Tiers.STONE, Tiers.IRON, Tiers.DIAMOND, Tiers.NETHERITE);

    private LegacyTierSortingRegistry() {}

    public static Tier byName(ResourceLocation name) {
        return TIERS_BY_NAME.get(name);
    }

    public static ResourceLocation getName(Tier tier) {
        return NAMES_BY_TIER.get(tier);
    }

    public static List<Tier> getSortedTiers() {
        return SORTED_TIERS;
    }

    public static boolean isCorrectTierForDrops(Tier tier, BlockState state) {
        return !state.is(tier.getIncorrectBlocksForDrops());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }
}
