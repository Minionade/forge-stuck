package net.neoforged.forgestuck.compat;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bridges direct Forge block-entity energy providers to NeoForge's block capability system. */
public final class LegacyBlockEnergyCapabilityCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgestuck");
    private static final Set<String> INSTALLED_MODS = ConcurrentHashMap.newKeySet();

    private LegacyBlockEnergyCapabilityCompat() {}

    public static void install(IEventBus modEventBus) {
        var activeContainer = ModLoadingContext.get().getActiveContainer();
        if (activeContainer == null) return;
        String modId = activeContainer.getModId();
        if (INSTALLED_MODS.add(modId)) {
            modEventBus.addListener(RegisterCapabilitiesEvent.class,
                    event -> registerLegacyEnergyBlocks(event, modId));
        }
    }

    private static void registerLegacyEnergyBlocks(RegisterCapabilitiesEvent event, String modId) {
        Set<Block> blocksToRegister = new LinkedHashSet<>();
        for (BlockEntityType<?> type : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
            for (Block block : type.getValidBlocks()) {
                var id = BuiltInRegistries.BLOCK.getKey(block);
                if (id != null && id.getNamespace().equals(modId)
                        && !event.isBlockRegistered(Capabilities.EnergyStorage.BLOCK, block)) {
                    blocksToRegister.add(block);
                }
            }
        }
        if (!blocksToRegister.isEmpty()) {
            event.registerBlock(Capabilities.EnergyStorage.BLOCK,
                    LegacyBlockEnergyCapabilityCompat::getLegacyEnergyStorage,
                    blocksToRegister.toArray(Block[]::new));
            LOGGER.info("Registered legacy Forge energy capability lookup for {} block(s) in mod {}",
                    blocksToRegister.size(), modId);
        }
    }

    private static IEnergyStorage getLegacyEnergyStorage(
            Level level, BlockPos pos, BlockState state, BlockEntity blockEntity, Direction side) {
        return getLegacyEnergyStorage(blockEntity, side);
    }

    private static IEnergyStorage getLegacyEnergyStorage(BlockEntity blockEntity, Direction side) {
        IEnergyStorage energyStorage = invokeEnergyStorageAccessor(blockEntity, "getEnergyManager");
        if (energyStorage != null) return energyStorage;
        try {
            Object result = blockEntity.getClass()
                    .getMethod("getCapability", LegacyCapability.class, Direction.class)
                    .invoke(blockEntity, LegacyForgeCapabilities.ENERGY, side);
            if (result instanceof LazyOptional<?> optional) {
                Object resolved = optional.resolve().orElse(null);
                return resolved instanceof IEnergyStorage storage ? storage : null;
            }
            return null;
        } catch (NoSuchMethodException e) {
            return null;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Unable to query legacy block energy capability", cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to query legacy block energy capability", e);
        }
    }

    private static IEnergyStorage invokeEnergyStorageAccessor(BlockEntity blockEntity, String methodName) {
        try {
            Object result = blockEntity.getClass().getMethod(methodName).invoke(blockEntity);
            return result instanceof IEnergyStorage storage ? storage : null;
        } catch (NoSuchMethodException e) {
            return null;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Unable to read legacy block energy storage", cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to read legacy block energy storage", e);
        }
    }
}
