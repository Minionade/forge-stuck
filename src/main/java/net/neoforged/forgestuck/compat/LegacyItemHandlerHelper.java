package net.neoforged.forgestuck.compat;

import net.minecraft.world.item.ItemStack;

public final class LegacyItemHandlerHelper {
    private LegacyItemHandlerHelper() {}

    public static boolean canItemStacksStack(ItemStack first, ItemStack second) {
        return !first.isEmpty() && ItemStack.isSameItemSameComponents(first, second);
    }

    public static ItemStack copyStackWithSize(ItemStack stack, int size) {
        return size == 0 ? ItemStack.EMPTY : stack.copyWithCount(size);
    }
}
