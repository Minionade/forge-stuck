package net.neoforged.forgestuck.compat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/** Forwards registration into the real mod-bus event; NeoForge retains validation and predicate merging. */
public final class LegacySpawnPlacements {
    private LegacySpawnPlacements() {}
    public static <T extends Entity> void register(RegisterSpawnPlacementsEvent event, EntityType<T> entity,
            LegacySpawnPlacementType placement, Heightmap.Types heightmap,
            SpawnPlacements.SpawnPredicate<T> predicate, RegisterSpawnPlacementsEvent.Operation operation) {
        event.register(entity, placement == null ? null : placement.nativeType(), heightmap, predicate, operation);
    }
    public static boolean isCancelable(RegisterSpawnPlacementsEvent event) { return false; }
    public static boolean hasResult(RegisterSpawnPlacementsEvent event) { return false; }
}
