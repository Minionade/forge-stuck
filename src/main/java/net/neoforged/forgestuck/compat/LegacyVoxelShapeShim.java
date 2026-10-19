package net.neoforged.forgestuck.compat;

import java.lang.reflect.Field;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Accesses the legacy-visible voxel backing shape, which is protected in 1.21.1. */
public final class LegacyVoxelShapeShim {
    private static final Field SHAPE = findShapeField();

    private LegacyVoxelShapeShim() {}

    public static DiscreteVoxelShape getDiscreteShape(VoxelShape shape) {
        try {
            return (DiscreteVoxelShape) SHAPE.get(shape);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot access VoxelShape backing data", e);
        }
    }

    private static Field findShapeField() {
        try {
            Field field = VoxelShape.class.getDeclaredField("shape");
            if (!field.trySetAccessible()) {
                throw new IllegalStateException("Cannot access VoxelShape backing data");
            }
            return field;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("VoxelShape backing data field is unavailable", e);
        }
    }
}
