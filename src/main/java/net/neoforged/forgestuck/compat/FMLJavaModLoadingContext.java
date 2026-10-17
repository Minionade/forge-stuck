package net.neoforged.forgestuck.compat;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
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
        LegacyTickBridge.install();
        return INSTANCE;
    }

    public IEventBus getModEventBus() {
        IEventBus bus = ModLoadingContext.get().getActiveContainer().getEventBus();
        if (bus == null) bus = busOfCallerModule();
        if (bus == null) throw new IllegalStateException("The active mod container has no event bus");
        return bus;
    }

    /**
     * Some language providers (e.g. kotori_scala) clear the active container before touching the mod object, in which
     * case FML falls back to the "minecraft" container, which has no bus. Resolve the mod from the calling class's
     * module instead, which is the module of the mod jar that owns the call site.
     */
    private static IEventBus busOfCallerModule() {
        Module self = FMLJavaModLoadingContext.class.getModule();
        String callerModule = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(frames -> frames.map(StackWalker.StackFrame::getDeclaringClass)
                        .map(Class::getModule)
                        .filter(m -> m != self && m.isNamed())
                        .map(Module::getName)
                        .findFirst())
                .orElse(null);
        if (callerModule == null) return null;
        ModList list = ModList.get();
        if (list == null) return null;
        return list.getMods().stream()
                .filter(info -> callerModule.equals(info.getOwningFile().moduleName()))
                .map(info -> list.getModContainerById(info.getModId()).map(ModContainer::getEventBus).orElse(null))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
