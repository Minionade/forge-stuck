package net.neoforged.forgestuck.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Persists powered-machine progress and marks its chunk dirty when the counter advances. */
public final class LegacyPoweredMachineProgress {
    private static final String WORK_COUNTER_KEY = "forgestuck:powered_machine_work_counter";
    private static final String STEPS_PER_CYCLE_KEY = "forgestuck:powered_machine_steps_per_cycle";
    private static final Map<BlockEntity, SavedProgress> RESTORED_PROGRESS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private LegacyPoweredMachineProgress() {
    }

    public static void save(BlockEntity tile, CompoundTag tag, HolderLookup.Provider registries) {
        writeTankManager(tile, tag, registries);
        tag.putInt(WORK_COUNTER_KEY, getWorkCounter(tile));
        tag.putInt(STEPS_PER_CYCLE_KEY, getIntField(tile, "stepsPerWorkCycle"));
    }

    public static void load(BlockEntity tile, CompoundTag tag, HolderLookup.Provider registries) {
        readTankManager(tile, tag, registries);
        if (tag.contains(WORK_COUNTER_KEY, CompoundTag.TAG_INT)
                && tag.contains(STEPS_PER_CYCLE_KEY, CompoundTag.TAG_INT)) {
            SavedProgress progress = new SavedProgress(
                    tag.getInt(WORK_COUNTER_KEY), tag.getInt(STEPS_PER_CYCLE_KEY));
            setIntField(tile, "stepsPerWorkCycle", progress.stepsPerCycle());
            setWorkCounter(tile, progress.workCounter());
            RESTORED_PROGRESS.put(tile, progress);
        }
    }

    public static void markChangedIfProgressAdvanced(BlockEntity tile, int previousProgress) {
        if (getWorkCounter(tile) != previousProgress) {
            tile.setChanged();
        }
    }

    public static void setStepsPerWorkCycle(BlockEntity tile, int stepsPerCycle) {
        SavedProgress restored = RESTORED_PROGRESS.remove(tile);
        if (restored != null && restored.stepsPerCycle() == stepsPerCycle) {
            return;
        }
        invokeIntSetter(tile, "setStepsPerWorkCycle", stepsPerCycle);
    }

    private static int getWorkCounter(BlockEntity tile) {
        try {
            Method method = tile.getClass().getMethod("getWorkCounter");
            Object counter = method.invoke(tile);
            if (counter instanceof Integer value) {
                return value;
            }
            throw new IllegalStateException("Powered tile returned a non-integer work counter");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to access powered tile work counter", exception);
        }
    }

    private static void setWorkCounter(BlockEntity tile, int counter) {
        setIntField(tile, "workCounter", counter);
    }

    private static int getIntField(BlockEntity tile, String name) {
        for (Class<?> type = tile.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.getInt(tile);
            } catch (NoSuchFieldException ignored) {
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to read powered tile field " + name, exception);
            }
        }
        throw new IllegalStateException("Powered tile does not expose field " + name);
    }

    private static void setIntField(BlockEntity tile, String name, int value) {
        for (Class<?> type = tile.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.setInt(tile, value);
                return;
            } catch (NoSuchFieldException ignored) {
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to restore powered tile field " + name, exception);
            }
        }
        throw new IllegalStateException("Powered tile does not expose field " + name);
    }

    private static void invokeIntSetter(BlockEntity tile, String name, int value) {
        try {
            tile.getClass().getMethod(name, int.class).invoke(tile, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to invoke powered tile method " + name, exception);
        }
    }

    private static void writeTankManager(
            BlockEntity tile, CompoundTag tag, HolderLookup.Provider registries) {
        Object manager = tankManager(tile);
        if (manager == null) {
            return;
        }
        try {
            manager.getClass().getMethod("write", CompoundTag.class, HolderLookup.Provider.class)
                    .invoke(manager, tag, registries);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to save powered tile fluid tanks", exception);
        }
    }

    private static void readTankManager(
            BlockEntity tile, CompoundTag tag, HolderLookup.Provider registries) {
        Object manager = tankManager(tile);
        if (manager == null) {
            return;
        }
        try {
            manager.getClass().getMethod("read", CompoundTag.class, HolderLookup.Provider.class)
                    .invoke(manager, tag, registries);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to load powered tile fluid tanks", exception);
        }
    }

    private static Object tankManager(BlockEntity tile) {
        try {
            return tile.getClass().getMethod("getTankManager").invoke(tile);
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to access powered tile fluid tanks", exception);
        }
    }

    private record SavedProgress(int workCounter, int stepsPerCycle) {
    }
}
