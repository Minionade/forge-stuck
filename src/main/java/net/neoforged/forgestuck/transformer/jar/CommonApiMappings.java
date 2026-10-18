package net.neoforged.forgestuck.transformer.jar;

import java.util.List;
import java.util.Set;

final class CommonApiMappings {
    private CommonApiMappings() {}

    static List<ForgeOwnerMappings.Entry> entries() {
        String loot = "net/minecraftforge/common/loot/LootTableIdCondition";
        return List.of(
                new ForgeOwnerMappings.Entry("net/minecraftforge/common/TierSortingRegistry",
                        "net/neoforged/forgestuck/compat/LegacyTierSortingRegistry",
                        Set.of("isCorrectTierForDrops(Lnet/minecraft/world/item/Tier;"
                                + "Lnet/minecraft/world/level/block/state/BlockState;)Z")),
                new ForgeOwnerMappings.Entry("net/minecraftforge/common/BasicItemListing",
                        "net/neoforged/neoforge/common/BasicItemListing",
                        Set.of("<init>(Lnet/minecraft/world/item/ItemStack;"
                                + "Lnet/minecraft/world/item/ItemStack;IIF)V")),
                new ForgeOwnerMappings.Entry(loot, "net/neoforged/neoforge/common/loot/LootTableIdCondition",
                        Set.of("builder(Lnet/minecraft/resources/ResourceLocation;)"
                                + "Lnet/minecraftforge/common/loot/LootTableIdCondition$Builder;")),
                new ForgeOwnerMappings.Entry(loot + "$Builder",
                        "net/neoforged/neoforge/common/loot/LootTableIdCondition$Builder", Set.of()),
                new ForgeOwnerMappings.Entry(
                        "net/minecraftforge/common/data/DatapackBuiltinEntriesProvider",
                        "net/neoforged/neoforge/common/data/DatapackBuiltinEntriesProvider",
                        Set.of("<init>(Lnet/minecraft/data/PackOutput;"
                                        + "Ljava/util/concurrent/CompletableFuture;"
                                        + "Lnet/minecraft/core/RegistrySetBuilder;Ljava/util/Set;)V",
                                "getRegistryProvider()Ljava/util/concurrent/CompletableFuture;")),
                new ForgeOwnerMappings.Entry("net/minecraftforge/registries/GameData",
                        "net/neoforged/neoforge/registries/GameData",
                        Set.of("getBlockStateIDMap()Lnet/minecraft/core/IdMapper;")),
                new ForgeOwnerMappings.Entry("net/minecraftforge/registries/holdersets/AndHolderSet",
                        "net/neoforged/neoforge/registries/holdersets/AndHolderSet",
                        Set.of("<init>(Ljava/util/List;)V")),
                new ForgeOwnerMappings.Entry("net/minecraftforge/registries/holdersets/OrHolderSet",
                        "net/neoforged/neoforge/registries/holdersets/OrHolderSet",
                        Set.of("<init>(Ljava/util/List;)V")),
                new ForgeOwnerMappings.Entry("net/minecraftforge/registries/holdersets/NotHolderSet",
                        "net/neoforged/neoforge/registries/holdersets/NotHolderSet",
                        Set.of("<init>(Lnet/minecraft/core/HolderLookup$RegistryLookup;"
                                + "Lnet/minecraft/core/HolderSet;)V")));
    }
}
