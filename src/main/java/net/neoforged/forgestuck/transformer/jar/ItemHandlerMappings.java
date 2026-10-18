package net.neoforged.forgestuck.transformer.jar;

import java.util.List;
import java.util.Set;

final class ItemHandlerMappings {
    private ItemHandlerMappings() {}

    static List<ForgeOwnerMappings.Entry> entries() {
        String itemHandler = "Lnet/minecraftforge/items/IItemHandler;";
        String itemStack = "Lnet/minecraft/world/item/ItemStack;";
        return List.of(
                new ForgeOwnerMappings.Entry(
                        "net/minecraftforge/items/ItemHandlerHelper",
                        "net/neoforged/neoforge/items/ItemHandlerHelper",
                        Set.of(
                                "giveItemToPlayer(Lnet/minecraft/world/entity/player/Player;" + itemStack + ")V",
                                "giveItemToPlayer(Lnet/minecraft/world/entity/player/Player;" + itemStack + "I)V",
                                "insertItem(" + itemHandler + itemStack + "Z)" + itemStack,
                                "insertItemStacked(" + itemHandler + itemStack + "Z)" + itemStack)),
                new ForgeOwnerMappings.Entry(
                        "net/minecraftforge/items/ItemStackHandler",
                        "net/neoforged/neoforge/items/ItemStackHandler",
                        Set.of(
                                "<init>(I)V",
                                "extractItem(IIZ)" + itemStack,
                                "insertItem(I" + itemStack + "Z)" + itemStack,
                                "setStackInSlot(I" + itemStack + ")V")),
                new ForgeOwnerMappings.Entry(
                        "net/minecraftforge/items/IItemHandlerModifiable",
                        "net/neoforged/neoforge/items/IItemHandlerModifiable",
                        Set.of(
                                "getSlots()I",
                                "getStackInSlot(I)" + itemStack,
                                "setStackInSlot(I" + itemStack + ")V")),
                new ForgeOwnerMappings.Entry(
                        "net/minecraftforge/items/wrapper/InvWrapper",
                        "net/neoforged/neoforge/items/wrapper/InvWrapper",
                        Set.of(
                                "<init>(Lnet/minecraft/world/Container;)V",
                                "extractItem(IIZ)" + itemStack,
                                "insertItem(I" + itemStack + "Z)" + itemStack,
                                "isItemValid(I" + itemStack + ")Z")));
    }
}
