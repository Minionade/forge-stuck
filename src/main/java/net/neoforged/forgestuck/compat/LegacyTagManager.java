package net.neoforged.forgestuck.compat;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.core.Registry;
import net.minecraft.tags.TagKey;

public interface LegacyTagManager<T> {
    LegacyTag<T> getTag(TagKey<T> tag);

    Optional<LegacyReverseTag<T>> getReverseTag(T value);

    static <T> LegacyTagManager<T> create(Registry<T> registry) {
        return new LegacyTagViews.Manager<>(registry);
    }
}

final class LegacyTagViews {
    private LegacyTagViews() {}

    static final class Manager<T> implements LegacyTagManager<T> {
        private final Registry<T> registry;
        private final ConcurrentMap<TagKey<T>, LegacyTag<T>> tags = new ConcurrentHashMap<>();

        Manager(Registry<T> registry) {
            this.registry = registry;
        }

        @Override
        public LegacyTag<T> getTag(TagKey<T> tag) {
            return tags.computeIfAbsent(tag, key -> new TagView<>(registry, key));
        }

        @Override
        public Optional<LegacyReverseTag<T>> getReverseTag(T value) {
            return registry.getResourceKey(value).map(ignored -> new ReverseTagView<>(registry, value));
        }
    }

    private record TagView<T>(Registry<T> registry, TagKey<T> key) implements LegacyTag<T> {
        @Override
        public java.util.stream.Stream<T> stream() {
            return registry.getTag(key).stream().flatMap(net.minecraft.core.HolderSet::stream)
                    .map(net.minecraft.core.Holder::value);
        }
    }

    private record ReverseTagView<T>(Registry<T> registry, T value) implements LegacyReverseTag<T> {
        @Override
        public boolean containsTag(TagKey<T> tag) {
            return registry.getResourceKey(value)
                    .flatMap(registry::getHolder)
                    .flatMap(holder -> registry.getTag(tag).map(holders -> holders.contains(holder)))
                    .orElse(false);
        }

        @Override
        public java.util.stream.Stream<TagKey<T>> getTagKeys() {
            return registry.getTagNames().filter(this::containsTag);
        }
    }
}
