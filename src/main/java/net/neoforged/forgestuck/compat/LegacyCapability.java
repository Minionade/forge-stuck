package net.neoforged.forgestuck.compat;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.EntityCapability;
import net.neoforged.neoforge.capabilities.ItemCapability;

public final class LegacyCapability<T> {
    private final Class<T> type;
    private volatile EntityCapability<T, Void> entityCapability;
    private volatile ItemCapability<T, Void> itemCapability;
    private volatile BlockCapability<T, Direction> blockCapability;

    LegacyCapability(Class<T> type) {
        this.type = type;
    }

    Class<T> type() {
        return type;
    }

    void bind(EntityCapability<T, Void> capability) {
        if (entityCapability != null) {
            throw new IllegalStateException("Legacy capability already registered: " + type.getName());
        }
        entityCapability = capability;
    }

    void bindItem(ItemCapability<T, Void> capability) {
        if (itemCapability != null) {
            throw new IllegalStateException("Legacy item capability already registered: " + type.getName());
        }
        itemCapability = capability;
    }

    void bindBlock(BlockCapability<T, Direction> capability) {
        if (blockCapability != null) {
            throw new IllegalStateException("Legacy block capability already registered: " + type.getName());
        }
        blockCapability = capability;
    }

    T getCapability(Entity entity) {
        EntityCapability<T, Void> capability = entityCapability;
        return capability == null ? null : capability.getCapability(entity, null);
    }

    T getItemCapability(ItemStack stack) {
        ItemCapability<T, Void> capability = itemCapability;
        return capability == null ? null : stack.getCapability(capability);
    }

    T getBlockCapability(BlockEntity blockEntity, Direction side) {
        BlockCapability<T, Direction> capability = blockCapability;
        Level level = blockEntity.getLevel();
        return capability == null || level == null ? null
                : capability.getCapability(level, blockEntity.getBlockPos(), blockEntity.getBlockState(),
                        blockEntity, side);
    }

    public String getName() {
        return type.getName();
    }

    public LazyOptional<T> orEmpty(LegacyCapability<T> capability, LazyOptional<T> optional) {
        return this == capability ? optional : LazyOptional.empty();
    }
}
