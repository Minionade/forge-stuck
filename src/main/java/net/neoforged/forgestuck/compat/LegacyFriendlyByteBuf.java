package net.neoforged.forgestuck.compat;

import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class LegacyFriendlyByteBuf {
    private LegacyFriendlyByteBuf() {}

    public static <T> void writeId(FriendlyByteBuf buffer, IdMap<T> idMap, T value) {
        buffer.writeVarInt(idMap.getId(value));
    }

    public static <T> void writeRegistryId(FriendlyByteBuf buffer, IForgeRegistry<T> registry, T value) {
        if (registry == null || value == null) {
            throw new NullPointerException("Cannot write a null legacy registry or entry");
        }
        if (!registry.containsValue(value)) {
            throw new IllegalArgumentException("Cannot find " + value + " in " + registry.getRegistryName());
        }
        buffer.writeResourceLocation(registry.getRegistryName());
        buffer.writeVarInt(registry.getId(value));
    }

    @SuppressWarnings("unchecked")
    public static <T> T readRegistryId(FriendlyByteBuf buffer) {
        ResourceLocation registryName = buffer.readResourceLocation();
        IForgeRegistry<?> registry = ForgeRegistries.byName(registryName);
        int id = buffer.readVarInt();
        Object value = registry.byId(id);
        if (value == null) {
            throw new IllegalArgumentException("Unknown registry id " + id + " in " + registryName);
        }
        return (T) value;
    }

    public static FriendlyByteBuf writeItem(FriendlyByteBuf buffer, ItemStack stack) {
        ItemStack.OPTIONAL_STREAM_CODEC.encode(registryBuffer(buffer), stack);
        return buffer;
    }

    public static ItemStack readItem(FriendlyByteBuf buffer) {
        return ItemStack.OPTIONAL_STREAM_CODEC.decode(registryBuffer(buffer));
    }

    private static RegistryFriendlyByteBuf registryBuffer(FriendlyByteBuf buffer) {
        if (buffer instanceof RegistryFriendlyByteBuf registryBuffer) {
            return registryBuffer;
        }
        throw new IllegalArgumentException("Legacy item stack serialization requires a registry-friendly buffer");
    }
}
