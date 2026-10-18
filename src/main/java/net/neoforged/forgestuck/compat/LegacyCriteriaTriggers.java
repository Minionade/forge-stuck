package net.neoforged.forgestuck.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.resources.ResourceLocation;

/** Adapts Forge's one-argument criterion registration API to the keyed 1.21.1 registration method. */
public final class LegacyCriteriaTriggers {
    private LegacyCriteriaTriggers() {}

    public static <T extends CriterionTrigger<?>> T register(T trigger) {
        return CriteriaTriggers.register(legacyId(trigger).toString(), trigger);
    }

    private static ResourceLocation legacyId(CriterionTrigger<?> trigger) {
        Field id = null;
        for (Class<?> type = trigger.getClass(); type != null && id == null; type = type.getSuperclass()) {
            try {
                Field candidate = type.getDeclaredField("ID");
                if (Modifier.isStatic(candidate.getModifiers())) id = candidate;
            } catch (NoSuchFieldException ignored) {
                // The trigger may inherit its ID from a base class.
            }
        }
        if (id == null) {
            throw new IllegalStateException("Legacy criterion trigger " + trigger.getClass().getName()
                    + " has no static ID field");
        }
        try {
            if (!id.trySetAccessible()) {
                throw new IllegalStateException("Legacy criterion trigger ID field is inaccessible: "
                        + trigger.getClass().getName());
            }
            Object value = id.get(null);
            if (value instanceof ResourceLocation resourceLocation) return resourceLocation;
            throw new IllegalStateException("Legacy criterion trigger " + trigger.getClass().getName()
                    + " has a non-ResourceLocation ID field");
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Could not read legacy criterion trigger ID field from "
                    + trigger.getClass().getName(), e);
        }
    }
}
