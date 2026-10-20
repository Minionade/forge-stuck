package net.neoforged.forgestuck.compat;

import net.minecraft.network.chat.Component;

/** Stand-in for the 1.20.1 {@code CommandRuntimeException}, which 1.21.1 removed. */
public class LegacyCommandRuntimeException extends RuntimeException {
    private final Component component;

    public LegacyCommandRuntimeException(Component component) {
        super(component.getString());
        this.component = component;
    }

    public Component getComponent() {
        return component;
    }
}
