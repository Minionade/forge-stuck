package net.neoforged.forgestuck.compat;

import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;

/** Native deferred eggs provide registration, colors and dispensers; these overloads preserve old NBT lookups. */
public class LegacyForgeSpawnEggItem extends DeferredSpawnEggItem {
    public LegacyForgeSpawnEggItem(Supplier<? extends EntityType<? extends Mob>> type,
                                   int backgroundColor, int highlightColor, Item.Properties properties) {
        super(type, backgroundColor, highlightColor, properties);
    }

    public static SpawnEggItem fromEntityType(EntityType<?> type) {
        // NeoForge's byId consults the deferred egg map before the vanilla map.
        return SpawnEggItem.byId(type);
    }

    @Override
    public EntityType<?> getType(ItemStack stack) {
        CompoundTag legacyTag = new CompoundTag();
        CustomData entityData = stack.get(DataComponents.ENTITY_DATA);
        if (entityData != null) legacyTag.put("EntityTag", entityData.copyTag());
        // Preserve virtual dispatch to legacy getType(CompoundTag) overrides during native egg use.
        return getType(legacyTag);
    }

    public EntityType<?> getType(CompoundTag tag) {
        if (tag != null && tag.contains("EntityTag", Tag.TAG_COMPOUND)) {
            CompoundTag entity = tag.getCompound("EntityTag");
            if (entity.contains("id", Tag.TAG_STRING)) {
                ResourceLocation id = ResourceLocation.tryParse(entity.getString("id"));
                if (id != null) {
                    EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
                    if (type != null) return type;
                }
            }
        }
        return getDefaultType();
    }

    public boolean spawnsEntity(CompoundTag tag, EntityType<?> type) { return getType(tag) == type; }

    // Available during the owner-remap pass, before SRG calls are renamed to their named overloads.
    public int m_43211_(int layer) { return getColor(layer); }
    public EntityType<?> m_43228_(CompoundTag tag) { return getType(tag); }
    public boolean m_43230_(CompoundTag tag, EntityType<?> type) { return spawnsEntity(tag, type); }
}
