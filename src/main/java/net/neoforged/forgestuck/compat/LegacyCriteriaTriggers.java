package net.neoforged.forgestuck.compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
        for (String name : new String[] {"getId", "m_7295_"}) {
            try {
                Method method = trigger.getClass().getMethod(name);
                Object id = method.invoke(trigger);
                if (id instanceof ResourceLocation resourceLocation) return resourceLocation;
                throw new IllegalStateException("Legacy criterion trigger " + trigger.getClass().getName()
                        + " returned a non-ResourceLocation id from " + name);
            } catch (NoSuchMethodException ignored) {
                // Try the alternate source-level or SRG method name.
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("Could not read legacy criterion trigger id from "
                        + trigger.getClass().getName() + "." + name, e);
            }
        }
        throw new IllegalStateException("Legacy criterion trigger " + trigger.getClass().getName()
                + " has neither getId() nor m_7295_()");
    }
}
