package net.neoforged.forgestuck.compat;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.world.item.component.CustomData;

/** Adapts Forge's NBT-bearing and copy FluidStack constructors to NeoForge's data-component representation. */
public final class LegacyFluidStack {
    private static final Map<FluidStack, CustomData> OWNED = Collections.synchronizedMap(new WeakHashMap<>());

    private LegacyFluidStack() {}

    public static CompoundTag getTag(FluidStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : owned(stack, data).getUnsafe();
    }

    public static CompoundTag getOrCreateTag(FluidStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            CustomData created = CustomData.of(new CompoundTag());
            stack.set(DataComponents.CUSTOM_DATA, created);
            OWNED.put(stack, created);
            return created.getUnsafe();
        }
        return owned(stack, data).getUnsafe();
    }

    public static boolean hasTag(FluidStack stack) {
        return stack.get(DataComponents.CUSTOM_DATA) != null;
    }

    public static Fluid getRawFluid(FluidStack stack) {
        return stack.getFluid();
    }

    public static void setTag(FluidStack stack, CompoundTag tag) {
        if (tag == null) {
            stack.remove(DataComponents.CUSTOM_DATA);
            OWNED.remove(stack);
            return;
        }
        CustomData fresh = CustomData.of(tag.copy());
        stack.set(DataComponents.CUSTOM_DATA, fresh);
        OWNED.put(stack, fresh);
    }

    public static FluidStack create(FluidStack stack, int amount) {
        return stack.copyWithAmount(amount);
    }

    public static FluidStack create(Fluid fluid, int amount, CompoundTag tag) {
        FluidStack stack = new FluidStack(fluid, amount);
        if (tag != null && !tag.isEmpty()) {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag.copy()));
        }
        return stack;
    }

    private static CustomData owned(FluidStack stack, CustomData current) {
        if (OWNED.get(stack) == current) return current;
        CustomData copy = CustomData.of(current.copyTag());
        stack.set(DataComponents.CUSTOM_DATA, copy);
        OWNED.put(stack, copy);
        return copy;
    }
}
