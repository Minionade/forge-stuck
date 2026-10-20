package net.neoforged.forgestuck.compat;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.EntityCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Adapts Forge entity capability registration and attachment events to NeoForge entity capabilities.
 */
public final class LegacyCapabilities {
    private static final Map<Entity, Map<Class<?>, Object>> PLAYER_CAPABILITIES = new IdentityHashMap<>();
    private static final Map<Entity, LegacyAttachCapabilitiesEvent<Entity>> ATTACHED_PROVIDERS =
            new IdentityHashMap<>();
    private static final Map<Level, LegacyAttachCapabilitiesEvent<Level>> ATTACHED_LEVEL_PROVIDERS =
            new IdentityHashMap<>();
    private static final AtomicBoolean CLEANUP_LISTENERS_REGISTERED = new AtomicBoolean();

    private LegacyCapabilities() {}

    public static void register(RegisterCapabilitiesEvent event, Class<?> type) {
        Objects.requireNonNull(type, "capability type");
        Constructor<?> constructor = playerConstructor(type);
        Objects.requireNonNull(event, "capability registration event");
        if (constructor != null && LegacyICapabilityProvider.class.isAssignableFrom(type)) {
            registerPlayer(event, type, constructor);
        } else {
            registerAttached(event, type);
        }
        registerCleanupListeners();
    }

    public static <T> LazyOptional<T> getCapability(Entity entity, LegacyCapability<T> capability) {
        return getCapability(entity, capability, null);
    }

    public static <T> LazyOptional<T> getCapability(Entity entity, LegacyCapability<T> capability,
            Direction side) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(capability, "capability");
        T value = capability.getCapability(entity);
        if (value == null) return LazyOptional.empty();
        LazyOptional<T> optional = findProviderOptional(entity, capability, side, value);
        if (optional == null) {
            throw new IllegalStateException("Registered legacy capability " + capability.getName()
                    + " has no matching provider on " + entity.getClass().getName());
        }
        return optional;
    }

    public static <T> LazyOptional<T> getCapability(BlockEntity blockEntity, LegacyCapability<T> capability) {
        return getCapability(blockEntity, capability, null);
    }

    public static <T> LazyOptional<T> getCapability(BlockEntity blockEntity, LegacyCapability<T> capability,
            Direction side) {
        Objects.requireNonNull(blockEntity, "block entity");
        Objects.requireNonNull(capability, "capability");
        if (blockEntity instanceof LegacyICapabilityProvider provider) {
            LazyOptional<T> optional = side == null
                    ? provider.getCapability(capability)
                    : provider.getCapability(capability, side);
            if (optional == null) {
                throw new IllegalStateException("Legacy block capability provider returned null for "
                        + capability.getName() + " from " + blockEntity.getClass().getName());
            }
            if (optional.isPresent()) return optional;
        }
        T value = capability.getBlockCapability(blockEntity, side);
        return value == null ? LazyOptional.empty() : LazyOptional.of(() -> value);
    }

    public static <T> LazyOptional<T> getCapability(ItemStack stack, LegacyCapability<T> capability) {
        Objects.requireNonNull(stack, "item stack");
        Objects.requireNonNull(capability, "capability");
        T value = capability.getItemCapability(stack);
        return value == null ? LazyOptional.empty() : LazyOptional.of(() -> value);
    }

    public static <T> LazyOptional<T> getCapability(Level level, LegacyCapability<T> capability) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(capability, "capability");
        LegacyAttachCapabilitiesEvent<Level> event = attachLevelProviders(level);
        for (LegacyICapabilityProvider provider : event.getCapabilities().values()) {
            LazyOptional<T> optional = provider.getCapability(capability);
            if (optional == null) {
                throw new IllegalStateException("Legacy level capability provider returned null for "
                        + capability.getName() + " from " + provider.getClass().getName());
            }
            if (optional.isPresent()) return optional;
        }
        return LazyOptional.empty();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> void registerPlayer(RegisterCapabilitiesEvent event, Class<?> rawType,
            Constructor<?> rawConstructor) {
        Class<T> type = (Class<T>) rawType;
        Constructor<T> constructor = (Constructor<T>) rawConstructor;
        LegacyCapability<T> legacyCapability = LegacyCapabilityManager.forType(type);
        EntityCapability<T, Void> capability = EntityCapability.createVoid(capabilityId(type), type);
        legacyCapability.bind(capability);
        event.registerEntity(capability, EntityType.PLAYER,
                (player, context) -> getOrCreate(player, type, constructor));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> void registerAttached(RegisterCapabilitiesEvent event, Class<?> rawType) {
        Class<T> type = (Class<T>) rawType;
        LegacyCapability<T> legacyCapability = LegacyCapabilityManager.forType(type);
        EntityCapability<T, Void> capability = EntityCapability.createVoid(capabilityId(type), type);
        legacyCapability.bind(capability);
        EntityType[] entityTypes = BuiltInRegistries.ENTITY_TYPE.stream().toArray(EntityType[]::new);
        if (entityTypes.length == 0) {
            throw new IllegalStateException("No entity types are available for legacy capability "
                    + type.getName());
        }
        for (EntityType entityType : entityTypes) {
            registerEntity(event, capability, entityType, legacyCapability);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> void registerEntity(RegisterCapabilitiesEvent event,
            EntityCapability<T, Void> capability, EntityType<?> entityType, LegacyCapability<T> legacyCapability) {
        event.registerEntity((EntityCapability) capability, (EntityType) entityType,
                (net.neoforged.neoforge.capabilities.ICapabilityProvider<Entity, Void, T>)
                        (entity, context) -> resolveAttached(entity, legacyCapability, null));
    }

    private static <T> T resolveAttached(Entity entity, LegacyCapability<T> capability, Direction side) {
        LazyOptional<T> optional = findProviderOptional(entity, capability, side, null);
        return optional == null ? null : optional.resolve().orElse(null);
    }

    private static <T> LazyOptional<T> findProviderOptional(Entity entity, LegacyCapability<T> capability,
            Direction side, T expectedValue) {
        if (expectedValue instanceof LegacyICapabilityProvider directProvider) {
            return matchingOptional(directProvider, capability, side, expectedValue);
        }
        LegacyAttachCapabilitiesEvent<Entity> event = attachProviders(entity);
        for (LegacyICapabilityProvider provider : event.getCapabilities().values()) {
            LazyOptional<T> optional = matchingOptional(provider, capability, side, expectedValue);
            if (optional != null) return optional;
        }
        return null;
    }

    private static <T> LazyOptional<T> matchingOptional(LegacyICapabilityProvider provider,
            LegacyCapability<T> capability, Direction side, T expectedValue) {
        LazyOptional<T> optional = side == null
                ? provider.getCapability(capability)
                : provider.getCapability(capability, side);
        if (optional == null) {
            throw new IllegalStateException("Legacy capability provider returned null for "
                    + capability.getName() + " from " + provider.getClass().getName());
        }
        if (expectedValue == provider) return optional;
        T resolved = optional.resolve().orElse(null);
        return resolved != null && (expectedValue == null || resolved == expectedValue) ? optional : null;
    }

    private static LegacyAttachCapabilitiesEvent<Entity> attachProviders(Entity entity) {
        synchronized (PLAYER_CAPABILITIES) {
            LegacyAttachCapabilitiesEvent<Entity> event = ATTACHED_PROVIDERS.get(entity);
            if (event != null) return event;
            event = new LegacyAttachCapabilitiesEvent<>(Entity.class, entity);
            ATTACHED_PROVIDERS.put(entity, event);
            NeoForge.EVENT_BUS.post(event);
            return event;
        }
    }

    private static LegacyAttachCapabilitiesEvent<Level> attachLevelProviders(Level level) {
        synchronized (ATTACHED_LEVEL_PROVIDERS) {
            LegacyAttachCapabilitiesEvent<Level> event = ATTACHED_LEVEL_PROVIDERS.get(level);
            if (event != null) return event;
            event = new LegacyAttachCapabilitiesEvent<>(Level.class, level);
            ATTACHED_LEVEL_PROVIDERS.put(level, event);
            registerCleanupListeners();
            NeoForge.EVENT_BUS.post(event);
            return event;
        }
    }

    private static ResourceLocation capabilityId(Class<?> type) {
        String path = type.getName().replace('.', '/').replace('$', '/').toLowerCase(Locale.ROOT);
        return ResourceLocation.fromNamespaceAndPath("forgestuck", "legacy/" + path);
    }

    private static Constructor<?> playerConstructor(Class<?> type) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor(Player.class);
            if (!LegacyICapabilityProvider.class.isAssignableFrom(type)) return null;
            constructor.setAccessible(true);
            return constructor;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static <T> T getOrCreate(Player player, Class<T> type, Constructor<T> constructor) {
        synchronized (PLAYER_CAPABILITIES) {
            Map<Class<?>, Object> capabilities =
                    PLAYER_CAPABILITIES.computeIfAbsent(player, ignored -> new java.util.HashMap<>());
            Object value = capabilities.get(type);
            if (value == null) {
                value = instantiate(constructor, player);
                capabilities.put(type, value);
            }
            return type.cast(value);
        }
    }

    private static <T> T instantiate(Constructor<T> constructor, Player player) {
        try {
            return constructor.newInstance(player);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            Throwable cause = e instanceof InvocationTargetException invocation && invocation.getCause() != null
                    ? invocation.getCause() : e;
            throw new IllegalStateException("Could not create player capability "
                    + constructor.getDeclaringClass().getName(), cause);
        }
    }

    private static void registerCleanupListeners() {
        if (!CLEANUP_LISTENERS_REGISTERED.compareAndSet(false, true)) return;
        NeoForge.EVENT_BUS.addListener((EntityLeaveLevelEvent event) -> clear(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> clear(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.Clone event) -> clear(event.getOriginal()));
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload event) -> {
            if (event.getLevel() instanceof Level level) clear(level);
        });
    }

    private static void clear(Entity entity) {
        Map<Class<?>, Object> capabilities;
        LegacyAttachCapabilitiesEvent<Entity> attachedProviders;
        synchronized (PLAYER_CAPABILITIES) {
            capabilities = PLAYER_CAPABILITIES.remove(entity);
            attachedProviders = ATTACHED_PROVIDERS.remove(entity);
        }
        if (capabilities != null) capabilities.values().forEach(value -> {
            if (value instanceof Runnable invalidation) invalidation.run();
        });
        if (attachedProviders != null) attachedProviders.getListeners().forEach(Runnable::run);
    }

    private static void clear(Level level) {
        LegacyAttachCapabilitiesEvent<Level> attachedProviders;
        synchronized (ATTACHED_LEVEL_PROVIDERS) {
            attachedProviders = ATTACHED_LEVEL_PROVIDERS.remove(level);
        }
        if (attachedProviders != null) attachedProviders.getListeners().forEach(Runnable::run);
    }
}
