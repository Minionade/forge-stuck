package net.neoforged.forgestuck.compat;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;

public final class LegacyCriticalHitEvent {
    private static final Map<CriticalHitEvent, LegacyEventResult> RESULTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private LegacyCriticalHitEvent() {}

    public static CriticalHitEvent getCriticalHit(Player player, Entity target, boolean vanillaCritical,
            float damageModifier) {
        return CommonHooks.fireCriticalHit(player, target, vanillaCritical, damageModifier);
    }

    public static float getDamageModifier(CriticalHitEvent event) {
        return event.getDamageMultiplier();
    }

    public static void setDamageModifier(CriticalHitEvent event, float damageModifier) {
        event.setDamageMultiplier(damageModifier);
    }

    public static LegacyEventResult getResult(CriticalHitEvent event) {
        LegacyEventResult explicitResult = RESULTS.get(event);
        if (explicitResult != null) return explicitResult;
        if (event.isCriticalHit() == event.isVanillaCritical()) return LegacyEventResult.DEFAULT;
        return event.isCriticalHit() ? LegacyEventResult.ALLOW : LegacyEventResult.DENY;
    }

    public static void setResult(CriticalHitEvent event, LegacyEventResult result) {
        LegacyEventResult checkedResult = Objects.requireNonNull(result, "result");
        RESULTS.put(event, checkedResult);
        switch (checkedResult) {
            case ALLOW -> event.setCriticalHit(true);
            case DENY -> event.setCriticalHit(false);
            case DEFAULT -> event.setCriticalHit(event.isVanillaCritical());
        }
    }
}
