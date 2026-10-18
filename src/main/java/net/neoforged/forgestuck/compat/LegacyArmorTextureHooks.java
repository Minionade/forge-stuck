package net.neoforged.forgestuck.compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class LegacyArmorTextureHooks {
    private static final ClassValue<Optional<Method>> LEGACY_OVERRIDES = new ClassValue<>() {
        @Override
        protected Optional<Method> computeValue(Class<?> type) {
            try {
                Method method = type.getMethod(
                        "getArmorTexture", ItemStack.class, Entity.class, EquipmentSlot.class, String.class);
                if (!method.trySetAccessible()) {
                    throw new IllegalStateException("Cannot access legacy armor texture override on "
                            + type.getName());
                }
                return Optional.of(method);
            } catch (NoSuchMethodException ignored) {
                return Optional.empty();
            }
        }
    };

    private LegacyArmorTextureHooks() {}

    public static String getArmorTexture(Entity entity, ItemStack armor, String defaultTexture,
            EquipmentSlot slot, String type) {
        return getLegacyArmorTexture(armor.getItem(), armor, entity, defaultTexture, slot, type);
    }

    static String getLegacyArmorTexture(Object item, ItemStack armor, Entity entity, String defaultTexture,
            EquipmentSlot slot, String type) {
        Optional<Method> override = LEGACY_OVERRIDES.get(item.getClass());
        if (override.isEmpty()) return defaultTexture;
        try {
            String texture = (String) override.get().invoke(item, armor, entity, slot, type);
            return texture == null ? defaultTexture : texture;
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot access legacy armor texture override on "
                    + item.getClass().getName(), e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Legacy armor texture override failed", cause);
        }
    }
}
