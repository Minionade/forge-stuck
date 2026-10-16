package net.neoforged.forgestuck;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.forgestuck.shim.LegacyCapabilityBridge;
import net.neoforged.forgestuck.shim.LegacyItemStackShim;
import net.neoforged.forgestuck.shim.LegacyNetworkBridge;
import net.neoforged.forgestuck.transformer.ForgeClassTransformer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

/**
 * Forge Stuck!!: NeoForge 1.21.1 compatibility bridge for legacy Forge mod patterns.
 */
@Mod(ForgeStuckMod.MODID)
public class ForgeStuckMod {
    public static final String MODID = "forgestuck";
    public static final String VERSION = "1.21.1-1.1.0";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ForgeStuckMod(IEventBus modEventBus) {
        LOGGER.info("[Forge Stuck!!] Unsticking legacy Forge mods for NeoForge 1.21.1 v{}", VERSION);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::onLoadComplete);

        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(LegacyItemStackShim.class);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("[Forge Stuck!!] Bootstrapping compatibility layer...");
        ForgeClassTransformer.init();
        LOGGER.info("[Forge Stuck!!] Compatibility layer initialized");
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        LOGGER.info("[Forge Stuck!!] Registering capability bridges...");
        LegacyCapabilityBridge.register(event);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        LOGGER.info("[Forge Stuck!!] Registering payload bridge...");
        LegacyNetworkBridge.registerPayloadHandlers(event);
    }

    private void onLoadComplete(FMLLoadCompleteEvent event) {
        LOGGER.info("[Forge Stuck!!] Compat bridge online. Legacy Forge mod patterns are being monitored.");
    }
}
