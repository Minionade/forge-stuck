package net.neoforged.forgestuck.compat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Stand-in for the removed static {@code BuiltInRegistries.ENCHANTMENT}. In 1.21.1 enchantments are a datapack
 * registry that normally exists inside a {@link RegistryAccess}, so the returned {@link Registry} forwards calls to
 * the live registry of the running server (or, on the client, of the connected level). Before a world exists, it
 * falls back to vanilla's built-in datapack registry bootstrap so menu-time resource reloads can resolve vanilla
 * enchantments. Modded enchantments require the live registry access.
 */
public final class LegacyEnchantmentRegistry {
    private LegacyEnchantmentRegistry() {}

    public static final Registry<Enchantment> ENCHANTMENT = create(LegacyEnchantmentRegistry::currentAccess);

    public static boolean contains(Set<?> allowed, Object enchantment) {
        if (!(enchantment instanceof Enchantment value)) return allowed.contains(enchantment);
        return ENCHANTMENT.getResourceKey(value).map(allowed::contains).orElse(false);
    }

    public static EnchantmentInstance createInstance(Enchantment enchantment, int level) {
        Holder<Enchantment> holder = ENCHANTMENT.getResourceKey(enchantment)
                .flatMap(ENCHANTMENT::getHolder)
                .map(value -> (Holder<Enchantment>) value)
                .orElseGet(() -> Holder.direct(enchantment));
        return new EnchantmentInstance(holder, level);
    }

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
            Optional<Registry<Enchantment>> live = access.get().flatMap(a -> a.registry(Registries.ENCHANTMENT));
            if (live.isEmpty()) {
                return fallback(method, args);
            }
            try {
                return method.invoke(live.get(), args);
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

    private static final class VanillaFallback {
        private static final HolderLookup.RegistryLookup<Enchantment> LOOKUP =
                VanillaRegistries.createLookup().lookupOrThrow(Registries.ENCHANTMENT);
    }

    private static Object fallback(Method method, Object[] args) {
        HolderLookup.RegistryLookup<Enchantment> lookup = VanillaFallback.LOOKUP;
        String name = method.getName();
        if (name.equals("containsKey") && args[0] instanceof ResourceLocation id) {
            return lookup.get(ResourceKey.create(Registries.ENCHANTMENT, id)).isPresent();
        }
        if ((name.equals("get") || name.equals("getOptional")) && args[0] instanceof ResourceLocation id) {
            Optional<Holder.Reference<Enchantment>> holder =
                    lookup.get(ResourceKey.create(Registries.ENCHANTMENT, id));
            return name.equals("getOptional") ? holder.map(Holder::value) : holder.map(Holder::value).orElse(null);
        }
        if ((name.equals("get") || name.equals("getOptional") || name.equals("getHolder"))
                && args[0] instanceof ResourceKey<?> key) {
            @SuppressWarnings("unchecked")
            ResourceKey<Enchantment> enchantmentKey = (ResourceKey<Enchantment>) key;
            Optional<Holder.Reference<Enchantment>> holder = lookup.get(enchantmentKey);
            return switch (name) {
                case "getOptional" -> holder.map(Holder::value);
                case "getHolder" -> holder;
                default -> holder.map(Holder::value).orElse(null);
            };
        }
        if (name.equals("getHolder") && args[0] instanceof ResourceLocation id) {
            return lookup.get(ResourceKey.create(Registries.ENCHANTMENT, id));
        }
        if (name.equals("getOrThrow") && args[0] instanceof ResourceKey<?> key) {
            @SuppressWarnings("unchecked")
            ResourceKey<Enchantment> enchantmentKey = (ResourceKey<Enchantment>) key;
            return lookup.getOrThrow(enchantmentKey).value();
        }
        if (name.equals("getHolderOrThrow") && args[0] instanceof ResourceKey<?> key) {
            @SuppressWarnings("unchecked")
            ResourceKey<Enchantment> enchantmentKey = (ResourceKey<Enchantment>) key;
            return lookup.getOrThrow(enchantmentKey);
        }
        if (name.equals("containsKey") && args[0] instanceof ResourceKey<?> key) {
            return lookup.get(key.cast(Registries.ENCHANTMENT).orElseThrow()).isPresent();
        }
        if (name.equals("getResourceKey") || name.equals("getKey")) {
            Optional<ResourceKey<Enchantment>> key = lookup.listElements()
                    .filter(holder -> holder.value() == args[0])
                    .map(Holder.Reference::key).findFirst();
            return name.equals("getKey") ? key.map(ResourceKey::location).orElse(null) : key;
        }
        if (name.equals("getId")) {
            List<Holder.Reference<Enchantment>> holders = lookup.listElements().toList();
            if (args[0] instanceof Enchantment enchantment) {
                for (int i = 0; i < holders.size(); i++) {
                    if (holders.get(i).value() == enchantment) return i;
                }
                return -1;
            }
            if (args[0] instanceof ResourceLocation id) {
                ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, id);
                for (int i = 0; i < holders.size(); i++) {
                    if (holders.get(i).key().equals(key)) return i;
                }
                return -1;
            }
            if (args[0] instanceof ResourceKey<?> key) {
                return key.cast(Registries.ENCHANTMENT).map(castKey -> {
                    for (int i = 0; i < holders.size(); i++) {
                        if (holders.get(i).key().equals(castKey)) return i;
                    }
                    return -1;
                }).orElse(-1);
            }
        }
        if (name.equals("byId") && args[0] instanceof Integer id) {
            return VanillaFallback.LOOKUP.listElements().skip(id).findFirst().map(Holder::value).orElse(null);
        }
        if (name.equals("keySet") || name.equals("registryKeySet") || name.equals("entrySet")
                || name.equals("holders") || name.equals("stream") || name.equals("size")
                || name.equals("isEmpty") || name.equals("iterator") || name.equals("asLookup")
                || name.equals("holderOwner") || name.equals("registryLifecycle")) {
            List<Holder.Reference<Enchantment>> holders = lookup.listElements().toList();
            return switch (name) {
                case "keySet" -> holders.stream().map(Holder.Reference::key).map(ResourceKey::location)
                        .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
                case "registryKeySet" -> holders.stream().map(Holder.Reference::key)
                        .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
                case "entrySet" -> {
                    Map<ResourceKey<Enchantment>, Enchantment> entries = new LinkedHashMap<>();
                    holders.forEach(holder -> entries.put(holder.key(), holder.value()));
                    yield entries.entrySet();
                }
                case "holders" -> holders.stream();
                case "stream" -> holders.stream().map(Holder::value);
                case "size" -> holders.size();
                case "isEmpty" -> holders.isEmpty();
                case "iterator" -> holders.stream().map(Holder::value).iterator();
                case "asLookup", "holderOwner" -> lookup;
                default -> lookup.registryLifecycle();
            };
        }
        throw new UnsupportedOperationException("Registry minecraft:enchantment." + name
                + " is unavailable before world loading except for vanilla datapack entries");
    }

    private static final class ClientAccess {
        static Optional<RegistryAccess> get() {
            var level = net.minecraft.client.Minecraft.getInstance().level;
            return level == null ? Optional.empty() : Optional.of(level.registryAccess());
        }
    }
}