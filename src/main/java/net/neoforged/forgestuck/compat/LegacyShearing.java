package net.neoforged.forgestuck.compat;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.IShearable;

/** Dispatches both API shapes without requiring native entities to implement a legacy subtype. */
public final class LegacyShearing {
    private LegacyShearing() {}
    public static boolean isShearable(IShearable target, ItemStack item, Level level, BlockPos pos) {
        return target instanceof LegacyShearable legacy ? legacy.isShearable(item, level, pos)
                : target.isShearable(null, item, level, pos);
    }
    public static List<ItemStack> onSheared(IShearable target, Player player, ItemStack item,
                                          Level level, BlockPos pos, int fortune) {
        return target instanceof LegacyShearable legacy ? legacy.onSheared(player, item, level, pos, fortune)
                : target.onSheared(player, item, level, pos);
    }
    public static boolean nativeIsShearable(LegacyShearable target, Player player,
                                            ItemStack item, Level level, BlockPos pos) {
        return target.isShearable(item, level, pos);
    }
    public static List<ItemStack> nativeOnSheared(LegacyShearable target, Player player,
                                                ItemStack item, Level level, BlockPos pos) {
        var fortune = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.FORTUNE);
        int amount = EnchantmentHelper.getItemEnchantmentLevel(fortune, item);
        return target.onSheared(player, item, level, pos, amount);
    }
}
