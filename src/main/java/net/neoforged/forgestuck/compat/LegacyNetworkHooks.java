package net.neoforged.forgestuck.compat;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;

public final class LegacyNetworkHooks {
    private LegacyNetworkHooks() {}

    public static void openScreen(ServerPlayer player, MenuProvider menuProvider) {
        player.openMenu(menuProvider);
    }

    public static void openScreen(ServerPlayer player, MenuProvider menuProvider,
            Consumer<RegistryFriendlyByteBuf> extraDataWriter) {
        player.openMenu(menuProvider, extraDataWriter);
    }

    public static void openScreen(ServerPlayer player, MenuProvider menuProvider, BlockPos pos) {
        player.openMenu(menuProvider, pos);
    }
}
