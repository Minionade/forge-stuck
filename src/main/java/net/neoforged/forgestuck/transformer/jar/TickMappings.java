package net.neoforged.forgestuck.transformer.jar;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Forge 1.20.1 {@code TickEvent} mapped onto {@code compat.LegacyTickEvent}, fed by {@code LegacyTickBridge}. */
final class TickMappings {
    private TickMappings() {}

    private static final String T = "net/minecraftforge/event/TickEvent";
    private static final String C = "net/neoforged/forgestuck/compat/LegacyTickEvent";

    static List<ForgeOwnerMappings.Entry> entries() {
        String phase = "L" + T + "$Phase;";
        String type = "L" + T + "$Type;";
        Set<String> base = Set.of("phase" + phase, "type" + type, "side" + "Lnet/minecraftforge/fml/LogicalSide;");
        return List.of(
                new ForgeOwnerMappings.Entry(T, C, base),
                new ForgeOwnerMappings.Entry(T + "$Phase", C + "$Phase", enumMembers(phase, "START", "END")),
                new ForgeOwnerMappings.Entry(T + "$Type", C + "$Type",
                        enumMembers(type, "LEVEL", "SERVER", "CLIENT", "PLAYER", "RENDER")),
                new ForgeOwnerMappings.Entry(T + "$PlayerTickEvent", C + "$PlayerTickEvent",
                        with(base, "player" + "Lnet/minecraft/world/entity/player/Player;")),
                new ForgeOwnerMappings.Entry(T + "$ServerTickEvent", C + "$ServerTickEvent",
                        with(base, "haveTime()Z", "getServer()Lnet/minecraft/server/MinecraftServer;")),
                new ForgeOwnerMappings.Entry(T + "$ClientTickEvent", C + "$ClientTickEvent", base),
                new ForgeOwnerMappings.Entry(T + "$RenderTickEvent", C + "$RenderTickEvent",
                        with(base, "renderTickTimeF", "<init>(" + phase + "F)V")),
                new ForgeOwnerMappings.Entry(T + "$LevelTickEvent", C + "$LevelTickEvent",
                        with(base, "level" + "Lnet/minecraft/world/level/Level;", "haveTime()Z")));
    }

    private static Set<String> with(Set<String> base, String... more) {
        Set<String> out = new HashSet<>(base);
        out.addAll(List.of(more));
        return out;
    }

    private static Set<String> enumMembers(String desc, String... constants) {
        Set<String> out = new HashSet<>();
        for (String c : constants) out.add(c + desc);
        out.addAll(List.of("ordinal()I", "name()Ljava/lang/String;", "values()[" + desc,
                "valueOf(Ljava/lang/String;)" + desc, "equals(Ljava/lang/Object;)Z", "hashCode()I",
                "toString()Ljava/lang/String;"));
        return out;
    }
}
