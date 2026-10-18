package net.neoforged.forgestuck.transformer.jar;

import java.util.List;
import java.util.Set;

final class FluidUtilityMappings {
    private static final String OLD = "net/minecraftforge/";
    private static final String NEW = "net/neoforged/neoforge/";
    private static final String STACK = "Lnet/minecraftforge/fluids/FluidStack;";
    private static final String HANDLER = "Lnet/minecraftforge/fluids/capability/IFluidHandler;";
    private static final String ITEM_HANDLER = "Lnet/minecraftforge/items/IItemHandler;";
    private static final String RESULT = "Lnet/minecraftforge/fluids/FluidActionResult;";

    private FluidUtilityMappings() {}

    static List<ForgeOwnerMappings.Entry> entries() {
        return List.of(
                entry("common/util/Lazy", Set.of(
                        "get()Ljava/lang/Object;",
                        "of(Ljava/util/function/Supplier;)Lnet/minecraftforge/common/util/Lazy;")),
                entry("fluids/FluidActionResult", Set.of(
                        "getResult()Lnet/minecraft/world/item/ItemStack;",
                        "isSuccess()Z")),
                entry("fluids/FluidUtil", Set.of(
                        "getFluidContained(Lnet/minecraft/world/item/ItemStack;)Ljava/util/Optional;",
                        "tryEmptyContainerAndStow(Lnet/minecraft/world/item/ItemStack;" + HANDLER + ITEM_HANDLER
                                + "ILnet/minecraft/world/entity/player/Player;Z)" + RESULT,
                        "tryFillContainerAndStow(Lnet/minecraft/world/item/ItemStack;" + HANDLER + ITEM_HANDLER
                                + "ILnet/minecraft/world/entity/player/Player;Z)" + RESULT,
                        "tryFluidTransfer(" + HANDLER + HANDLER + STACK + "Z)" + STACK,
                        "tryPickUpFluid(Lnet/minecraft/world/item/ItemStack;"
                                + "Lnet/minecraft/world/entity/player/Player;"
                                + "Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                                + "Lnet/minecraft/core/Direction;)" + RESULT,
                        "tryPlaceFluid(Lnet/minecraft/world/entity/player/Player;"
                                + "Lnet/minecraft/world/level/Level;Lnet/minecraft/world/InteractionHand;"
                                + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;" + STACK + ")"
                                + RESULT)),
                entry("fluids/capability/templates/EmptyFluidHandler", Set.of(
                        "INSTANCELnet/minecraftforge/fluids/capability/templates/EmptyFluidHandler;")),
                entry("fluids/capability/wrappers/FluidBucketWrapper", Set.of(
                        "<init>(Lnet/minecraft/world/item/ItemStack;)V")));
    }

    private static ForgeOwnerMappings.Entry entry(String suffix, Set<String> members) {
        return new ForgeOwnerMappings.Entry(OLD + suffix, NEW + suffix, members);
    }
}
