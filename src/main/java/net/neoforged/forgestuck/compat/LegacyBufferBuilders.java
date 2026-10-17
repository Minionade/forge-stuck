package net.neoforged.forgestuck.compat;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

/** Target of the rewritten 1.20 {@code new BufferBuilder(int)}; yields a placeholder builder so the holder links. */
public final class LegacyBufferBuilders {
    private LegacyBufferBuilders() {}

    public static BufferBuilder create(int capacity) {
        return new BufferBuilder(new ByteBufferBuilder(Math.max(capacity, 256)), VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION);
    }
}
