package net.neoforged.forgestuck.compat;

import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.core.cauldron.CauldronInteraction;

public final class LegacyCauldronInteractions {
    private LegacyCauldronInteractions() {}

    public static Map<Item, CauldronInteraction> water() {
        return CauldronInteraction.WATER.map();
    }

    public static Map<Item, CauldronInteraction> empty() {
        return CauldronInteraction.EMPTY.map();
    }
}
