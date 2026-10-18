package net.neoforged.forgestuck.compat;

import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;

public final class LegacyAdditionalModelEvent {
    private LegacyAdditionalModelEvent() {}

    public static void register(ModelEvent.RegisterAdditional event, ResourceLocation location) {
        event.register(ModelResourceLocation.standalone(location));
    }
}
