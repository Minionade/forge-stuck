# Forge 1.20.1 Compatibility Inventory

Last scanned: 2026-10-18

This inventory tracks bytecode references in the supplied Minecraft 1.20.1 Forge mod jars. The detailed, deduplicated call and field reference list is in [forge-1.20.1-call-inventory.tsv](forge-1.20.1-call-inventory.tsv). Update its status as compatibility patches are implemented and verified.

## Scope and method

Scanned only the 13 jars whose filenames identify Minecraft 1.20.1, plus Jar-in-Jar classes embedded in those jars (including `thermal_core` inside Thermal Foundation). NeoForge 1.21.1 jars were excluded.

The scan records bytecode method invocations, field accesses, and method/field handles whose symbolic owner begins with `net.minecraftforge`. It compares each owner and member against the current `ForgeOwnerMappings` table. It does not yet cover Forge-added methods invoked with Minecraft owners, descriptor-only references, reflection/string-based lookups, mixin targets, or resource/config references; those require follow-up passes before this can be considered an exhaustive migration audit.

Status meanings:

- `PATCHED`: the current owner/member remap table has a supported mapping. This confirms a transformer mapping exists, not that the corresponding behavior has been exercised in-game.
- `KNOWN_GAP`: the owner is mapped, but this member is not in its supported-member set. Existing transformation may leave the owner unmapped or replace the call with a runtime failure.
- `UNMAPPED`: no owner mapping was found. Verify whether the API still exists unchanged on NeoForge; add a compatibility patch when it does not.

The TSV contains **1,173 unique signatures** across the scanned jars: **953 patched**, **74 known gaps**, and **146 unmapped**. Their 9,443 bytecode references are distributed as 8,623 patched, 166 known-gap, and 654 unmapped references (91.3% patched). These are static bytecode-reference counts, not a claim that every reference executes at runtime.

The implementation batch covers verified tag fields, fluid handlers and types (including type-level descriptions, sounds, and lighter-than-air checks), FluidStack compatibility including legacy NBT serialization, item-handler helpers including Forge's stack comparison and sized-copy utilities, inherited modifiable-handler methods, the empty-handler singleton and `SidedInvWrapper` constructor, composite crafting ingredient factories, direct client model/data-generation and baked-geometry mappings including `ChunkRenderTypeSet` and matching ForgeHooksClient utility methods, compatible fluid utility signatures, model builder/state APIs, model-data refresh, common hooks, root-transform/vertex-pipeline helpers, fluid bucket wrappers, adapters for the `EmptyFluidHandler` constructor, burn-time calls and method handles, multipart entities, biome modifier constructors, simple and composite crafting-condition constructors, exact NeoForge event hooks, generic event-listener compatibility (including priority overloads and cancellation-return event posting), critical-hit event result and damage-modifier adaptation, crop-growth pre/post hooks, `ForgeHooks.hasNoElements`, `FMLLoader.isProduction`, current-server lifecycle lookup, MobEffectEvent.Remove constructor adaptation, legacy block-state-definition super-call remapping, and DamageSource creative-player method-handle adaptation, Forge reach/step attributes mapped to their vanilla 1.21.1 counterparts, the milk fluid registry key, read-only registry iteration, and compatibility adapters for all three `NetworkHooks.openScreen` overloads. Target members are checked against the NeoForge 1.21.1 API; adapters are regression-tested. The legacy entity-spawning packet helper, `QuadBakingVertexConsumer` constructor, `ForgeHooksClient.getArmorTexture`, and `FluidUtil.getFluidHandler(ItemStack)` remain unresolved due changed context, return, or construction APIs. The legacy `QuadBakingVertexConsumer.Buffered` nested API remains unmapped because NeoForge replaced it with a different baking flow.

## Per-jar status

Counts are unique owner/member signatures found in each jar, not invocation frequency.

| Mod jar | Patched | Known gaps | Unmapped |
|---|---:|---:|---:|
| `Mantle-1.20.1-1.11.117.jar` | 241 | 19 | 42 |
| `TConstruct-1.20.1-3.12.1.231.jar` | 649 | 40 | 106 |
| `advgenerators-1.6.0.6-mc1.20.1.jar` | 75 | 1 | 3 |
| `bdlib-1.27.0.8-mc1.20.1.jar` | 149 | 3 | 5 |
| `cofh_core-1.20.1-11.0.2.56.jar` | 361 | 19 | 24 |
| `gendustry-1.20.1-1.0.5.jar` | 40 | 0 | 7 |
| `thermal_cultivation-1.20.1-11.0.1.24.jar` | 42 | 0 | 1 |
| `thermal_dynamics-1.20.1-11.0.1.23.jar` | 85 | 3 | 5 |
| `thermal_expansion-1.20.1-11.0.1.29.jar` | 37 | 0 | 3 |
| `thermal_foundation-1.20.1-11.0.6.70.jar` | 176 | 7 | 16 |
| `thermal_innovation-1.20.1-11.0.1.23.jar` | 63 | 1 | 10 |
| `thermal_integration-1.20.1-11.0.1.27.jar` | 19 | 0 | 0 |
| `thermal_locomotion-1.20.1-11.0.1.19.jar` | 35 | 0 | 3 |

## Updating the inventory

When adding or correcting compatibility, update the matching TSV rows from `UNMAPPED` or `KNOWN_GAP` to `PATCHED` only after the transformer mapping and relevant regression coverage are in place. Keep bytecode-reference totals and affected jar names intact. If the supplied mod jars change, rescan the 1.20.1 jars and refresh both this overview and the TSV; do not include NeoForge-native 1.21.1 jars.
