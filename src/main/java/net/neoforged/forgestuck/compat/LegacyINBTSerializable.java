package net.neoforged.forgestuck.compat;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;

/** Preserves Forge's no-context serialization hooks while implementing NeoForge's registry-aware interface. */
public interface LegacyINBTSerializable<T extends Tag> extends net.neoforged.neoforge.common.util.INBTSerializable<T> {
    default T serializeNBT() {
        return null;
    }

    @Override
    default T serializeNBT(HolderLookup.Provider provider) {
        return serializeNBT();
    }

    default void deserializeNBT(T tag) {}

    @Override
    default void deserializeNBT(HolderLookup.Provider provider, T tag) {
        deserializeNBT(tag);
    }
}
