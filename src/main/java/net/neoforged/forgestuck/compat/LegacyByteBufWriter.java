package net.neoforged.forgestuck.compat;

import java.util.Optional;
import java.util.function.BiConsumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamEncoder;

/**
 * The 1.20.1 {@code FriendlyByteBuf.Writer}: a {@link BiConsumer} that is also a 1.21 {@link StreamEncoder}, so
 * legacy lambdas can be passed to the 1.21 {@code FriendlyByteBuf} collection/optional helpers.
 */
@FunctionalInterface
public interface LegacyByteBufWriter<T> extends StreamEncoder<FriendlyByteBuf, T>, BiConsumer<FriendlyByteBuf, T> {
    @Override
    default void encode(FriendlyByteBuf buf, T value) {
        accept(buf, value);
    }

    default LegacyByteBufWriter<Optional<T>> optional() {
        return (buf, value) -> buf.writeOptional(value, this);
    }
}
