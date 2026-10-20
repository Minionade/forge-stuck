package net.neoforged.forgestuck.compat;

import com.mojang.blaze3d.vertex.VertexConsumer;

public final class LegacyVertexConsumerShim {
    private LegacyVertexConsumerShim() {}

    public static VertexConsumer vertex(VertexConsumer consumer, double x, double y, double z) {
        return consumer.addVertex((float) x, (float) y, (float) z);
    }

    public static VertexConsumer color(VertexConsumer consumer, int argb) {
        return consumer.setColor(argb);
    }

    public static VertexConsumer color(VertexConsumer consumer, int red, int green, int blue, int alpha) {
        return consumer.setColor(red, green, blue, alpha);
    }

    public static VertexConsumer normal(VertexConsumer consumer, float x, float y, float z) {
        return consumer.setNormal(x, y, z);
    }

    public static VertexConsumer uv(VertexConsumer consumer, float u, float v) {
        return consumer.setUv(u, v);
    }

    public static VertexConsumer overlayCoords(VertexConsumer consumer, int u, int v) {
        return consumer.setUv1(u, v);
    }

    public static VertexConsumer uv2(VertexConsumer consumer, int u, int v) {
        return consumer.setUv2(u, v);
    }

    public static void endVertex(VertexConsumer consumer) {
        if (consumer instanceof LegacyQuadBakingVertexConsumer.Adapter adapter) {
            adapter.endLegacyVertex();
        } else if (consumer instanceof LegacyTransformingVertexPipeline pipeline) {
            pipeline.endLegacyVertex();
        }
    }
}
