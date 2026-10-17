package net.neoforged.forgestuck.compat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;

/**
 * View of the root registry ({@code BuiltInRegistries.REGISTRY}) that forwards every call to the real one and only
 * adds one answer: {@code getKey(LegacyEnchantmentRegistry.ENCHANTMENT)} is {@code minecraft:enchantment}, because the
 * enchantment view is not (and must not be) a member of the real root registry. Nothing is added to any registry.
 */
public final class LegacyRegistryRoot {
    private LegacyRegistryRoot() {}

    public static final Registry<?> REGISTRY = create(() -> BuiltInRegistries.REGISTRY, LegacyEnchantmentRegistry.ENCHANTMENT);

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Registry<?> create(Supplier<Registry<?>> real, Object enchantmentView) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getParameterCount() == 1 && method.getName().equals("getKey") && args[0] == enchantmentView) {
                return Registries.ENCHANTMENT.location();
            }
            if (method.getParameterCount() == 0 && method.getName().equals("toString")) return "LegacyRegistryRoot";
            if (method.getParameterCount() == 0 && method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            if (method.getParameterCount() == 1 && method.getName().equals("equals")) return proxy == args[0];
            try {
                return method.invoke(real.get(), args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        };
        return (Registry<?>) Proxy.newProxyInstance(Registry.class.getClassLoader(), new Class<?>[] {Registry.class}, handler);
    }
}
