package net.neoforged.forgestuck.compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Resends TConstruct's dynamic multiblock dimensions before its menu syncs data slots. */
public final class LegacyTConstructStructureSync {
    private static final String STRUCTURE_DATA =
            "slimeknights.tconstruct.smeltery.block.entity.multiblock.HeatingStructureMultiblock$StructureData";
    private static final String STRUCTURE_PACKET =
            "slimeknights.tconstruct.smeltery.network.StructureUpdatePacket";
    private static final String TINKER_NETWORK = "slimeknights.tconstruct.common.network.TinkerNetwork";

    private LegacyTConstructStructureSync() {}

    public static void resendStructure(Object value) {
        if (!(value instanceof BlockEntity blockEntity)) {
            throw new IllegalArgumentException("Expected a TConstruct heating structure block entity");
        }
        Level level = blockEntity.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        Object structure = invoke(blockEntity, "getStructure");
        if (structure == null) {
            return;
        }

        try {
            ClassLoader loader = blockEntity.getClass().getClassLoader();
            Class<?> structureDataType = Class.forName(STRUCTURE_DATA, true, loader);
            Method getMinPos = structureDataType.getMethod("getMinPos");
            Method getMaxPos = structureDataType.getMethod("getMaxPos");
            Method getTanks = structureDataType.getMethod("getTanks");
            Object packet = Class.forName(STRUCTURE_PACKET, true, loader)
                    .getConstructor(BlockPos.class, BlockPos.class, BlockPos.class, List.class)
                    .newInstance(blockEntity.getBlockPos(), getMinPos.invoke(structure),
                            getMaxPos.invoke(structure), getTanks.invoke(structure));
            Class<?> networkType = Class.forName(TINKER_NETWORK, true, loader);
            Object network = networkType.getMethod("getInstance").invoke(null);
            networkType.getMethod("sendToClientsAround", Object.class,
                    net.minecraft.world.level.LevelAccessor.class, BlockPos.class)
                    .invoke(network, packet, level, blockEntity.getBlockPos());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to resend TConstruct smeltery structure data", exception);
        }
    }

    private static Object invoke(Object receiver, String methodName) {
        try {
            return receiver.getClass().getMethod(methodName).invoke(receiver);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("TConstruct " + methodName + " failed", cause);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not invoke TConstruct " + methodName, exception);
        }
    }
}
