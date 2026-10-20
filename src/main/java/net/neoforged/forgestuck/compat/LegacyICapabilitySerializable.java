package net.neoforged.forgestuck.compat;

import net.minecraft.nbt.CompoundTag;

/** Forge's combined capability-provider and persistent-NBT contract. */
public interface LegacyICapabilitySerializable
        extends LegacyICapabilityProvider, LegacyINBTSerializable<CompoundTag> {}
