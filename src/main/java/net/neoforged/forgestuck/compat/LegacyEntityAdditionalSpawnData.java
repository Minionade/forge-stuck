package net.neoforged.forgestuck.compat;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

/**
 * Stand-in for Forge's {@code IEntityAdditionalSpawnData}. Legacy implementers define the {@link FriendlyByteBuf}
 * methods; the NeoForge registry-buffer methods delegate to them.
 */
public interface LegacyEntityAdditionalSpawnData extends IEntityWithComplexSpawn {
    void writeSpawnData(FriendlyByteBuf buffer);

    void readSpawnData(FriendlyByteBuf additionalData);

    @Override
    default void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        writeSpawnData((FriendlyByteBuf) buffer);
    }

    @Override
    default void readSpawnData(RegistryFriendlyByteBuf additionalData) {
        readSpawnData((FriendlyByteBuf) additionalData);
    }
}
