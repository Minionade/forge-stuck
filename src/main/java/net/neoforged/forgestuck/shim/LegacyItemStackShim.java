package net.neoforged.forgestuck.shim;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Generic reflective shim for the most common legacy ItemStack tag patterns.
 *
 * This avoids compile-time dependence on a specific Minecraft mapping while still attempting runtime bridge
 * behavior if the method exists on the target object instance.
 */
public final class LegacyItemStackShim {
    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyItemStackShim.class);

    private LegacyItemStackShim() {
    }

    public static Object getTag(Object stack) {
        return invoke(stack, "getTag");
    }

    public static Object getOrCreateTag(Object stack) {
        try {
            return invoke(stack, "getOrCreateTag");
        } catch (Throwable t) {
            return invoke(stack, "getTag");
        }
    }

    public static boolean hasTag(Object stack) {
        try {
            Object value = invoke(stack, "hasTag");
            return value instanceof Boolean && (Boolean) value;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void setTag(Object stack, Object tag) {
        if (stack == null) {
            return;
        }

        try {
            Method method = stack.getClass().getMethod("setTag", tag == null ? Object.class : tag.getClass());
            method.invoke(stack, tag);
        } catch (Throwable t) {
            try {
                Method method = stack.getClass().getMethod("setTag", Object.class);
                method.invoke(stack, tag);
            } catch (Throwable ignored) {
                LOGGER.debug("[Forge Stuck!!] ItemStack.setTag not available for runtime object {}", stack.getClass().getName());
            }
        }
    }

    public static void syncTag(Object stack, Object tag) {
        setTag(stack, tag);
    }

    private static Object invoke(Object stack, String methodName) {
        if (stack == null) {
            return null;
        }

        try {
            Method method = stack.getClass().getMethod(methodName);
            return method.invoke(stack);
        } catch (Throwable t) {
            return null;
        }
    }
}
