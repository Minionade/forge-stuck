package net.neoforged.forgestuck.locator;

/** Where Forge Stuck startup status lines go; implementations must never throw. */
public interface StartupDisplay {
    /** Starts a progress bar with {@code total} steps. */
    void begin(String title, int total);

    /** Moves the bar to {@code position} (0-based count of finished steps) and shows {@code text}. */
    void update(int position, String text);

    /** Shows a message without moving the bar. */
    void message(String text);

    /** Completes and removes the bar. */
    void end(String text);
}
