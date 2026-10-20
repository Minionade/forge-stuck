package net.neoforged.forgestuck.compat;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/** Adapts Forge's event result type to NeoForge's mob-effect applicability result. */
public final class LegacyMobEffectApplicable {
    private static final Map<MobEffectEvent.Applicable, MobEffectEvent.Applicable.Result> CANCELED =
            Collections.synchronizedMap(new WeakHashMap<>());

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

    public static boolean isCanceled(MobEffectEvent.Applicable event) {
        return CANCELED.containsKey(event);
    }

    public static void setCanceled(MobEffectEvent.Applicable event, boolean canceled) {
        if (canceled) {
            CANCELED.putIfAbsent(event, event.getResult());
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        } else {
            MobEffectEvent.Applicable.Result previous = CANCELED.remove(event);
            if (previous != null) event.setResult(previous);
        }
    }

    public static boolean isCanceled(MobEffectEvent.Added event) {
        return false;
    }
}
