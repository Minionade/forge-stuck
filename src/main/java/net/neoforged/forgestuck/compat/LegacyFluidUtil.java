package net.neoforged.forgestuck.compat;

import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

/** Adapts Forge's lazy item-handler lookup to NeoForge's optional handler API. */
public final class LegacyFluidUtil {
    private LegacyFluidUtil() {}

    public static LazyOptional<IFluidHandlerItem> getFluidHandler(ItemStack stack) {
        Optional<IFluidHandlerItem> handler = FluidUtil.getFluidHandler(stack);
        return handler.<LazyOptional<IFluidHandlerItem>>map(value -> LazyOptional.of(() -> value))
                .orElseGet(LazyOptional::empty);
    }
}
