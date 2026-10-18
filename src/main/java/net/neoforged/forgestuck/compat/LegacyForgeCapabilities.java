package net.neoforged.forgestuck.compat;

import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.items.IItemHandler;

public final class LegacyForgeCapabilities {
    public static final LegacyCapability<IEnergyStorage> ENERGY =
            LegacyCapabilityManager.forType(IEnergyStorage.class);
    public static final LegacyCapability<IFluidHandler> FLUID_HANDLER =
            LegacyCapabilityManager.forType(IFluidHandler.class);
    public static final LegacyCapability<IFluidHandlerItem> FLUID_HANDLER_ITEM =
            LegacyCapabilityManager.forType(IFluidHandlerItem.class);
    public static final LegacyCapability<IItemHandler> ITEM_HANDLER =
            LegacyCapabilityManager.forType(IItemHandler.class);

    private LegacyForgeCapabilities() {}
}
