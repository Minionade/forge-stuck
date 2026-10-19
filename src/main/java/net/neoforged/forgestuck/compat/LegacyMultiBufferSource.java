package net.neoforged.forgestuck.compat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SequencedMap;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

public final class LegacyMultiBufferSource {
    private LegacyMultiBufferSource() {}

    public static ByteBufferBuilder createByteBufferBuilder(int capacity) {
        return new ByteBufferBuilder(Math.max(capacity, 256));
    }

    public static MultiBufferSource.BufferSource immediateWithBuffers(
            Map<RenderType, ByteBufferBuilder> buffers, ByteBufferBuilder fallback) {
        SequencedMap<RenderType, ByteBufferBuilder> sequenced = new LinkedHashMap<>(buffers);
        return MultiBufferSource.immediateWithBuffers(sequenced, fallback);
    }
}
