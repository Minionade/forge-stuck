# ForgeBridge: NeoForge 1.21.1 Runtime Mod Patcher & Web Compatibility Analyzer

A self-contained NeoForge 1.21.1 Minecraft mod that acts as an in-game runtime patcher and compatibility layer for Forge 1.20.1 mods (specifically targeting Thermal Series, Gendustry CE, and Advanced Generators), paired with a streamlined web-based compatibility analyzer.

### User Review & Critical Decisions

> [!IMPORTANT]
> The architectural scope has been revised per user direction:
> - **Self-Contained Mod Patcher**: The patcher is the NeoForge mod JAR itself. Users simply place `forgebridge-1.21.1.jar` into their Minecraft NeoForge 1.21.1 `mods` folder alongside their Forge 1.20.1 mod JARs. At game launch, ForgeBridge's built-in bytecode transformer and dynamic runtime shims automatically patch 1.20.1 mods in memory.
> - **Lightweight Web Analysis Layer**: The web application is focused on rapid compatibility analysis—allowing users to test JARs or select presets (Thermal Series, Gendustry CE, Advanced Generators) to instantly verify whether a mod is supported by ForgeBridge, view the exact shims that will trigger, and download the ready-to-use mod JAR and full Java source.

- **Confirmed Decision 1**: All patching logic (bytecode remapping, class transformation, NBT-to-DataComponent proxying, capability bridging) resides inside the Java NeoForge mod JAR (`ForgeBridge`).
- **Confirmed Decision 2**: The web layer is a fast, clean diagnostic analyzer that inspects mod JARs against ForgeBridge's supported shims, displaying compatibility status, detected legacy APIs, and providing the mod download.

---

### 1. Overview & Core Concept

- **What It Does**: ForgeBridge allows players and modpack creators to run Forge 1.20.1 mods on NeoForge 1.21.1 by placing a single bridge mod into their `mods` directory:
  1. **In-Mod Runtime Patcher & Shims (`forgebridge-1.21.1.jar`)**:
     - Automatically intercepts Forge 1.20.1 mod classes during NeoForge classloading.
     - Dynamically rewrites calls to legacy `net.minecraftforge.*` packages and directs `ItemStack.getTag()` calls to ForgeBridge's `DataComponents.CUSTOM_DATA` emulator.
     - Provides runtime emulation for `ForgeCapabilities` (Energy FE, FluidHandler, ItemHandler), `SimpleChannel` networking, and `DeferredRegister`.
     - Includes specialized adapters for Thermal Series (augments, RF energy items), Gendustry CE (Forestry genome NBT), and Advanced Generators (multiblock turbine formation).
  2. **Web Compatibility Analyzer**:
     - Fast web utility to inspect any 1.20.1 JAR file or inspect pre-loaded profiles (Thermal Expansion, Gendustry CE, Advanced Generators).
     - Reports compatibility score, required shims, detected legacy calls, and lets users download the compiled mod JAR or source code.
- **Target Audience**: Minecraft players and modpack authors upgrading to NeoForge 1.21.1 who want classic 1.20.1 industrial and genetic tech mods.

---

### 2. User Experience & Visual Design

#### Key User Flows
1. **Quick Compatibility Scan**:
   - The user opens the analyzer. Pre-analyzed profiles for **Thermal Series**, **Gendustry CE**, and **Advanced Generators** are featured prominently, along with a drag-and-drop zone for any custom 1.20.1 mod JAR.
   - Analysis executes in milliseconds, displaying a clean verdict: e.g. `Compatible · 4 Shims Active · Thermal Adapter Engaged`.
2. **Detailed API Breakdown**:
   - Scanned results show exact API hooks detected:
     * `ItemStack.getTag() / setTag()` $\rightarrow$ Handled by `LegacyItemStackShim` (Data Components)
     * `ForgeCapabilities.ENERGY / FLUID_HANDLER` $\rightarrow$ Handled by `LegacyCapabilityBridge`
     * `SimpleChannel.send()` $\rightarrow$ Handled by `LegacyNetworkBridge` (PayloadRegistrar)
     * `DeferredRegister` $\rightarrow$ Handled by `LegacyRegistryBridge`
3. **One-Click Mod Download & Source Inspector**:
   - Primary action in the top bar: **Download ForgeBridge Mod (.jar)** ready to place in `run/mods` or Minecraft launcher.
   - Built-in Code Viewer allows exploring the complete Java source files, Gradle build scripts, and `neoforge.mods.toml`.

#### Visual Identity & Theme
- **Aesthetic Direction**: High-efficiency developer workbench with dark slate styling, crisp hairline borders, and responsive single-elevation surfaces.
- **Color Palette**:
  - Dominant Canvas: Deep slate `#0B0F17` background with `#131B2A` card surfaces and `#1E293B` borders.
  - Accent Color: Energetic industrial amber (`#F59E0B`) paired with cyan (`#06B6D4`) for NeoForge and emerald (`#10B981`) for Forestry genetics.
- **Typography**: `Plus Jakarta Sans` for clean titles, `DM Sans` for body text, and `JetBrains Mono` with `tabular-nums` for bytecode signatures, NBT structures, and package names.
- **Restraint & Anti-Slop**: Zero pill badges, zero decorative pulsing dots, no artificial telemetry tickers, and clean unboxed metadata separated by `·`.

---

### 3. Key Product Decisions & Trade-Offs

#### Decision 1: In-Game Runtime Bytecode Patcher vs Offline Patcher
- **Chosen Approach**: Build the patcher directly into the NeoForge 1.21.1 mod JAR via an ASM class transformer / Mixin plugin, so end-users only need to drop `forgebridge.jar` into their mods folder alongside their 1.20.1 mods.
- **Why**: Eliminates manual multi-step file patching and allows mods to be updated or added without re-running an external patching program.
- **Trade-off**: Requires careful bytecode transformation during classloading, which ForgeBridge handles with targeted method remapping and fallback delegates.

#### Decision 2: Focused Web Analyzer Role
- **Chosen Approach**: Streamline the web app into an instant compatibility scanner, diagnostic viewer, and mod deliverer.
- **Why**: Aligns directly with user instructions: "The patcher should just be the mod jar itself. the web layer can just be a quick analysis to see if a mod is compatible with our patcher."

---

### 4. Technical Architecture & Mod Structure

```
┌─────────────────────────────────────────────────────────────────────────┐
│                      ForgeBridge System Overview                        │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│   [Minecraft NeoForge 1.21.1 Game Client / Server]                      │
│   mods/                                                                 │
│   ├── forgebridge-1.21.1-1.0.0.jar (The Patcher & Compatibility Engine) │
│   │   ├── ASM ClassTransformer (redirects 1.20.1 bytecode at load)      │
│   │   ├── LegacyItemStackShim (maps NBT to DataComponents.CUSTOM_DATA)   │
│   │   ├── LegacyCapabilityBridge (maps ForgeCapabilities to BlockCaps)   │
│   │   ├── LegacyNetworkBridge (maps SimpleChannel to CustomPayload)     │
│   │   ├── ThermalSeriesAdapter (augments & CoFH energy item shims)      │
│   │   ├── ForestryGeneticsAdapter (preserves genome NBT serialization)  │
│   │   └── AdvancedGeneratorsMultiblockAdapter (multiblock validation)   │
│   ├── thermal_expansion-1.20.1.jar ────────────────────┐                │
│   ├── gendustry-1.20.1.jar ────────────────────────────┼── Auto-patched │
│   └── advgenerators-1.20.1.jar ────────────────────────┘   at runtime   │
│                                                                         │
├─────────────────────────────────────────────────────────────────────────┤
│   [Web Compatibility Analyzer] (React SPA)                              │
│   ├── Fast JAR Bytecode Scanner (inspects classes & imports)            │
│   ├── Target Mod Presets (Thermal, Gendustry CE, Advanced Generators)   │
│   ├── Compatibility Verdict & Active Shim Inspector                     │
│   ├── Mod Source Code Browser (browse full Java NeoForge 1.21.1 files)  │
│   └── Instant Download (forgebridge-1.21.1.jar & Full Project ZIP)      │
└─────────────────────────────────────────────────────────────────────────┘
```

#### Mod JAR Java Implementation Details
1. **`ForgeBridgeMod.java`**: NeoForge `@Mod("forgebridge")` entrypoint, registers mod event buses, registers payload handlers, and initializes capability bridges.
2. **`ForgeTransformerService.java`**: ASM ClassLoader transformer service that intercepts class definitions from 1.20.1 mods and redirects `invokevirtual ItemStack.getTag()` to `LegacyItemStackShim.getTag(stack)`.
3. **`LegacyItemStackShim.java`**: Implements 1.20.1 `CompoundTag` getters/setters using 1.21.1's `stack.get(DataComponents.CUSTOM_DATA)` and `stack.set(DataComponents.CUSTOM_DATA, ...)`.
4. **`LegacyCapabilityBridge.java`**: Emulates `ForgeCapabilities.ENERGY`, `FLUID_HANDLER`, and `ITEM_HANDLER`, connecting them to NeoForge's `RegisterCapabilitiesEvent`.
5. **`ThermalSeriesAdapter.java`**: Provides CoFH Core energy item container shims, augment slot handlers, and fluid tank sync.
6. **`ForestryGeneticsAdapter.java`**: Handles Forestry allele registry, bee/tree/butterfly chromosome NBT preservation within custom data components.
7. **`AdvancedGeneratorsMultiblockAdapter.java`**: Simulates multiblock structure checks, steam/gas turbine fuel consumption, and FE output blocks.
8. **`build.gradle` & `neoforge.mods.toml`**: Fully configured NeoForge 1.21.1 Gradle build file with dependencies, MDK plugins, and metadata.
