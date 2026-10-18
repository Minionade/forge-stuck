package net.neoforged.forgestuck.compat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;

/**
 * Link-compatible representation of Forge's capability attachment event. NeoForge uses registered capabilities
 * instead of this event, so Forge listeners are not fired by the runtime.
 */
public class LegacyAttachCapabilitiesEvent<T> extends Event {
    private final T object;
    private final Map<ResourceLocation, LegacyICapabilityProvider> capabilities = new LinkedHashMap<>();
    private final List<Runnable> listeners = new java.util.ArrayList<>();

    public LegacyAttachCapabilitiesEvent(Class<T> type, T object) {
        this.object = object;
    }

    public T getObject() {
        return object;
    }

    public void addCapability(ResourceLocation key, LegacyICapabilityProvider provider) {
        if (capabilities.containsKey(key)) {
            throw new IllegalStateException("Duplicate Capability Key: " + key + " " + provider);
        }
        capabilities.put(key, provider);
    }

    public Map<ResourceLocation, LegacyICapabilityProvider> getCapabilities() {
        return Collections.unmodifiableMap(capabilities);
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public List<Runnable> getListeners() {
        return Collections.unmodifiableList(listeners);
    }
}
