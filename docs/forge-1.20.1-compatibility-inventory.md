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

The TSV contains **1,173 unique signatures** across the scanned jars: **924 patched**, **82 known gaps**, and **167 unmapped**. These are static bytecode-reference counts, not a claim that every reference executes at runtime.

The implementation batch covers verified tag fields, fluid handlers and types (including type-level descriptions, sounds, and lighter-than-air checks), FluidStack compatibility, item-handler helpers including Forge's stack comparison and sized-copy utilities and inherited modifiable-handler methods, composite crafting ingredient factories, direct client model/data-generation and baked-geometry mappings, compatible fluid utility signatures, model builder/state APIs, model-data refresh, common hooks, root-transform/vertex-pipeline helpers, fluid bucket wrappers, adapters for the `EmptyFluidHandler` constructor, burn-time calls and method handles, multipart entities, biome modifier constructors, simple and composite crafting-condition constructors, exact NeoForge event hooks, generic event-listener compatibility (including priority overloads and cancellation-return event posting), Forge reach/step attributes mapped to their vanilla 1.21.1 counterparts, the milk fluid registry key, and compatibility adapters for all three `NetworkHooks.openScreen` overloads. Target members are checked against the NeoForge 1.21.1 API; adapters are regression-tested. The legacy entity-spawning packet helper, `QuadBakingVertexConsumer` constructor, and `FluidUtil.getFluidHandler(ItemStack)` remain unresolved due changed context, return, or construction APIs. The legacy `QuadBakingVertexConsumer.Buffered` nested API remains unmapped because NeoForge replaced it with a different baking flow.

## Per-jar status

Counts are unique owner/member signatures found in each jar, not invocation frequency.

| Mod jar | Patched | Known gaps | Unmapped |
|---|---:|---:|---:|
| `Mantle-1.20.1-1.11.117.jar` | 236 | 20 | 46 |
| `TConstruct-1.20.1-3.12.1.231.jar` | 630 | 47 | 118 |
| `advgenerators-1.6.0.6-mc1.20.1.jar` | 75 | 1 | 3 |
| `bdlib-1.27.0.8-mc1.20.1.jar` | 143 | 5 | 9 |
| `cofh_core-1.20.1-11.0.2.56.jar` | 349 | 22 | 33 |
| `gendustry-1.20.1-1.0.5.jar` | 40 | 0 | 7 |
| `thermal_cultivation-1.20.1-11.0.1.24.jar` | 41 | 0 | 2 |
| `thermal_dynamics-1.20.1-11.0.1.23.jar` | 82 | 5 | 6 |
| `thermal_expansion-1.20.1-11.0.1.29.jar` | 35 | 1 | 4 |
| `thermal_foundation-1.20.1-11.0.6.70.jar` | 172 | 10 | 17 |
| `thermal_innovation-1.20.1-11.0.1.23.jar` | 61 | 1 | 12 |
| `thermal_integration-1.20.1-11.0.1.27.jar` | 18 | 0 | 1 |
| `thermal_locomotion-1.20.1-11.0.1.19.jar` | 33 | 1 | 4 |

## Updating the inventory

When adding or correcting compatibility, update the matching TSV rows from `UNMAPPED` or `KNOWN_GAP` to `PATCHED` only after the transformer mapping and relevant regression coverage are in place. Keep bytecode-reference totals and affected jar names intact. If the supplied mod jars change, rescan the 1.20.1 jars and refresh both this overview and the TSV; do not include NeoForge-native 1.21.1 jars.
