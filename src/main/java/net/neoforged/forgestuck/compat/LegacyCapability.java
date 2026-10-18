package net.neoforged.forgestuck.compat;

public final class LegacyCapability<T> {
    private final Class<T> type;

    LegacyCapability(Class<T> type) {
        this.type = type;
    }

    public String getName() {
        return type.getName();
    }

    public LazyOptional<T> orEmpty(LegacyCapability<T> capability, LazyOptional<T> optional) {
        return this == capability ? optional : LazyOptional.empty();
    }
}
