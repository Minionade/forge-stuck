package net.neoforged.forgestuck.compat;

import java.util.function.Function;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.event.IModBusEvent;

public final class LegacyModLoader {
    private static final LegacyModLoader INSTANCE = new LegacyModLoader();

    private LegacyModLoader() {}

    public static LegacyModLoader get() {
        return INSTANCE;
    }

    public <T extends Event & IModBusEvent> void postEvent(T event) {
        ModLoader.postEvent(event);
    }

    public <T extends Event & IModBusEvent> void runEventGenerator(Function<ModContainer, T> generator) {
        ModLoader.runEventGenerator(generator);
    }
}
