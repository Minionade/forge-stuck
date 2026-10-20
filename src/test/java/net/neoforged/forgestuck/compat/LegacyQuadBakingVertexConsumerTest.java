package net.neoforged.forgestuck.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.math.Transformation;
import net.minecraft.client.renderer.block.model.BakedQuad;
import org.junit.jupiter.api.Test;

class LegacyQuadBakingVertexConsumerTest {
    @Test
    void bakesAfterTheFourthLegacyVertexIsCompleted() {
        class RecordingAdapterTest extends LegacyQuadBakingVertexConsumer.Adapter {
            private int bakedQuads;

            RecordingAdapterTest() {
                super(quad -> {});
            }

            @Override
            public BakedQuad bakeQuad() {
                bakedQuads++;
                return null;
            }
        }

        RecordingAdapterTest adapter = new RecordingAdapterTest();
        for (int vertex = 0; vertex < 4; vertex++) {
            adapter.addVertex(vertex, 0, 0);
            assertEquals(0, adapter.bakedQuads);
            LegacyVertexConsumerShim.endVertex(adapter);
            assertEquals(vertex == 3 ? 1 : 0, adapter.bakedQuads);
        }
    }

    @Test
    void completesVerticesWhenWrappedByTransformingPipeline() {
        class RecordingAdapterTest extends LegacyQuadBakingVertexConsumer.Adapter {
            private int bakedQuads;

            RecordingAdapterTest() {
                super(quad -> {});
            }

            @Override
            public BakedQuad bakeQuad() {
                bakedQuads++;
                return null;
            }
        }

        RecordingAdapterTest adapter = new RecordingAdapterTest();
        LegacyTransformingVertexPipeline pipeline =
                new LegacyTransformingVertexPipeline(adapter, Transformation.identity());
        for (int vertex = 0; vertex < 4; vertex++) {
            pipeline.addVertex(vertex, 0, 0);
            LegacyVertexConsumerShim.endVertex(pipeline);
        }

        assertEquals(1, adapter.bakedQuads);
    }
}
