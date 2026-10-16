export interface LegacyApiHook {
  signature: string;
  sourceType: 'NBT' | 'Capability' | 'Registry' | 'Network' | 'Multiblock';
  severity: 'Breaking' | 'Critical' | 'Warning';
  legacy1201: string;
  neoForge1211: string;
  activeShim: string;
  status: 'Unstuck' | 'Patched' | 'Adapted';
}

export interface ModProfile {
  id: string;
  name: string;
  version: string;
  authors: string;
  modid: string;
  mcVersion: string;
  targetBadge: string;
  description: string;
  compatibilityScore: number;
  verdict: string;
  jarName: string;
  jarSize: string;
  classCount: number;
  hooks: LegacyApiHook[];
  activeAdapters: string[];
  sampleNbt: {
    title: string;
    legacySnbt: string;
    neo121Component: string;
    description: string;
  };
  notes: string[];
}

export const TARGET_MOD_PROFILES: ModProfile[] = [
  {
    id: 'thermal',
    name: 'Thermal Series (Expansion & CoFH Core)',
    version: '10.3.0.18',
    authors: 'Team CoFH (King Lemming, covers1624)',
    modid: 'thermal_expansion',
    mcVersion: '1.20.1',
    targetBadge: 'Primary Target',
    description: 'Premier RF tech mod suite featuring Redstone Furnace, Pulverizer, Induction Smelter, Flux Capacitors, and modular machine augmentations.',
    compatibilityScore: 98,
    verdict: '100% Compatible via Forge Stuck!! Runtime Shims',
    jarName: 'thermal_expansion-1.20.1-10.3.0.18.jar',
    jarSize: '4.8 MB',
    classCount: 412,
    activeAdapters: [
      'ThermalSeriesAdapter (Augment slots & RF item containers)',
      'LegacyItemStackShim (Item NBT -> DataComponents.CUSTOM_DATA)',
      'LegacyCapabilityBridge (ForgeCapabilities.ENERGY -> BlockCapability)',
      'LegacyRegistryBridge (ForgeRegistries -> DeferredHolder)'
    ],
    hooks: [
      {
        signature: 'ItemStack.getTag() / getOrCreateTag()',
        sourceType: 'NBT',
        severity: 'Breaking',
        legacy1201: 'stack.getTag().getInt("Energy")',
        neoForge1211: 'stack.get(DataComponents.CUSTOM_DATA).copyTag()',
        activeShim: 'LegacyItemStackShim.getTag()',
        status: 'Unstuck'
      },
      {
        signature: 'ForgeCapabilities.ENERGY',
        sourceType: 'Capability',
        severity: 'Breaking',
        legacy1201: 'be.getCapability(ForgeCapabilities.ENERGY, side)',
        neoForge1211: 'level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side)',
        activeShim: 'LegacyCapabilityBridge.register()',
        status: 'Adapted'
      },
      {
        signature: 'ForgeCapabilities.FLUID_HANDLER',
        sourceType: 'Capability',
        severity: 'Breaking',
        legacy1201: 'be.getCapability(ForgeCapabilities.FLUID_HANDLER, side)',
        neoForge1211: 'level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side)',
        activeShim: 'LegacyCapabilityBridge.register()',
        status: 'Adapted'
      },
      {
        signature: 'DeferredRegister.create(ForgeRegistries.ITEMS)',
        sourceType: 'Registry',
        severity: 'Critical',
        legacy1201: 'DeferredRegister.create(ForgeRegistries.ITEMS, "thermal")',
        neoForge1211: 'DeferredRegister.createItems("thermal")',
        activeShim: 'LegacyRegistryBridge.create()',
        status: 'Unstuck'
      },
      {
        signature: 'PacketHandler.sendTo() / SimpleChannel',
        sourceType: 'Network',
        severity: 'Breaking',
        legacy1201: 'channel.send(PacketDistributor.PLAYER.with(player), msg)',
        neoForge1211: 'PacketDistributor.sendToPlayer(player, payload)',
        activeShim: 'LegacyNetworkBridge.registerPayloadHandlers()',
        status: 'Patched'
      }
    ],
    sampleNbt: {
      title: 'Flux Capacitor Stored Energy & Augments',
      legacySnbt: `{
  Energy: 4000000,
  Augments: [
    { id: "thermal:dynamo_output_augment", Count: 1b },
    { id: "thermal:rf_coil_storage_augment", Count: 2b }
  ],
  Mode: 1b
}`,
      neo121Component: `minecraft:custom_data -> {
  "Energy": 4000000,
  "Augments": [
    { "id": "thermal:dynamo_output_augment", "Count": 1b },
    { "id": "thermal:rf_coil_storage_augment", "Count": 2b }
  ],
  "Mode": 1b
}`,
      description: 'Thermal stores internal RF and augment items inside item NBT. The LegacyItemStackShim intercepts getTag() and transparently reads/writes DataComponents.CUSTOM_DATA with zero loss.'
    },
    notes: [
      'Fluxbore, Flux Capacitor, and Reservoirs work out-of-the-box using the runtime NBT redirect.',
      'Energy generation and transfer seamlessly bridges to 1.21.1 NeoForge cables and batteries.',
      'Augment slots remain intact when saving and loading chunks.'
    ]
  },
  {
    id: 'gendustry',
    name: 'Gendustry CE (Forestry CE Addon)',
    version: '2.0.4',
    authors: 'bdew, ForestryMC Community',
    modid: 'gendustry',
    mcVersion: '1.20.1',
    targetBadge: 'Primary Target',
    description: 'Industrial genetics automation addon for Forestry: Mutatrons, Genetic Samplers, Genetic Imprinters, and Industrial Apiaries.',
    compatibilityScore: 96,
    verdict: '100% Compatible via Forge Stuck!! Genetics Adapter',
    jarName: 'gendustry-1.20.1-2.0.4.jar',
    jarSize: '2.1 MB',
    classCount: 198,
    activeAdapters: [
      'ForestryGeneticsAdapter (Deep bee/tree chromosome NBT preservation)',
      'LegacyItemStackShim (Item NBT -> DataComponents.CUSTOM_DATA)',
      'LegacyCapabilityBridge (FE Power & Mutagen Fluid tanks)',
      'LegacyRegistryBridge (GUI Container & Machine Block registration)'
    ],
    hooks: [
      {
        signature: 'ItemStack.getTag().getCompound("Genome")',
        sourceType: 'NBT',
        severity: 'Breaking',
        legacy1201: 'beeStack.getTag().getCompound("Genome")',
        neoForge1211: 'beeStack.get(DataComponents.CUSTOM_DATA).copyTag().getCompound("Genome")',
        activeShim: 'ForestryGeneticsAdapter.getGenomeTag()',
        status: 'Adapted'
      },
      {
        signature: 'ForgeCapabilities.FLUID_HANDLER (Mutagen/Liquid DNA)',
        sourceType: 'Capability',
        severity: 'Breaking',
        legacy1201: 'mutatronTile.getCapability(ForgeCapabilities.FLUID_HANDLER)',
        neoForge1211: 'level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side)',
        activeShim: 'LegacyCapabilityBridge.register()',
        status: 'Adapted'
      },
      {
        signature: 'ForgeCapabilities.ENERGY (Industrial Apiary Power)',
        sourceType: 'Capability',
        severity: 'Breaking',
        legacy1201: 'apiaryTile.getCapability(ForgeCapabilities.ENERGY)',
        neoForge1211: 'level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side)',
        activeShim: 'LegacyCapabilityBridge.register()',
        status: 'Adapted'
      },
      {
        signature: 'NetworkRegistry.newSimpleChannel("gendustry")',
        sourceType: 'Network',
        severity: 'Breaking',
        legacy1201: 'NetworkRegistry.newSimpleChannel(res, () -> "1.0", ...)',
        neoForge1211: 'RegisterPayloadHandlersEvent.registrar("forgestuck")',
        activeShim: 'LegacyNetworkBridge.registerMessage()',
        status: 'Patched'
      }
    ],
    sampleNbt: {
      title: 'Queen Bee Genetic Chromosome Structure',
      legacySnbt: `{
  Genome: {
    Chromosomes: [
      { UID0: "forestry.speciesForest", UID1: "forestry.speciesMeadows" },
      { UID0: "forestry.speedNormal",   UID1: "forestry.speedFast" },
      { UID0: "forestry.lifespanNormal", UID1: "forestry.lifespanLong" },
      { UID0: "forestry.fertilityNormal", UID1: "forestry.fertilityMaximum" }
    ]
  },
  Health: 38,
  MaxHealth: 40,
  IsAnalyzed: 1b
}`,
      neo121Component: `minecraft:custom_data -> {
  "Genome": {
    "Chromosomes": [
      { "UID0": "forestry.speciesForest", "UID1": "forestry.speciesMeadows" },
      { "UID0": "forestry.speedNormal",   "UID1": "forestry.speedFast" },
      { "UID0": "forestry.lifespanNormal", "UID1": "forestry.lifespanLong" },
      { "UID0": "forestry.fertilityNormal", "UID1": "forestry.fertilityMaximum" }
    ]
  },
  "Health": 38,
  "MaxHealth": 40,
  "IsAnalyzed": 1b
}`,
      description: 'Forestry bee breeding heavily mutates the nested Genome compound. ForestryGeneticsAdapter ensures cross-generation breeding traits are perfectly conserved without corruption.'
    },
    notes: [
      'Mutatron mutation rolls and Genetic Samplers function smoothly.',
      'Industrial Apiaries correctly consume RF energy and accept automation pipes.',
      'DNA templates and gene sample items retain full allele identifiers.'
    ]
  },
  {
    id: 'advgenerators',
    name: 'Advanced Generators',
    version: '1.4.2',
    authors: 'bdew',
    modid: 'advgenerators',
    mcVersion: '1.20.1',
    targetBadge: 'Primary Target',
    description: 'Modular multiblock power generation: Steam Turbines, Gas Turbines, Syngas Producers, and Heat Exchangers with customizable materials and outputs.',
    compatibilityScore: 97,
    verdict: '100% Compatible via Forge Stuck!! Multiblock Engine',
    jarName: 'advgenerators-1.20.1-1.4.2.jar',
    jarSize: '1.7 MB',
    classCount: 164,
    activeAdapters: [
      'AdvancedGeneratorsMultiblockAdapter (Turbine structure validation & power scaling)',
      'LegacyItemStackShim (Item NBT -> DataComponents.CUSTOM_DATA)',
      'LegacyCapabilityBridge (High-throughput FE emission & liquid fuel intake)',
      'LegacyRegistryBridge (Multiblock component blocks & BdLib inventory)'
    ],
    hooks: [
      {
        signature: 'Multiblock.validateStructure(World, BlockPos)',
        sourceType: 'Multiblock',
        severity: 'Critical',
        legacy1201: 'world.getBlockState(pos).is(block)',
        neoForge1211: 'level.getBlockState(pos).is(block)',
        activeShim: 'AdvancedGeneratorsMultiblockAdapter.validateStructure()',
        status: 'Adapted'
      },
      {
        signature: 'ForgeCapabilities.ENERGY (Output Bus)',
        sourceType: 'Capability',
        severity: 'Breaking',
        legacy1201: 'powerTile.getCapability(ForgeCapabilities.ENERGY, null)',
        neoForge1211: 'level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side)',
        activeShim: 'LegacyCapabilityBridge.register()',
        status: 'Adapted'
      },
      {
        signature: 'ForgeCapabilities.FLUID_HANDLER (Fluid Intake Bus)',
        sourceType: 'Capability',
        severity: 'Breaking',
        legacy1201: 'intakeTile.getCapability(ForgeCapabilities.FLUID_HANDLER, side)',
        neoForge1211: 'level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side)',
        activeShim: 'LegacyCapabilityBridge.register()',
        status: 'Adapted'
      },
      {
        signature: 'ItemStack.getTag() on Turbine Upgrade Kits',
        sourceType: 'NBT',
        severity: 'Breaking',
        legacy1201: 'stack.getTag().getString("TurbineTier")',
        neoForge1211: 'stack.get(DataComponents.CUSTOM_DATA).copyTag().getString("TurbineTier")',
        activeShim: 'LegacyItemStackShim.getTag()',
        status: 'Unstuck'
      }
    ],
    sampleNbt: {
      title: 'Gas Turbine Controller Configuration NBT',
      legacySnbt: `{
  BladesCount: 32,
  BladeTier: "manyullyn",
  Efficiency: 1.45f,
  InternalEnergy: 1850000L,
  FuelType: "thermal:refined_fuel",
  OutputRateFE: 8250
}`,
      neo121Component: `minecraft:custom_data -> {
  "BladesCount": 32,
  "BladeTier": "manyullyn",
  "Efficiency": 1.45f,
  "InternalEnergy": 1850000L,
  "FuelType": "thermal:refined_fuel",
  "OutputRateFE": 8250
}`,
      description: 'Multiblock controllers preserve connected blade counts and fuel combustion efficiencies across server restarts via DataComponents.CUSTOM_DATA.'
    },
    notes: [
      'Multiblock structure assembly detects connected chambers cleanly.',
      'Turbine energy output scales smoothly up to high-tier FE/t production.',
      'Fluid intake ports accept fuels from Thermal, Create, and other 1.21.1 mods.'
    ]
  }
];
