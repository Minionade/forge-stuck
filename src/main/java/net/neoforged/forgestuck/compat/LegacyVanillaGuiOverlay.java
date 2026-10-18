package net.neoforged.forgestuck.compat;

public enum LegacyVanillaGuiOverlay {
    CROSSHAIR(LegacyNamedGuiOverlay.CROSSHAIR),
    HOTBAR(LegacyNamedGuiOverlay.HOTBAR),
    PLAYER_HEALTH(LegacyNamedGuiOverlay.PLAYER_HEALTH);

    private final LegacyNamedGuiOverlay overlay;

    LegacyVanillaGuiOverlay(LegacyNamedGuiOverlay overlay) {
        this.overlay = overlay;
    }

    public LegacyNamedGuiOverlay type() {
        return overlay;
    }
}
