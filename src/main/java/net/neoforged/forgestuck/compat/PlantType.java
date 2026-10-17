package net.neoforged.forgestuck.compat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stand-in for Forge 1.20.1's {@code PlantType}, which NeoForge 1.21.1 removed. Like the original it is a final token
 * class whose instances are interned by name ({@code get(String)}), so identity comparison and use as map keys keep
 * working. It carries no engine behavior: nothing in 1.21.1 consults it.
 */
public final class PlantType {
    private static final Map<String, PlantType> VALUES = new ConcurrentHashMap<>();

    public static final PlantType PLAINS = get("plains");
    public static final PlantType DESERT = get("desert");
    public static final PlantType BEACH = get("beach");
    public static final PlantType CAVE = get("cave");
    public static final PlantType WATER = get("water");
    public static final PlantType NETHER = get("nether");
    public static final PlantType CROP = get("crop");

    private final String name;

    private PlantType(String name) {
        this.name = name;
    }

    public static PlantType get(String name) {
        return VALUES.computeIfAbsent(name, PlantType::new);
    }

    public String getName() {
        return name;
    }
}
