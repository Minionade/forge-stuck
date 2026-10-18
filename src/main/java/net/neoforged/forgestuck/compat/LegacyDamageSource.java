package net.neoforged.forgestuck.compat;

import net.minecraft.world.damagesource.DamageSource;

/** Adapts a removed damage-source query. */
public final class LegacyDamageSource {
    private LegacyDamageSource() {}

    public static boolean isCreativePlayer(DamageSource source) {
        return source.isCreativePlayer();
    }
}
