package net.neoforged.forgestuck.compat;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.world.item.component.CustomData;

/** Adapts Forge's NBT-bearing FluidStack constructor to NeoForge's data-component representation. */
public final class LegacyFluidStack {
    private LegacyFluidStack() {}

    public static FluidStack create(Fluid fluid, int amount, CompoundTag tag) {
        FluidStack stack = new FluidStack(fluid, amount);
        if (tag != null && !tag.isEmpty()) {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag.copy()));
        }
        return stack;
    }
}
