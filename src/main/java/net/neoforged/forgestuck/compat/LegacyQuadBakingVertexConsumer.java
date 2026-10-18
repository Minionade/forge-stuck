package net.neoforged.forgestuck.compat;

public final class LegacyQuadBakingVertexConsumer {
    private LegacyQuadBakingVertexConsumer() {}

    public static final class Buffered extends net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer {
        public Buffered() {}

        public net.minecraft.client.renderer.block.model.BakedQuad getQuad() {
            return bakeQuad();
        }
    }
}
