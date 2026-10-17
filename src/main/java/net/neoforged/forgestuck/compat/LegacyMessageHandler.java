package net.neoforged.forgestuck.compat;

/**
 * Return type of the legacy {@code SimpleChannel.registerMessage}
 * ({@code IndexedMessageCodec.MessageHandler}). Only the registered index is kept; the legacy builder-style
 * members are not mapped and therefore fail at the call.
 */
public final class LegacyMessageHandler {
    private final int index;

    LegacyMessageHandler(int index) {
        this.index = index;
    }

    public int index() {
        return index;
    }
}