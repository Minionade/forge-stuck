package net.neoforged.forgestuck.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

public final class LegacyTranslatableComponentShim {
    private LegacyTranslatableComponentShim() {}

    public static MutableComponent translatable(String key, Object... arguments) {
        Object[] compatibleArguments = arguments.clone();
        for (int index = 0; index < compatibleArguments.length; index++) {
            if (compatibleArguments[index] instanceof ResourceLocation location) {
                compatibleArguments[index] = location.toString();
            }
        }
        return Component.translatable(key, compatibleArguments);
    }
}
