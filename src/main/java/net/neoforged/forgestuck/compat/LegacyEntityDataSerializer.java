package net.neoforged.forgestuck.compat;

import java.util.Optional;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataSerializer;

/** Adapts Forge's FriendlyByteBuf writer/reader factories to the 1.21 EntityDataSerializer codec. */
public final class LegacyEntityDataSerializer {
    private LegacyEntityDataSerializer() {}

    public static <T> EntityDataSerializer<T> create(
            LegacyByteBufWriter<T> writer, LegacyByteBufReader<T> reader) {
        return EntityDataSerializer.forValueType(StreamCodec.of(writer, reader));
    }

    public static <T> EntityDataSerializer<Optional<T>> optional(
            LegacyByteBufWriter<T> writer, LegacyByteBufReader<T> reader) {
        return EntityDataSerializer.forValueType(StreamCodec.of(writer.optional(), reader.optional()));
    }
}
