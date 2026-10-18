package net.neoforged.forgestuck.compat;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

/** Preserves Forge's FriendlyByteBuf menu factory signature on NeoForge. */
@FunctionalInterface
public interface LegacyContainerFactory<T extends AbstractContainerMenu> extends MenuType.MenuSupplier<T> {
    T create(int windowId, Inventory inventory, FriendlyByteBuf data);

    @Override
    default T create(int windowId, Inventory inventory) {
        throw new UnsupportedOperationException("Menu creation requires the network buffer");
    }
}
