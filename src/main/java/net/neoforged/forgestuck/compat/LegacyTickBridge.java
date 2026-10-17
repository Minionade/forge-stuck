package net.neoforged.forgestuck.compat;

import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Re-posts NeoForge's Pre/Post tick events as {@link LegacyTickEvent}s; installed once by the first legacy mod. */
final class LegacyTickBridge {
    private static boolean installed;

    private LegacyTickBridge() {}

    static synchronized void install() {
        if (installed) return;
        installed = true;
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(PlayerTickEvent.Pre.class, e -> bus.post(
                new LegacyTickEvent.PlayerTickEvent(LegacyTickEvent.Phase.START, e.getEntity())));
        bus.addListener(PlayerTickEvent.Post.class, e -> bus.post(
                new LegacyTickEvent.PlayerTickEvent(LegacyTickEvent.Phase.END, e.getEntity())));
        bus.addListener(ServerTickEvent.Pre.class, e -> bus.post(
                new LegacyTickEvent.ServerTickEvent(LegacyTickEvent.Phase.START, e::hasTime)));
        bus.addListener(ServerTickEvent.Post.class, e -> bus.post(
                new LegacyTickEvent.ServerTickEvent(LegacyTickEvent.Phase.END, e::hasTime)));
        bus.addListener(LevelTickEvent.Pre.class, e -> bus.post(
                new LegacyTickEvent.LevelTickEvent(LegacyTickEvent.Phase.START, e.getLevel(), e::hasTime)));
        bus.addListener(LevelTickEvent.Post.class, e -> bus.post(
                new LegacyTickEvent.LevelTickEvent(LegacyTickEvent.Phase.END, e.getLevel(), e::hasTime)));
        if (FMLEnvironment.dist == Dist.CLIENT) LegacyClientTickBridge.install();
    }
}
