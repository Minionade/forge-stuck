package net.neoforged.forgestuck.locator;

import net.neoforged.forgestuck.transformer.jar.TransformProgress;

/**
 * Turns transformer events into status lines such as {@code Forge Stuck: transforming foo.jar (3/19)} and forwards
 * them to a {@link StartupDisplay}. Counting is by jar visited by the locator ({@link #next}).
 */
public final class StartupProgress implements TransformProgress {
    private final StartupDisplay display;
    private final int total;
    private int index;
    private int transformed;
    private int cached;
    private long startNanos;
    private long totalMillis;

    public StartupProgress(StartupDisplay display, int total) {
        this.display = display;
        this.total = total;
    }

    public void begin() {
        startNanos = System.nanoTime();
        display.begin("Forge Stuck", total);
        display.message("Forge Stuck: scanning " + total + " mod jar(s)");
    }

    /** Called by the locator when it starts working on the next jar of the mods folder. */
    public void next(String jar) {
        index++;
        display.update(index - 1, "Forge Stuck: checking " + jar + " (" + index + "/" + total + ")");
    }

    @Override
    public void cached(String jar) {
        cached++;
        display.update(index - 1, "Forge Stuck: using cached " + jar + " (" + index + "/" + total + ")");
    }

    @Override
    public void transforming(String jar) {
        display.update(index - 1, "Forge Stuck: transforming " + jar + " (" + index + "/" + total + ")");
    }

    @Override
    public void nested(String outer, String nestedEntry) {
        String name = nestedEntry.substring(nestedEntry.lastIndexOf('/') + 1);
        display.message("Forge Stuck: scanning nested jars: " + name + " in " + outer);
    }

    @Override
    public void transformed(String jar, long millis) {
        transformed++;
        totalMillis += millis;
        display.message("Forge Stuck: transformed " + jar + " in " + millis + " ms");
    }

    public void end() {
        long wall = (System.nanoTime() - startNanos) / 1_000_000;
        display.end("Forge Stuck: done (" + transformed + " transformed, " + cached + " cached, " + wall + " ms)");
    }

    public int transformedCount() {
        return transformed;
    }

    public int cachedCount() {
        return cached;
    }

    public long transformMillis() {
        return totalMillis;
    }
}
