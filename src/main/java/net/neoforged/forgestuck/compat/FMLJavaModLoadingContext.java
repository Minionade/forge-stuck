package net.neoforged.forgestuck.compat;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;

/**
 * Stand-in for Forge 1.20.1's {@code FMLJavaModLoadingContext}. NeoForge hands the mod event bus to the mod
 * constructor, so legacy mods that call {@code FMLJavaModLoadingContext.get().getModEventBus()} get the bus of the
 * active mod container instead. Only valid while that mod is being constructed, like in Forge.
 */
public final class FMLJavaModLoadingContext {
    private static final FMLJavaModLoadingContext INSTANCE = new FMLJavaModLoadingContext();

    private FMLJavaModLoadingContext() {}

    public static FMLJavaModLoadingContext get() {
        return INSTANCE;
    }

    public IEventBus getModEventBus() {
        IEventBus bus = ModLoadingContext.get().getActiveContainer().getEventBus();
        if (bus == null) throw new IllegalStateException("The active mod container has no event bus");
        return bus;
    }
}
