package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.forgestuck.compat.LegacyEnchantmentRegistry;
import org.junit.jupiter.api.Test;

class LegacyRegistryRootTest {
    private static Registry<?> fakeRoot() {
        return (Registry<?>) Proxy.newProxyInstance(Registry.class.getClassLoader(), new Class<?>[] {Registry.class},
                (p, m, a) -> switch (m.getName()) {
                    case "size" -> 42;
                    case "getKey" -> null;
                    default -> throw new UnsupportedOperationException(m.getName());
                });
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void enchantmentViewHasItsRegistryNameAndEverythingElseForwards() throws Exception {
        var create = Class.forName("net.neoforged.forgestuck.compat.LegacyRegistryRoot")
                .getDeclaredMethod("create", java.util.function.Supplier.class, Object.class);
        create.setAccessible(true);
        Object view = LegacyEnchantmentRegistry.ENCHANTMENT;
        Registry<?> root = (Registry<?>) create.invoke(null, (java.util.function.Supplier<Registry<?>>) LegacyRegistryRootTest::fakeRoot, view);
        ResourceLocation key = ((Registry) root).getKey(view);
        assertEquals(Registries.ENCHANTMENT.location(), key);
        assertEquals(42, root.size());
        assertEquals(null, ((Registry) root).getKey(new Object()));
    }
}
