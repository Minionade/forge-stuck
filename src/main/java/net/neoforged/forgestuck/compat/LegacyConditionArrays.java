package net.neoforged.forgestuck.compat;

import java.util.Arrays;
import java.util.List;
import net.neoforged.neoforge.common.conditions.ICondition;

public final class LegacyConditionArrays {
    private LegacyConditionArrays() {}

    public static List<ICondition> asList(ICondition[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("Values must not be empty");
        }
        for (ICondition value : values) {
            if (value == null) {
                throw new IllegalArgumentException("Value must not be null");
            }
        }
        return Arrays.asList(values);
    }
}
