package net.neoforged.forgestuck.compat;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Field;
import net.minecraft.world.entity.EntityType;
import org.junit.jupiter.api.Test;

class LegacyEntityTypeBuilderTest {
    @Test
    void installsAClientAwareFactoryOnTheBuilder() throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        Object builderObject = unsafeClass.getMethod("allocateInstance", Class.class)
                .invoke(unsafe, EntityType.Builder.class);
        @SuppressWarnings("unchecked")
        EntityType.Builder<?> builder = (EntityType.Builder<?>) builderObject;

        LegacyEntityTypeBuilder.setCustomClientFactory(builder, (type, level) -> null);

        Field factory = EntityType.Builder.class.getDeclaredField("factory");
        factory.setAccessible(true);
        assertNotNull(factory.get(builder));
    }
}
