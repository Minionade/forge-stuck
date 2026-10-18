package net.neoforged.forgestuck.compat;

import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacementTypes;

/** Keeps Forge 1.20.1 enum identity/order while translating built-in placements at registration. */
public enum LegacySpawnPlacementType {
    ON_GROUND(SpawnPlacementTypes.ON_GROUND),
    IN_WATER(SpawnPlacementTypes.IN_WATER),
    NO_RESTRICTIONS(SpawnPlacementTypes.NO_RESTRICTIONS),
    IN_LAVA(SpawnPlacementTypes.IN_LAVA);

    private final SpawnPlacementType nativeType;
    LegacySpawnPlacementType(SpawnPlacementType nativeType) { this.nativeType = nativeType; }
    public SpawnPlacementType nativeType() { return nativeType; }
}
