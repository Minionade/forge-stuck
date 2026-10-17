package net.neoforged.forgestuck.compat;

import java.io.Serializable;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * Stand-in for Forge 1.20.1's {@code DistExecutor}, which NeoForge 1.21.1 removed (mods use
 * {@code FMLEnvironment.dist} or separate classes instead). Behaviour follows Forge: the supplied code runs only on
 * the matching {@link Dist} and {@code call*} variants return {@code null} on the other side. Forge's dev-only
 * "safe referent" validation (which only throws for a misuse of the API) is not reproduced.
 */
public final class DistExecutor {
    private DistExecutor() {}

    public static <T> T callWhenOn(Dist dist, Supplier<Callable<T>> toRun) {
        return unsafeCallWhenOn(dist, toRun);
    }

    public static <T> T unsafeCallWhenOn(Dist dist, Supplier<Callable<T>> toRun) {
        if (dist == FMLEnvironment.dist) {
            try {
                return toRun.get().call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    public static <T> T safeCallWhenOn(Dist dist, Supplier<SafeCallable<T>> toRun) {
        return unsafeCallWhenOn(dist, toRun::get);
    }

    public static void runWhenOn(Dist dist, Supplier<Runnable> toRun) {
        unsafeRunWhenOn(dist, toRun);
    }

    public static void unsafeRunWhenOn(Dist dist, Supplier<Runnable> toRun) {
        if (dist == FMLEnvironment.dist) toRun.get().run();
    }

    public static void safeRunWhenOn(Dist dist, Supplier<SafeRunnable> toRun) {
        if (dist == FMLEnvironment.dist) toRun.get().run();
    }

    public static <T> T runForDist(Supplier<Supplier<T>> clientTarget, Supplier<Supplier<T>> serverTarget) {
        return unsafeRunForDist(clientTarget, serverTarget);
    }

    public static <T> T unsafeRunForDist(Supplier<Supplier<T>> clientTarget, Supplier<Supplier<T>> serverTarget) {
        return switch (FMLEnvironment.dist) {
            case CLIENT -> clientTarget.get().get();
            case DEDICATED_SERVER -> serverTarget.get().get();
        };
    }

    public static <T> T safeRunForDist(Supplier<SafeSupplier<T>> clientTarget, Supplier<SafeSupplier<T>> serverTarget) {
        return switch (FMLEnvironment.dist) {
            case CLIENT -> clientTarget.get().get();
            case DEDICATED_SERVER -> serverTarget.get().get();
        };
    }

    public interface SafeReferent {}

    public interface SafeCallable<T> extends SafeReferent, Callable<T>, Serializable {}

    public interface SafeSupplier<T> extends SafeReferent, Supplier<T>, Serializable {}

    public interface SafeRunnable extends SafeReferent, Runnable, Serializable {}
}
