package net.neoforged.forgestuck.locator;

import net.neoforged.fml.loading.progress.ProgressMeter;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shows Forge Stuck status in FML's early loading window using the loader 4.0.x API verified with javap
 * ({@code StartupNotificationManager.addProgressBar/popBar/addModMessage}, {@code ProgressMeter.label/setAbsolute/
 * complete}). Every call is guarded: a missing or changed API (or no early window) only logs once at debug level and
 * the display degrades to log-only.
 */
public final class FmlStartupDisplay implements StartupDisplay {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgestuck");

    private ProgressMeter bar;
    private boolean broken;

    @Override
    public void begin(String title, int total) {
        guard(() -> bar = StartupNotificationManager.addProgressBar(title, Math.max(1, total)));
    }

    @Override
    public void update(int position, String text) {
        guard(() -> {
            if (bar != null) {
                bar.setAbsolute(position);
                bar.label(text);
            }
            StartupNotificationManager.addModMessage(text);
        });
    }

    @Override
    public void message(String text) {
        guard(() -> StartupNotificationManager.addModMessage(text));
    }

    @Override
    public void end(String text) {
        guard(() -> {
            StartupNotificationManager.addModMessage(text);
            if (bar != null) {
                bar.complete();
                StartupNotificationManager.popBar(bar);
                bar = null;
            }
        });
    }

    private void guard(Runnable action) {
        if (broken) return;
        try {
            action.run();
        } catch (Exception | LinkageError e) {
            broken = true;
            LOGGER.debug("[Forge Stuck!!] early-window progress unavailable, using log output only: {}", e.toString());
        }
    }
}
