package net.neoforged.forgestuck.compat;

import java.util.Optional;
import java.util.function.Function;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamDecoder;

/**
 * The 1.20.1 {@code FriendlyByteBuf.Reader}: a {@link Function} that is also a 1.21 {@link StreamDecoder}, so
 * legacy lambdas can be passed to the 1.21 {@code FriendlyByteBuf} collection/optional helpers.
 */
@FunctionalInterface
public interface LegacyByteBufReader<T> extends StreamDecoder<FriendlyByteBuf, T>, Function<FriendlyByteBuf, T> {
    @Override
    default T decode(FriendlyByteBuf buf) {
        return apply(buf);
    }

    default LegacyByteBufReader<Optional<T>> optional() {
        return buf -> buf.readOptional(this);
    }
}
