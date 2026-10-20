package net.neoforged.forgestuck.compat;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.crafting.CraftingBookCategory;

/** Recreates the legacy typed enum codec for an enum whose 1.21 codec is exposed only as {@code Codec}. */
public final class LegacyEnumCodecs {
    private LegacyEnumCodecs() {}

    public static StringRepresentable.EnumCodec<CraftingBookCategory> craftingBookCategoryCodec() {
        return StringRepresentable.fromEnum(CraftingBookCategory::values);
    }
}
