package net.neoforged.forgestuck.compat;

import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;

public final class LegacyQuadBakingVertexConsumer {
    private LegacyQuadBakingVertexConsumer() {}

    public static class Adapter extends QuadBakingVertexConsumer {
        private final Consumer<BakedQuad> output;
        private int vertices;

        public Adapter(Consumer<BakedQuad> output) {
            this.output = Objects.requireNonNull(output, "output");
        }

        @Override
        public com.mojang.blaze3d.vertex.VertexConsumer addVertex(float x, float y, float z) {
            super.addVertex(x, y, z);
            if (++vertices == 4) {
                output.accept(bakeQuad());
                vertices = 0;
            }
            return this;
        }
    }

    public static final class Buffered extends Adapter {
        private final BakedQuad[] output;

        public Buffered() {
            this(new BakedQuad[1]);
        }

        private Buffered(BakedQuad[] output) {
            super(quad -> output[0] = quad);
            this.output = output;
        }

        public BakedQuad getQuad() {
            BakedQuad quad = Objects.requireNonNull(output[0], "No quad has been emitted");
            output[0] = null;
            return quad;
        }
    }
}
