package net.neoforged.forgestuck.compat;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.IShearable;

/** Legacy implementers retain their old methods and also implement the native shearable API. */
public interface LegacyShearable extends IShearable {
    default boolean isShearable(ItemStack item, Level level, BlockPos pos) { return true; }
    default List<ItemStack> onSheared(Player player, ItemStack item, Level level, BlockPos pos, int fortune) {
        return List.of();
    }
    @Override
    default boolean isShearable(Player player, ItemStack item, Level level, BlockPos pos) {
        return isShearable(item, level, pos);
    }
    @Override
    default List<ItemStack> onSheared(Player player, ItemStack item, Level level, BlockPos pos) {
        return LegacyShearing.nativeOnSheared(this, player, item, level, pos);
    }
}
