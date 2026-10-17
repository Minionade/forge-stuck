package net.neoforged.forgestuck.transformer.jar;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;

/** Per-jar record of data-pack resource migrations and of resources that were reported but left untouched. */
public final class ResourceRewriteReport {
    private final List<String> renamed = new ArrayList<>();
    private final List<String> rewritten = new ArrayList<>();
    private final List<String> unsupported = new ArrayList<>();

    void renamed(String from, String to) {
        renamed.add(from + " -> " + to);
    }

    void rewritten(String path, String what) {
        rewritten.add(path + ": " + what);
    }

    void unsupported(String path, String why) {
        unsupported.add(path + ": " + why);
    }

    public List<String> renamed() {
        return List.copyOf(renamed);
    }

    public List<String> rewritten() {
        return List.copyOf(rewritten);
    }

    /** Resources that were not migrated (malformed, ambiguous or colliding); their bytes are preserved. */
    public List<String> unsupported() {
        return List.copyOf(unsupported);
    }

    void log(Logger logger, String tag, String jarName) {
        if (!renamed.isEmpty() || !rewritten.isEmpty() || !unsupported.isEmpty()) {
            logger.info("{} {} resource migration: {} entry rename(s), {} JSON rewrite(s), {} left untouched", tag,
                    jarName, renamed.size(), rewritten.size(), unsupported.size());
        }
        unsupported.forEach(u -> logger.warn("{} {} resource left unmigrated (preserved as-is): {}", tag, jarName, u));
    }
}
