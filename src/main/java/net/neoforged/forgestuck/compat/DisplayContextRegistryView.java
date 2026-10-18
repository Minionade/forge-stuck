package net.neoforged.forgestuck.compat;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

/** Read-only view of Forge's display-context registry, whose entries are enum extensions on NeoForge 1.21.1. */
final class DisplayContextRegistryView implements IForgeRegistry<ItemDisplayContext> {
    static final ResourceKey<Registry<ItemDisplayContext>> KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("forge", "display_contexts"));
    static final DisplayContextRegistryView INSTANCE = new DisplayContextRegistryView();

    private DisplayContextRegistryView() {}

    private static Map<ResourceLocation, ItemDisplayContext> contexts() {
        return Arrays.stream(ItemDisplayContext.values()).filter(ItemDisplayContext::isModded)
                .collect(Collectors.toUnmodifiableMap(
                        context -> ResourceLocation.parse(context.getSerializedName()), context -> context));
    }

    @Override
    public ResourceKey<? extends Registry<ItemDisplayContext>> getRegistryKey() {
        return KEY;
    }

    @Override
    public ResourceLocation getRegistryName() {
        return KEY.location();
    }

    @Override
    public boolean containsKey(ResourceLocation key) {
        return contexts().containsKey(key);
    }

    @Override
    public boolean containsValue(ItemDisplayContext value) {
        return value != null && value.isModded() && contexts().containsValue(value);
    }

    @Override
    public ItemDisplayContext getValue(ResourceLocation key) {
        return contexts().get(key);
    }

    @Override
    public ResourceLocation getKey(ItemDisplayContext value) {
        return containsValue(value) ? ResourceLocation.parse(value.getSerializedName()) : null;
    }

    @Override
    public Optional<ResourceKey<ItemDisplayContext>> getResourceKey(ItemDisplayContext value) {
        ResourceLocation id = getKey(value);
        return id == null ? Optional.empty() : Optional.of(ResourceKey.create(KEY, id));
    }

    @Override
    public Set<ResourceLocation> getKeys() {
        return contexts().keySet();
    }

    @Override
    public Collection<ItemDisplayContext> getValues() {
        return contexts().values();
    }

    @Override
    public Set<Map.Entry<ResourceKey<ItemDisplayContext>, ItemDisplayContext>> getEntries() {
        return contexts().entrySet().stream()
                .map(entry -> Map.entry(ResourceKey.create(KEY, entry.getKey()), entry.getValue()))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void register(ResourceLocation key, ItemDisplayContext value) {
        if (!containsKey(key) || contexts().get(key) != value) {
            throw new IllegalArgumentException("Display context " + key
                    + " is not a generated NeoForge ItemDisplayContext enum extension");
        }
    }
}
