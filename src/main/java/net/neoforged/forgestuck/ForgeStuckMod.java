package net.neoforged.forgestuck;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.forgestuck.adapter.AdvancedGeneratorsMultiblockAdapter;
import net.neoforged.forgestuck.adapter.ForestryGeneticsAdapter;
import net.neoforged.forgestuck.adapter.ThermalSeriesAdapter;
import net.neoforged.forgestuck.shim.LegacyCapabilityBridge;
import net.neoforged.forgestuck.shim.LegacyItemStackShim;
import net.neoforged.forgestuck.shim.LegacyNetworkBridge;
import org.slf4j.Logger;

/**
 * Forge Stuck!!: NeoForge 1.21.1 Runtime Compatibility Bridge for Forge 1.20.1 mods.
 */
@Mod(ForgeStuckMod.MODID)
public class ForgeStuckMod {
    public static final String MODID = "forgestuck";
    public static final String VERSION = "1.21.1-1.1.0";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ForgeStuckMod(IEventBus modEventBus) {
        LOGGER.info("[Forge Stuck!!] Unsticking 1.20.1 mods for NeoForge 1.21.1 v{}", VERSION);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::onLoadComplete);

        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(LegacyItemStackShim.class);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("[Forge Stuck!!] Bootstrapping Legacy Forge API Shim Layer...");
        ThermalSeriesAdapter.init();
        ForestryGeneticsAdapter.init();
        AdvancedGeneratorsMultiblockAdapter.init();
        LOGGER.info("[Forge Stuck!!] Adapters unstuck: Thermal Series, Gendustry CE, Advanced Generators");
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        LOGGER.info("[Forge Stuck!!] Registering NeoForge 1.21.1 Capability Bridges...");
        LegacyCapabilityBridge.register(event);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        LOGGER.info("[Forge Stuck!!] Registering Legacy SimpleChannel Network Forwarders...");
        LegacyNetworkBridge.registerPayloadHandlers(event);
    }

    private void onLoadComplete(FMLLoadCompleteEvent event) {
        LOGGER.info("[Forge Stuck!!] Successfully un-stuck Forge 1.20.1 mods! All shims active.");
    }
}
