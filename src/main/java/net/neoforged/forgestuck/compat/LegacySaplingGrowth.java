package net.neoforged.forgestuck.compat;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.event.level.BlockGrowFeatureEvent;

/** Adapts Forge's result-based growth veto to the actual NeoForge growth event. */
public final class LegacySaplingGrowth {
    private LegacySaplingGrowth() {}
    private static final Map<BlockGrowFeatureEvent, LegacyEventResult> RESULTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    public static RandomSource getRandomSource(BlockGrowFeatureEvent event) {
        return event.getRandom();
    }

    public static void setResult(BlockGrowFeatureEvent event, LegacyEventResult result) {
        Objects.requireNonNull(result, "result");
        RESULTS.put(event, result);
        event.setCanceled(result == LegacyEventResult.DENY);
    }

    public static LegacyEventResult getResult(BlockGrowFeatureEvent event) {
        if (event.isCanceled()) return LegacyEventResult.DENY;
        return RESULTS.get(event) == LegacyEventResult.ALLOW
                ? LegacyEventResult.ALLOW : LegacyEventResult.DEFAULT;
    }

    public static boolean hasResult(BlockGrowFeatureEvent event) { return true; }
    public static boolean isCancelable(BlockGrowFeatureEvent event) { return true; }
}
