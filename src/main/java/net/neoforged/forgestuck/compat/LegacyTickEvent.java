package net.neoforged.forgestuck.compat;

import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Forge 1.20.1 {@code TickEvent} hierarchy. NeoForge 1.21.1 split ticks into Pre/Post event classes; {@link
 * LegacyTickBridge} re-posts them as these events (Pre = START, Post = END) so legacy handlers keep working.
 * Render ticks are forwarded from client render-frame events.
 */
public abstract class LegacyTickEvent extends Event {
    public enum Type { LEVEL, SERVER, CLIENT, PLAYER, RENDER }

    public enum Phase { START, END }

    public final Type type;
    public final LogicalSide side;
    public final Phase phase;

    protected LegacyTickEvent(Type type, LogicalSide side, Phase phase) {
        this.type = type;
        this.side = side;
        this.phase = phase;
    }

    public static class PlayerTickEvent extends LegacyTickEvent {
        public final Player player;

        public PlayerTickEvent(Phase phase, Player player) {
            super(Type.PLAYER, player.level().isClientSide ? LogicalSide.CLIENT : LogicalSide.SERVER, phase);
            this.player = player;
        }
    }

    public static class ServerTickEvent extends LegacyTickEvent {
        private final BooleanSupplier haveTime;

        public ServerTickEvent(Phase phase, BooleanSupplier haveTime) {
            super(Type.SERVER, LogicalSide.SERVER, phase);
            this.haveTime = haveTime;
        }

        public boolean haveTime() {
            return haveTime.getAsBoolean();
        }

        public MinecraftServer getServer() {
            return ServerLifecycleHooks.getCurrentServer();
        }
    }

    public static class ClientTickEvent extends LegacyTickEvent {
        public ClientTickEvent(Phase phase) {
            super(Type.CLIENT, LogicalSide.CLIENT, phase);
        }
    }

    public static class RenderTickEvent extends LegacyTickEvent {
        public final float renderTickTime;

        public RenderTickEvent(Phase phase, float renderTickTime) {
            super(Type.RENDER, LogicalSide.CLIENT, phase);
            this.renderTickTime = renderTickTime;
        }
    }

    public static class LevelTickEvent extends LegacyTickEvent {
        public final Level level;
        private final BooleanSupplier haveTime;

        public LevelTickEvent(Phase phase, Level level, BooleanSupplier haveTime) {
            super(Type.LEVEL, level.isClientSide ? LogicalSide.CLIENT : LogicalSide.SERVER, phase);
            this.level = level;
            this.haveTime = haveTime;
        }

        public boolean haveTime() {
            return haveTime.getAsBoolean();
        }
    }
}
