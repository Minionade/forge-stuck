package net.neoforged.forgestuck.compat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Stand-in for the removed static {@code BuiltInRegistries.ENCHANTMENT}. In 1.21.1 enchantments are a datapack
 * registry that only exists inside a {@link RegistryAccess}, so the returned {@link Registry} forwards every call to
 * the live registry of the running server (or, on the client, of the connected level). It adds no entries. Holding
 * the field (static init) links; any call made while no server/level exists fails with
 * {@link UnsupportedOperationException}. {@code key()} needs no access.
 */
public final class LegacyEnchantmentRegistry {
    private LegacyEnchantmentRegistry() {}

    public static final Registry<Enchantment> ENCHANTMENT = create(LegacyEnchantmentRegistry::currentAccess);

    @SuppressWarnings("unchecked")
    static Registry<Enchantment> create(Supplier<Optional<RegistryAccess>> access) {
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "key" -> {
                    if (method.getParameterCount() == 0) return Registries.ENCHANTMENT;
                }
                case "toString" -> {
                    if (method.getParameterCount() == 0) return "LegacyEnchantmentRegistry";
                }
                case "hashCode" -> {
                    if (method.getParameterCount() == 0) return System.identityHashCode(proxy);
                }
                case "equals" -> {
                    if (method.getParameterCount() == 1) return proxy == args[0];
                }
                default -> {
                }
            }
            Registry<Enchantment> live = access.get().flatMap(a -> a.registry(Registries.ENCHANTMENT))
                    .orElseThrow(() -> new UnsupportedOperationException("Registry minecraft:enchantment."
                            + method.getName() + ": enchantments are a datapack registry in 1.21.1 and no server or "
                            + "client level (RegistryAccess) is available yet"));
            try {
                return method.invoke(live, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        };
        return (Registry<Enchantment>) Proxy.newProxyInstance(Registry.class.getClassLoader(),
                new Class<?>[] {Registry.class}, handler);
    }

    private static Optional<RegistryAccess> currentAccess() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) return Optional.of(server.registryAccess());
        return FMLEnvironment.dist.isClient() ? ClientAccess.get() : Optional.empty();
    }

    private static final class ClientAccess {
        static Optional<RegistryAccess> get() {
            var level = net.minecraft.client.Minecraft.getInstance().level;
            return level == null ? Optional.empty() : Optional.of(level.registryAccess());
        }
    }
}