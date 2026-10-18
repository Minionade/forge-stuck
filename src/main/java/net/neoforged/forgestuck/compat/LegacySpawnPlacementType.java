package net.neoforged.forgestuck.compat;

import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.neoforged.fml.common.asm.enumextension.ExtensionInfo;
import net.neoforged.fml.common.asm.enumextension.IExtensibleEnum;

/** Keeps Forge 1.20.1 enum identity/order while translating built-in placements at registration. */
public enum LegacySpawnPlacementType implements IExtensibleEnum {
    ON_GROUND(SpawnPlacementTypes.ON_GROUND, null),
    IN_WATER(SpawnPlacementTypes.IN_WATER, null),
    NO_RESTRICTIONS(SpawnPlacementTypes.NO_RESTRICTIONS, null),
    IN_LAVA(SpawnPlacementTypes.IN_LAVA, null);

    private final SpawnPlacementType nativeType;
    private final String legacyName;

    LegacySpawnPlacementType(SpawnPlacementType nativeType, String legacyName) {
        this.nativeType = nativeType;
        this.legacyName = legacyName;
    }

    LegacySpawnPlacementType(String legacyName) {
        this(SpawnPlacementTypes.NO_RESTRICTIONS, legacyName);
    }

    public SpawnPlacementType nativeType() { return nativeType; }

    public static LegacySpawnPlacementType create(String name) {
        for (LegacySpawnPlacementType type : values()) {
            if (name.equals(type.legacyName)) return type;
        }
        throw new UnsupportedOperationException("Forge SpawnPlacements.Type.create(\"" + name
                + "\") has no generated NeoForge enum constant: Forge Stuck could not evaluate its arguments "
                + "statically when transforming the mod jar, so no enumextensions.json entry was emitted for it");
    }

    public static ExtensionInfo getExtensionInfo() {
        return ExtensionInfo.nonExtended(LegacySpawnPlacementType.class);
    }
}
