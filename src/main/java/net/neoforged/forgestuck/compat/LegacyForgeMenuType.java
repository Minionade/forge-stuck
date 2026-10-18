package net.neoforged.forgestuck.compat;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

/** Adapts Forge menu factories to NeoForge's registry-aware buffer callback. */
public interface LegacyForgeMenuType {
    static <T extends AbstractContainerMenu> MenuType<T> create(LegacyContainerFactory<T> factory) {
        return IMenuTypeExtension.create((windowId, inventory, data) -> factory.create(windowId, inventory, data));
    }
}
