package net.neoforged.forgestuck.compat;

import net.minecraft.network.chat.TextColor;

public final class LegacyTextColorShim {
    private LegacyTextColorShim() {}

    public static TextColor parseColor(String value) {
        return TextColor.parseColor(value).result().orElse(null);
    }
}
