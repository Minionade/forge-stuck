package net.neoforged.forgestuck.compat;

/** Stand-in for the removed {@code EnchantmentCategory} enum; a name-keyed token with the 1.20.1 constants. */
public final class LegacyEnchantmentCategory {
    public static final LegacyEnchantmentCategory ARMOR = new LegacyEnchantmentCategory("ARMOR");
    public static final LegacyEnchantmentCategory ARMOR_FEET = new LegacyEnchantmentCategory("ARMOR_FEET");
    public static final LegacyEnchantmentCategory ARMOR_LEGS = new LegacyEnchantmentCategory("ARMOR_LEGS");
    public static final LegacyEnchantmentCategory ARMOR_CHEST = new LegacyEnchantmentCategory("ARMOR_CHEST");
    public static final LegacyEnchantmentCategory ARMOR_HEAD = new LegacyEnchantmentCategory("ARMOR_HEAD");
    public static final LegacyEnchantmentCategory WEAPON = new LegacyEnchantmentCategory("WEAPON");
    public static final LegacyEnchantmentCategory DIGGER = new LegacyEnchantmentCategory("DIGGER");
    public static final LegacyEnchantmentCategory FISHING_ROD = new LegacyEnchantmentCategory("FISHING_ROD");
    public static final LegacyEnchantmentCategory TRIDENT = new LegacyEnchantmentCategory("TRIDENT");
    public static final LegacyEnchantmentCategory BREAKABLE = new LegacyEnchantmentCategory("BREAKABLE");
    public static final LegacyEnchantmentCategory BOW = new LegacyEnchantmentCategory("BOW");
    public static final LegacyEnchantmentCategory WEARABLE = new LegacyEnchantmentCategory("WEARABLE");
    public static final LegacyEnchantmentCategory CROSSBOW = new LegacyEnchantmentCategory("CROSSBOW");
    public static final LegacyEnchantmentCategory VANISHABLE = new LegacyEnchantmentCategory("VANISHABLE");

    private final String name;

    private LegacyEnchantmentCategory(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
