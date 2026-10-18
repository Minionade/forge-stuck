package net.neoforged.forgestuck.compat;

import java.lang.reflect.Field;
import java.util.function.BiFunction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Restores Forge's client-side entity factory by wrapping NeoForge's builder factory. */
public final class LegacyEntityTypeBuilder {
    private LegacyEntityTypeBuilder() {}

    @SuppressWarnings("unchecked")
    public static <T extends Entity> EntityType.Builder<T> setCustomClientFactory(EntityType.Builder<T> builder,
            BiFunction<EntityType<T>, Level, T> clientFactory) {
        try {
            Field field = EntityType.Builder.class.getDeclaredField("factory");
            field.setAccessible(true);
            EntityType.EntityFactory<T> serverFactory = (EntityType.EntityFactory<T>) field.get(builder);
            EntityType.EntityFactory<T> combined = (type, level) -> level.isClientSide
                    ? clientFactory.apply(type, level)
                    : serverFactory.create(type, level);
            field.set(builder, combined);
            return builder;
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("Unable to install legacy client entity factory", e);
        }
    }
}
