package net.neoforged.forgestuck.compat;

import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Forge 1.20.1 {@code PacketDistributor} targets expressed through NeoForge's {@link PacketDistributor}. */
public final class LegacyPacketDistributor<T> {
    private static final CustomPacketPayload[] NONE = new CustomPacketPayload[0];

    public static final LegacyPacketDistributor<ServerPlayer> PLAYER =
            new LegacyPacketDistributor<>((p, m) -> PacketDistributor.sendToPlayer(p, m, NONE));
    public static final LegacyPacketDistributor<ResourceKey<Level>> DIMENSION =
            new LegacyPacketDistributor<>((k, m) -> PacketDistributor.sendToPlayersInDimension(level(k), m, NONE));
    public static final LegacyPacketDistributor<LegacyTargetPoint> NEAR = new LegacyPacketDistributor<>(
            (p, m) -> PacketDistributor.sendToPlayersNear(level(p.dimension), p.excluded, p.x, p.y, p.z, p.radius, m, NONE));
    public static final LegacyPacketDistributor<Void> ALL =
            new LegacyPacketDistributor<>((v, m) -> PacketDistributor.sendToAllPlayers(m, NONE));
    public static final LegacyPacketDistributor<Void> SERVER =
            new LegacyPacketDistributor<>((v, m) -> PacketDistributor.sendToServer(m, NONE));
    public static final LegacyPacketDistributor<Entity> TRACKING_ENTITY =
            new LegacyPacketDistributor<>((e, m) -> PacketDistributor.sendToPlayersTrackingEntity(e, m, NONE));
    public static final LegacyPacketDistributor<Entity> TRACKING_ENTITY_AND_SELF =
            new LegacyPacketDistributor<>((e, m) -> PacketDistributor.sendToPlayersTrackingEntityAndSelf(e, m, NONE));
    public static final LegacyPacketDistributor<LevelChunk> TRACKING_CHUNK = new LegacyPacketDistributor<>(
            (c, m) -> PacketDistributor.sendToPlayersTrackingChunk((ServerLevel) c.getLevel(), c.getPos(), m, NONE));

    /** Forge's {@code PacketDistributor.PacketTarget}. */
    public static final class LegacyPacketTarget {
        private final Consumer<CustomPacketPayload> sender;

        LegacyPacketTarget(Consumer<CustomPacketPayload> sender) {
            this.sender = sender;
        }

        void send(CustomPacketPayload payload) {
            sender.accept(payload);
        }
    }

    /** Forge's {@code PacketDistributor.TargetPoint}. */
    public static final class LegacyTargetPoint {
        final ServerPlayer excluded;
        final double x;
        final double y;
        final double z;
        final double radius;
        final ResourceKey<Level> dimension;

        public LegacyTargetPoint(ServerPlayer excluded, double x, double y, double z, double radius,
                ResourceKey<Level> dimension) {
            this.excluded = excluded;
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.dimension = dimension;
        }

        public LegacyTargetPoint(double x, double y, double z, double radius, ResourceKey<Level> dimension) {
            this(null, x, y, z, radius, dimension);
        }

        public static LegacyTargetPoint p(double x, double y, double z, double radius, ResourceKey<Level> dimension) {
            return new LegacyTargetPoint(x, y, z, radius, dimension);
        }
    }

    interface Dispatch<T> {
        void send(T value, CustomPacketPayload payload);
    }

    private final Dispatch<T> dispatch;

    private LegacyPacketDistributor(Dispatch<T> dispatch) {
        this.dispatch = dispatch;
    }

    public LegacyPacketTarget with(Supplier<T> supplier) {
        T value = supplier.get();
        return new LegacyPacketTarget(payload -> dispatch.send(value, payload));
    }

    public LegacyPacketTarget noArg() {
        return new LegacyPacketTarget(payload -> dispatch.send(null, payload));
    }

    private static ServerLevel level(ResourceKey<Level> key) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerLevel level = server == null ? null : server.getLevel(key);
        if (level == null) throw new IllegalStateException("No loaded level " + key + " to send a packet to");
        return level;
    }
}