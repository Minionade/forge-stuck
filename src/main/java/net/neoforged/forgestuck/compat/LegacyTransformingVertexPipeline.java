package net.neoforged.forgestuck.compat;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Transformation;
import net.neoforged.neoforge.client.model.pipeline.TransformingVertexPipeline;

public final class LegacyTransformingVertexPipeline extends TransformingVertexPipeline {
    private final LegacyQuadBakingVertexConsumer.Adapter quadBaker;

    public LegacyTransformingVertexPipeline(VertexConsumer parent, Transformation transformation) {
        super(parent, transformation);
        this.quadBaker = parent instanceof LegacyQuadBakingVertexConsumer.Adapter adapter ? adapter : null;
    }

    public void endLegacyVertex() {
        if (quadBaker != null) {
            quadBaker.endLegacyVertex();
        }
    }
}
