package net.neoforged.forgestuck.compat;

import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/** Adapts Forge's event result type to NeoForge's mob-effect applicability result. */
public final class LegacyMobEffectApplicable {
    private LegacyMobEffectApplicable() {}

    public static void setResult(MobEffectEvent.Applicable event, LegacyEventResult result) {
        MobEffectEvent.Applicable.Result adapted = switch (result) {
            case DENY -> MobEffectEvent.Applicable.Result.DO_NOT_APPLY;
            case DEFAULT -> MobEffectEvent.Applicable.Result.DEFAULT;
            case ALLOW -> MobEffectEvent.Applicable.Result.APPLY;
        };
        event.setResult(adapted);
    }

    public static LegacyEventResult getResult(MobEffectEvent.Applicable event) {
        return switch (event.getResult()) {
            case DO_NOT_APPLY -> LegacyEventResult.DENY;
            case DEFAULT -> LegacyEventResult.DEFAULT;
            case APPLY -> LegacyEventResult.ALLOW;
        };
    }
}
