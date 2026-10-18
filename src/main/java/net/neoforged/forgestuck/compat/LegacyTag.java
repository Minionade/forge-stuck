package net.neoforged.forgestuck.compat;

import java.util.stream.Stream;

public interface LegacyTag<T> {
    Stream<T> stream();
}
