package net.neoforged.forgestuck.compat;

import net.minecraft.locale.Language;

public final class LegacyForgeI18n {
    private LegacyForgeI18n() {}

    public static String getPattern(String key) {
        return Language.getInstance().getOrDefault(key);
    }
}
