package net.neoforged.forgestuck.compat;

import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Target of redirected Forge 1.20.1 {@code RegisterCapabilitiesEvent.register(Class)} calls. Forge's interface
 * based capability tokens cannot be registered by NeoForge's object-specific
 * {@code BlockCapability}/{@code ItemCapability}/{@code EntityCapability} API without translating each provider.
 * Fail explicitly rather than silently pretending the legacy capability was registered.
 */
public final class LegacyCapabilities {
    private LegacyCapabilities() {}

    public static void register(RegisterCapabilitiesEvent event, Class<?> type) {
        java.util.Objects.requireNonNull(type, "capability type");
        UnsupportedForgeApi.fail("net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent.register("
                + type.getName() + ")", "legacy capability registration");
    }
}