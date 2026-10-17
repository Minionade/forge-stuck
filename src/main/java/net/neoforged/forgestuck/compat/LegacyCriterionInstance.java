package net.neoforged.forgestuck.compat;

import java.util.Optional;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;

/**
 * Stand-in for 1.20's {@code AbstractCriterionTriggerInstance}, removed in 1.21 when trigger instances became codec
 * records. Lets legacy trigger instance classes link; the JSON (de)serialization they implement is not used by 1.21.
 */
public class LegacyCriterionInstance implements SimpleCriterionTrigger.SimpleInstance {
    private final ContextAwarePredicate player;

    public LegacyCriterionInstance(ContextAwarePredicate player) {
        this.player = player;
    }

    @Override
    public Optional<ContextAwarePredicate> player() {
        return Optional.ofNullable(player);
    }
}
