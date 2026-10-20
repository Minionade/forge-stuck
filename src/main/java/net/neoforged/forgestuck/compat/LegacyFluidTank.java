package net.neoforged.forgestuck.compat;

import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public final class LegacyFluidTank {
    private LegacyFluidTank() {}

    public static void setFluid(FluidTank tank, FluidStack fluid) {
        tank.setFluid(fluid == null ? FluidStack.EMPTY : fluid);
    }

    public static FluidTank readFromNBT(FluidTank tank, CompoundTag nbt) {
        if (nbt.contains("Fluid", CompoundTag.TAG_COMPOUND)) {
            tank.setFluid(LegacyFluidStack.loadFluidStackFromNBT(nbt.getCompound("Fluid")));
        }
        return tank;
    }

    public static CompoundTag writeToNBT(FluidTank tank, CompoundTag nbt) {
        FluidStack fluid = tank.getFluid();
        if (!fluid.isEmpty()) {
            nbt.put("Fluid", LegacyFluidStack.writeToNBT(fluid, new CompoundTag()));
        }
        return nbt;
    }
}
