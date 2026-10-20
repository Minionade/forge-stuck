package net.neoforged.forgestuck.compat;

import java.util.Optional;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;

/**
 * Stand-in for 1.20's {@code AbstractCriterionTriggerInstance}, removed in 1.21 when trigger instances became codec
 * records. Lets legacy trigger instance classes link; the JSON (de)serialization they implement is not used by 1.21.
 */
public class LegacyCriterionInstance implements SimpleCriterionTrigger.SimpleInstance {
    private final ResourceLocation criterion;
    private final ContextAwarePredicate player;

    public LegacyCriterionInstance(ResourceLocation criterion, ContextAwarePredicate player) {
        this.criterion = criterion;
        this.player = player;
    }

    public ResourceLocation getCriterion() {
        return criterion;
    }

    @Override
    public Optional<ContextAwarePredicate> player() {
        return Optional.ofNullable(player);
    }

    public JsonObject m_7683_(Object ignoredLegacySerializationContext) {
        JsonObject json = new JsonObject();
        if (player != null) {
            json.add("player", ContextAwarePredicate.CODEC.encodeStart(JsonOps.INSTANCE, player)
                    .result().orElseThrow(() ->
                            new IllegalStateException("Could not encode legacy criterion player predicate")));
        }
        return json;
    }
}
