package net.neoforged.forgestuck.compat;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

/** Adapts Forge's removed damage-effect query to the damage-type tag API. */
public final class LegacyDamageSource {
    private LegacyDamageSource() {}

    public static boolean bypassesEffects(DamageSource source) {
        return source.is(DamageTypeTags.BYPASSES_EFFECTS);
    }
}
