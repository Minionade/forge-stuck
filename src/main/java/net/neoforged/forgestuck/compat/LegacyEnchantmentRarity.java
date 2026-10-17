package net.neoforged.forgestuck.compat;

/** Stand-in for the removed {@code Enchantment.Rarity} enum; a name-keyed token with the 1.20.1 constants. */
public final class LegacyEnchantmentRarity {
    public static final LegacyEnchantmentRarity COMMON = new LegacyEnchantmentRarity("COMMON", 10);
    public static final LegacyEnchantmentRarity UNCOMMON = new LegacyEnchantmentRarity("UNCOMMON", 5);
    public static final LegacyEnchantmentRarity RARE = new LegacyEnchantmentRarity("RARE", 2);
    public static final LegacyEnchantmentRarity VERY_RARE = new LegacyEnchantmentRarity("VERY_RARE", 1);

    private final String name;
    private final int weight;

    private LegacyEnchantmentRarity(String name, int weight) {
        this.name = name;
        this.weight = weight;
    }

    public int getWeight() {
        return weight;
    }

    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
