package net.neoforged.forgestuck.transformer.jar;

import java.util.List;
import java.util.Set;

final class SpawnEggMappings {
    private SpawnEggMappings() {}
    static List<ForgeOwnerMappings.Entry> entries() {
        String type = "Lnet/minecraft/world/entity/EntityType;";
        String tag = "Lnet/minecraft/nbt/CompoundTag;";
        return List.of(new ForgeOwnerMappings.Entry("net/minecraftforge/common/ForgeSpawnEggItem",
                "net/neoforged/forgestuck/compat/LegacyForgeSpawnEggItem", Set.of(
                "<init>(Ljava/util/function/Supplier;IILnet/minecraft/world/item/Item$Properties;)V",
                "fromEntityType(" + type + ")Lnet/minecraft/world/item/SpawnEggItem;",
                "getDefaultType()" + type,
                "createDispenseBehavior()Lnet/minecraft/core/dispenser/DispenseItemBehavior;",
                "getType(" + tag + ")" + type,
                "getType(Lnet/minecraft/world/item/ItemStack;)" + type,
                "m_43228_(" + tag + ")" + type,
                "spawnsEntity(" + tag + type + ")Z",
                "m_43230_(" + tag + type + ")Z",
                "getColor(I)I", "m_43211_(I)I")));
    }
}
