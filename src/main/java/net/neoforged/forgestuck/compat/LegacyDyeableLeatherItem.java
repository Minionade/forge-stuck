package net.neoforged.forgestuck.compat;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.core.component.DataComponents;

/** Forge 1.20.1 dyeable-item contract backed by Minecraft 1.21.1's dyed-color component. */
public interface LegacyDyeableLeatherItem {
    String TAG_COLOR = "color";
    String TAG_DISPLAY = "display";
    int DEFAULT_LEATHER_COLOR = 0xA06540;

    default boolean hasCustomColor(ItemStack stack) {
        return stack.has(DataComponents.DYED_COLOR);
    }

    default int getColor(ItemStack stack) {
        return DyedItemColor.getOrDefault(stack, DEFAULT_LEATHER_COLOR) & 0xFFFFFF;
    }

    default void clearColor(ItemStack stack) {
        stack.remove(DataComponents.DYED_COLOR);
    }

    default void setColor(ItemStack stack, int color) {
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color, true));
    }
}
