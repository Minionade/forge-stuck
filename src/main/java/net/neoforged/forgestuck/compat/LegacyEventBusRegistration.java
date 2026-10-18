package net.neoforged.forgestuck.compat;

import java.lang.reflect.Method;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Preserves Forge's no-op registration of targets without any annotated event handlers. */
public final class LegacyEventBusRegistration {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgestuck");

    private LegacyEventBusRegistration() {}

    public static void register(IEventBus bus, Object target) {
        java.util.Objects.requireNonNull(bus, "event bus");
        java.util.Objects.requireNonNull(target, "listener target");
        if (target instanceof Method) {
            bus.register(target);
            return;
        }
        Class<?> type = target instanceof Class<?> clazz ? clazz : target.getClass();
        if (hasSubscriber(type)) {
            bus.register(target);
        } else {
            LOGGER.debug("[Forge Stuck!!] {} has no @SubscribeEvent methods; keeping Forge's no-op registration",
                    type.getName());
        }
    }

    private static boolean hasSubscriber(Class<?> type) {
        if (type == null || type == Object.class) return false;
        for (Method method : type.getDeclaredMethods()) {
            if (method.isAnnotationPresent(SubscribeEvent.class)) return true;
        }
        if (hasSubscriber(type.getSuperclass())) return true;
        for (Class<?> implemented : type.getInterfaces()) {
            if (hasSubscriber(implemented)) return true;
        }
        return false;
    }
}
