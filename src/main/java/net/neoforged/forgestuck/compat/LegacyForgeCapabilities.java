package net.neoforged.forgestuck.compat;

import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.items.IItemHandler;

public final class LegacyForgeCapabilities {
    public static final LegacyCapability<IEnergyStorage> ENERGY =
            LegacyCapabilityManager.forItemAndBlockType(IEnergyStorage.class, Capabilities.EnergyStorage.ITEM,
                    Capabilities.EnergyStorage.BLOCK);
    public static final LegacyCapability<IFluidHandler> FLUID_HANDLER =
            LegacyCapabilityManager.forBlockType(IFluidHandler.class, Capabilities.FluidHandler.BLOCK);
    public static final LegacyCapability<IFluidHandlerItem> FLUID_HANDLER_ITEM =
            LegacyCapabilityManager.forItemType(IFluidHandlerItem.class, Capabilities.FluidHandler.ITEM);
    public static final LegacyCapability<IItemHandler> ITEM_HANDLER =
            LegacyCapabilityManager.forItemAndBlockType(IItemHandler.class, Capabilities.ItemHandler.ITEM,
                    Capabilities.ItemHandler.BLOCK);

    private LegacyForgeCapabilities() {}
}
