package net.neoforged.forgestuck.compat;

public enum LegacyItemStackTooltipPart {
    ENCHANTMENTS,
    MODIFIERS,
    UNBREAKABLE,
    CAN_DESTROY,
    CAN_PLACE,
    ADDITIONAL,
    DYE,
    UPGRADES,
    STORED_ENCHANTMENTS;

    public int getMask() {
        return 1 << ordinal();
    }
}
