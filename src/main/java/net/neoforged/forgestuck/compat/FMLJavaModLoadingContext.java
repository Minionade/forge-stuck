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
        ModContainer active = ModLoadingContext.get().getActiveContainer();
        if (active == null || active.getModId().equals("minecraft")) active = callerModContainer();
        IEventBus bus = active == null ? null : active.getEventBus();
        if (bus == null) throw new IllegalStateException("The active mod container has no event bus");
        return bus;
    }

    /**
     * Some language providers (e.g. kotori_scala) clear the active container before touching mod code, leaving FML's
     * fallback container as "minecraft". Resolve the mod from the outermost calling mod module; this also handles
     * library code (such as BdLib registries) called while a dependent mod is being initialized.
     */
    static ModContainer callerModContainer() {
        Module self = FMLJavaModLoadingContext.class.getModule();
        ModList list = ModList.get();
        if (list == null) return null;
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(frames -> frames.map(StackWalker.StackFrame::getDeclaringClass)
                        .map(Class::getModule)
                        .filter(module -> module != self && module.isNamed())
                        .map(Module::getName)
                        .map(name -> list.getMods().stream()
                                .filter(info -> name.equals(info.getOwningFile().moduleName()))
                                .map(info -> list.getModContainerById(info.getModId()).orElse(null))
                                .filter(java.util.Objects::nonNull)
                                .findFirst()
                                .orElse(null))
                        .filter(container -> container != null && !container.getModId().equals("minecraft"))
                        .reduce((first, last) -> last))
                .orElse(null);
    }
}
