package net.neoforged.forgestuck.compat;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class LegacyAddLayersEvent {
    private LegacyAddLayersEvent() {}

    public static LivingEntityRenderer<?, ?> getRenderer(EntityRenderersEvent.AddLayers event,
            EntityType<?> entityType) {
        return (LivingEntityRenderer<?, ?>) event.getRenderer(entityType);
    }
}
