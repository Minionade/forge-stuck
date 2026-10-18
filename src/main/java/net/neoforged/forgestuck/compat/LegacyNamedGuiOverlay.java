package net.neoforged.forgestuck.compat;

import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

public final class LegacyNamedGuiOverlay {
    private static final ConcurrentHashMap<ResourceLocation, LegacyNamedGuiOverlay> BY_NAME =
            new ConcurrentHashMap<>();

    public static final LegacyNamedGuiOverlay CROSSHAIR = of(VanillaGuiLayers.CROSSHAIR);
    public static final LegacyNamedGuiOverlay HOTBAR = of(VanillaGuiLayers.HOTBAR);
    public static final LegacyNamedGuiOverlay PLAYER_HEALTH = of(VanillaGuiLayers.PLAYER_HEALTH);

    private final ResourceLocation name;

    private LegacyNamedGuiOverlay(ResourceLocation name) {
        this.name = name;
    }

    public ResourceLocation id() {
        return name;
    }

    public static LegacyNamedGuiOverlay of(ResourceLocation name) {
        return BY_NAME.computeIfAbsent(name, LegacyNamedGuiOverlay::new);
    }
}
