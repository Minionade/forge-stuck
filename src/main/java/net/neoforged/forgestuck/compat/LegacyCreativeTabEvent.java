package net.neoforged.forgestuck.compat;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

public final class LegacyCreativeTabEvent {
    private LegacyCreativeTabEvent() {}

    public static void accept(BuildCreativeModeTabContentsEvent event, ItemStack stack) {
        event.accept(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
    }
}
