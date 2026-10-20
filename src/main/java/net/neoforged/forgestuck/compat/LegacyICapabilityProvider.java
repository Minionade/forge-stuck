package net.neoforged.forgestuck.compat;

import net.minecraft.core.Direction;

/** Compatibility contract for Forge capability providers. */
public interface LegacyICapabilityProvider {
    default <T> LazyOptional<T> getCapability(LegacyCapability<T> capability) {
        return getCapability(capability, null);
    }

    default <T> LazyOptional<T> getCapability(LegacyCapability<T> capability, Direction side) {
        return LazyOptional.empty();
    }
}
