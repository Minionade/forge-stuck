# Agent maintenance playbook

Contributor checklist for debugging legacy Forge → NeoForge compatibility.
This is a source-backed guide, not a promise that arbitrary Forge mods work.
Check the current implementation before applying an older workaround.

## Maintenance contract

- Every agent-authored PR touching transform/compat behavior **must update this
  playbook if behavior, diagnostics, or workflow changed**.
- Keep guidance aligned with code and tests; distinguish implemented behavior
  from proposed safeguards. Do not turn a pack-specific symptom into a global fix.
- Add a changelog note below: **date + PR link + what changed in workflow
  guidance**. Link this guide in the PR and explain the update, or explicitly
  state why no guidance changed.
- Keep reproduction details in the PR; do not copy private logs, credentials,
  or local machine details here.

## Architecture map: where to look

Java implementation lives under
[`src/main/java/net/neoforged/forgestuck`](../src/main/java/net/neoforged/forgestuck).
The target versions are declared in [`gradle.properties`](../gradle.properties)
(currently Minecraft 1.21.1 / NeoForge 21.1.262).

| Stage | Source and responsibility |
| --- | --- |
| Discovery | [`ForgeStuckCandidateLocator`](../src/main/java/net/neoforged/forgestuck/locator/ForgeStuckCandidateLocator.java) scans `<gameDir>/mods` jar files, excluding `forgestuck*`. Its explicit provider/platform filename exclusions register the **original** jar, not a transformed copy. |
| Legacy detection | [`LegacyJarDetector`](../src/main/java/net/neoforged/forgestuck/locator/LegacyJarDetector.java) requires `META-INF/mods.toml` and **no** `META-INF/neoforge.mods.toml`. Native NeoForge jars, including jars with both descriptors, are not legacy candidates and are left for normal discovery. This is metadata detection, not a filename/version heuristic. |
| Jar orchestration | [`JarTransformer`](../src/main/java/net/neoforged/forgestuck/transformer/jar/JarTransformer.java) reads the source without modifying it, strips jar signatures/manifest digests, transforms classes/resources, and generates `neoforge.mods.toml`. The locator submits the cached output to NeoForge discovery. |
| Ordered class steps | `withDefaultSteps()` runs `ForgeClassTransformStep` → `ForgeOwnerRemapStep` → `ResourceLocationClassTransformStep` → `SrgMemberRemapStep`. Respect this ordering when reproducing failures. |
| Compat abstractions | [`ForgeOwnerMappings`](../src/main/java/net/neoforged/forgestuck/transformer/jar/ForgeOwnerMappings.java) maps exact owners and, where specified, verified member signatures. [`compat/`](../src/main/java/net/neoforged/forgestuck/compat) supplies `LazyOptional` and `NonNull*` stand-ins; these are not a complete Forge API implementation. |
| Mixin/resource pass | [`MixinResourceRewriter`](../src/main/java/net/neoforged/forgestuck/transformer/jar/MixinResourceRewriter.java) inspects transformed classes, removes incompatible entries from mixin configs/refmaps, and remaps member tokens. Model loader conversion applies to `assets/*/models/**/*.json`, not arbitrary JSON. |

Class-step details:

- `ForgeClassTransformStep` delegates to `ForgeClassTransformer` for exact
  ItemStack NBT calls. It emits calls to
  [`shim/LegacyItemStackShim`](../src/main/java/net/neoforged/forgestuck/shim/LegacyItemStackShim.java)
  (`getTag`/`getOrCreateTag`/`setTag`, backed by `DataComponents.CUSTOM_DATA`).
  `ItemStackShimTest` loads transformed bytecode in a custom classloader and
  asserts every emitted shim owner/method exists.
- `ForgeOwnerRemapStep` handles owners, descriptors, signatures, and selected
  mixin annotation strings. An unsupported member blocks that owner **for the
  whole jar** (a `scan` pre-pass in `JarTransformer`), so declarations,
  descriptors and accesses cannot diverge between classes of one jar. Jars are
  transformed independently: the same owner may be mapped in one jar and blocked
  in another.
- `ResourceLocationClassTransformStep` replaces supported constructor patterns
  with factory calls. `SrgMemberRemapStep` uses
  [`srg-members.properties`](../src/main/resources/forgestuck/srg-members.properties)
  for game member names; lookup is owner/descriptor aware (precedence
  `owner#name(desc)` > `name(desc)` > `owner#name` > bare name; fields use
  `name@desc`). [`LegacyNameMappings`](../src/main/java/net/neoforged/forgestuck/transformer/jar/LegacyNameMappings.java)
  also reads the explicit
  [`missing-mixin-targets.txt`](../src/main/resources/forgestuck/missing-mixin-targets.txt).
- Metadata conversion keeps every declared `modId` and dependency id exactly as
  written (no id renames), normalizes dependency fields, and applies the
  `kotori_scala` → `javafml` string rewrite. Metadata acceptance does not
  establish API or language-provider compatibility; it is **not** evidence Scala
  mods are constructed. After conversion, `JarTransformer.validateEntrypoints`
  scans the source jar for `@Mod("…")` values and logs an ERROR for any value
  with no matching metadata `modId` (FML would report `dangling_entrypoint`).
  Dependencies on ids no loaded mod provides (e.g. Thermal modules' `thermal`,
  which is a nested jar-in-jar `thermal_core` that is **not** transformed) are
  left as declared; see "Remaining blockers".
- Unknown custom model loaders are now **preserved** with a warning
  (`REMOVE_UNKNOWN_MODEL_LOADERS=false`); stripping is opt-in only.
- Locator logs say "discovered", not "registered": discovery is not construction.

### Registration support (verified against NeoForge 21.1.262)

Mapped Forge owners and their verified targets live in `ForgeOwnerMappings`;
`ForgeOwnerMappingTargetsTest` reflects every whitelisted member against the real
target. Adapters in `compat/`: `RegistryObject` (extends `DeferredHolder`),
`LegacyDeferredRegister`, `IForgeRegistry`/`ForgeRegistries` (17 registries; no
ENCHANTMENTS/BIOMES), `FMLJavaModLoadingContext`, `LazyOptional` (+`map`,
`filter`, `orElseGet`, `cast`, `removeListener`), `NonNull*`. `@Mod`,
`@Mod.EventBusSubscriber` (bus `FORGE`→`GAME`), `IEventBus`, `SubscribeEvent`,
`RegisterEvent`, three lifecycle setup events and `IConfigSpec` map to NeoForge
types. `RegistrationLifecycleTest` transforms a synthetic Forge-style mod in an
unrelated namespace and shows the final id `<modid>:<name>` appears in a registry
after `RegisterEvent`. Root-cause hypothesis (supported by this fixture, not by a
real jar): `@Mod`/`DeferredRegister`/`ForgeRegistries` were previously unmapped, so
FML never constructed entry points. The prior mapping of the Forge capability
classes was wrong (`CapabilityToken` does not exist) and was removed.

### Config, environment and dist adapters (t12 log follow-up)

Mapped in `ConfigEnvironmentMappings`; `ModConfigSpec.Builder` has the same names and
erased descriptors as `ForgeConfigSpec.Builder` (javap-verified on 21.1.262).
`DistExecutor` does not exist in NeoForge, so `compat/DistExecutor` reimplements the
run/call-when-on and run-for-dist methods over `FMLEnvironment.dist` (the dev-only
safe-referent validation is not reproduced). `ModLoadingContext.registerConfig`
forwards to the active `ModContainer`. Runtime classloader tests:
`ConfigEnvironmentCompatTest`.

Real referrers in the t12 log: Forestry (native, untouched) failed on
`MinecraftForge` only because its module manager loaded the legacy-transformed
**gendustry** (`RecipeCacheRegistry.<init>`). `bdlib` and `advgenerators` fail in FML's
Java language provider ("must have exactly 1 public constructor, found 0"): Scala
`object` mods need a Scala language provider; this is a **provider blocker, not a link
error**, and is not fixed here.

Unmapped owners ranked by references in the 13 transformed t12 jars (refs/classes):
`fluids/FluidStack` 5025/459, `IFluidHandler$FluidAction` 949/147, `IFluidHandler`
786/111, `crafting/conditions/ICondition` 773/84, `FluidType$Properties` 767/44,
`capabilities/Capability` 677/174, `Tags$Items` 608/30, `ItemModelBuilder` 432/10,
`IItemHandler` 368/69, `IGeometryBakingContext` 362/41, `ExistingFileHelper` 342/111,
`ModelData` 320/58, `ToolAction` 276/48. Documented blockers, deliberately not faked:
conditions (NeoForge `ICondition` is codec-based with a different `IContext`; Mantle),
capabilities and fluid/item handlers (redesigned), component-based `FluidStack`,
`NetworkDirection`/networking, datagen model builders, `IEventBus.post(Event)Z` and
`addGenericListener`, and `Tags$Items` (NeoForge uses `c:` tags whereas Forge mods ship
`forge:` data, so a mapping would silently mismatch).

### Data-pack migration (`ResourceDataRewriteStep`, `DatapackSchemaRewriter`)

Applied to `data/**` during the jar walk; idempotent, UTF-8, unaffected resources
keep original bytes, malformed/unsupported data is reported
(`ResourceRewriteReport`) and never deleted.

- Legacy plural folders → 1.21.1 singular (`recipes`→`recipe`, `loot_tables`,
  `advancements`, `structures`, `functions`, `predicates`, `item_modifiers`,
  `tags/{blocks,items,entity_types,fluids,game_events}`). Existing target
  entries are never overwritten (reported instead). Worldgen is not renamed.
- Under `data/*/worldgen/**` only: `{"type":"minecraft:uniform","value":{…}}`
  (also `biased_to_bottom`, `clamped`, `clamped_normal`, `trapezoid`) is flattened
  when the key set exactly matches a verified 1.21.1 codec shape.
- `forge:conditions` → `neoforge:conditions` and the 8 `forge:` condition types
  that exist on NeoForge.
- Not migrated (explicit boundaries): recipe `result.item`→`id`, `forge:` tags →
  `c:` (ambiguous), custom types and namespaces.

### Cache behavior

- Outputs live at `<gameDir>/mods/.forgestuck/<source-sha256>-t<TRANSFORMER_VERSION>-<code-fingerprint>.jar`.
  A nonempty existing file is a hit; content is not revalidated on a hit.
- A miss transforms into a temporary file, then moves it into place (atomically
  where supported). Original mods remain untouched.
- Any change affecting generated bytes, including mapping/resource changes,
  needs a `JarTransformer.TRANSFORMER_VERSION` bump. Otherwise unchanged input
  jars can silently reuse old output.
- For a cold reproduction, stop the game and remove only the relevant cached
  output in a disposable instance. Preserve original jars and the failing logs.
  A warm hit does not replay the per-class/resource transformation diagnostics.

## Log taxonomy

Java logs use logger `forgestuck` and tag `[Forge Stuck!!]`. Read the jar name,
class, owner, **method descriptor**, and mixin resource together. Enable DEBUG
for that logger when per-class remaps are needed.

| Level / recognizable text | Meaning and next action |
| --- | --- |
| INFO `candidate found`, `cache miss` / `cache hit for` | Establish which original jar entered transformation and which output was used. Native metadata bypass is silent; do not expect a native-bypass message. |
| INFO `bypass transform (provider/platform jar)`, `registered UNTRANSFORMED mod id(s)` | Explicit provider exclusion; different from metadata-based native bypass. |
| INFO `owner remap ... -> ... (... reference(s))`; DEBUG `remapped Forge owners in` | Per-jar totals / per-class detail from owner mapping. Counts are references, not proof of semantic equivalence. |
| WARN `has no verified equivalent ... owner left unmapped in this class` | Member signature is unsupported. Record the complete owner/name/descriptor; do not force a broad namespace replacement. |
| INFO `remapped ... legacy member name(s)` / `refmap ... remapped` | SRG bytecode or mixin/refmap token conversion. Inspect unresolved names separately. |
| WARN `dropped mixin ... from ... (reason)` | Missing target, unresolved owner, or unmapped member. Removal is from config/refmap references, not necessarily deletion of the class file. Check feature loss, even if startup succeeds. |
| WARN `has no mixins left and is effectively disabled` | The config contributes no remaining mixins; a nonfatal launch may still have lost behavior. |
| INFO `model loader ... -> ...` | Known ID conversion, e.g. `forge:bucket` → `neoforge:fluid_container`. Verify the resulting model in-game. |
| WARN `removed unknown model loader` | Default fallback removes the root `loader` property for unknown non-vanilla/non-NeoForge loaders. It can lose rendering/functionality; it is not a full port of that loader. |
| WARN `uses modLoader="kotori_scala"; compatibility fallback` | Metadata rewritten to `javafml`; Scala semantics may not survive. Other unavailable language providers warn that loading will likely fail. |
| WARN `class step ... failed ... leaving it unchanged` | That step's input is retained and processing continues; earlier changes remain. Inspect partial output, not just the final success line. |
| INFO `summary: ...`, `transformed jar created`, `registered mod id(s)` | Transformation totals, cache output written, and loader acceptance are separate milestones. None proves a successful world load. |
| ERROR `skipped ...` / INFO `loader did not accept` | Discovery/transform failure or rejected output. Capture the associated exception and output path. |

## Crash triage workflow

1. **Capture evidence.** Keep the full startup log and crash report, exact
   Minecraft/NeoForge/Java/mod versions, client/server side, original jar names
   and hashes, reproduction steps, and cold/warm cache status.
2. **Find the primary fatal chain.** Start at the exception that prevents
   discovery, initialization, resource reload, or world entry. Follow nested
   `Caused by` entries and suppressed exceptions to the missing class/member or
   failing callback. Earlier warnings are leads, not automatically the cause.
   Shutdown exceptions and later “failed to load” summaries may be secondary.
3. **Establish ownership from the jar, not the package alone.**

   | Stacktrace pattern | Likely investigation path (verify, do not assume) |
   | --- | --- |
   | Native mod callback + absent `.gametest.*` development class | Inspect that jar's NeoForge metadata, packaged classes, and gametest enablement. Reproduce without Forge Stuck where dependencies allow; do not invent a Binnie-specific remap. |
   | `NoSuchMethodError` / `NoClassDefFoundError` mentioning `net/minecraftforge`, `m_..._`, or a compat target | Identify the legacy input and cached output; compare the failing owner/name/descriptor with the mapping and diagnostics. A transformed legacy mod is a candidate owner, not a proven cause. |
   | JEI/plugin discovery or registration frame + missing integration class | Identify the jar supplying the plugin and the referenced optional API/version. The integration mod, JEI version, or transformed bytecode may be responsible; a JEI frame alone does not assign ownership to JEI. |
   | Mixin apply failure, invalid injection, or missing target | Match config, mixin class, target, and refmap to the transformed output and drop/remap logs. |

4. **Minimize and compare.**
   - Use a disposable instance with Forge Stuck, the suspect mod, and required
     dependencies; add optional integrations one at a time.
   - Reproduce native mods on their supported NeoForge runtime without Forge
     Stuck. Test legacy mods on their original supported Forge runtime when
     possible. Simply removing Forge Stuck from a NeoForge legacy-mod instance
     is not a valid upstream comparison.
   - Compare original/cached entries using `jar tf` and bytecode using
     `javap -c -s -p -classpath <jar> <class>`. Check descriptors and generated
     metadata/resources, not just class names.
   - Repeat with a cold cache and the proposed transformer build. If unchanged
     upstream inputs also fail on their supported runtime, report that evidence;
     if only rewritten output fails, identify the smallest responsible rewrite.
5. **Record confidence and limits.** If the pack or optional dependency is
   unavailable, report that limitation rather than claiming a smoke-run pass.

## Compatibility patch playbook

- [ ] Write down the exact failing jar/class, owner, member descriptor, opcode,
  resource, and expected target behavior before changing code.
- [ ] Choose the smallest existing extension point: exact owner/member mapping,
  a class step, explicit SRG entry/missing-target entry, model loader mapping,
  or metadata conversion. Keep each rule tied to verified target semantics.
- [ ] Preserve allowlists. Never bypass an unsupported-member check just to
  silence a warning. Validate method handles/lambdas, descriptors, fields, and
  mixin annotation references when they use the changed API.
- [ ] Avoid global namespace/string replacement, blanket exception swallowing,
  disabling all mixins/plugins, or dropping all classes with similar names.
  Existing broad fallbacks are not precedent for widening behavior.
- [ ] Preserve native detection: `neoforge.mods.toml` must exclude a jar from
  legacy transformation, even alongside `mods.toml`. Do not equate “1.21.1” or
  a filename substring with native metadata. Keep provider exclusions narrow.
- [ ] **Dev-only boundary:** there is currently no dedicated gametest guard or
  JEI-plugin handler. Missing development classes are not permission to suppress
  arbitrary registration failures. Any future guard needs a reproduced case,
  transformed-legacy-only scope, a proven dev-only callback/absent class, clear
  diagnostics, and tests that preserve native and ordinary runtime failures.
- [ ] Add positive and negative ASM/JUnit fixtures alongside existing tests:
  the failing pattern changes, neighboring/unrelated patterns remain untouched,
  and unsupported mappings still warn/drop only the intended mixin references.
- [ ] Bump the transformer cache version if output changes; update this guide's
  behavior/log guidance and changelog.

## Known pitfalls

- **Unmapped `LazyOptional` methods:** `ifPresent(NonNullConsumer)` requires both
  the owner **and descriptor** to point at local compat types. Unsupported
  signatures such as the `filter` fixture remain unmapped and can cause a mixin
  to be dropped. `LazyOptionalRemapTest` covers both paths; adding a method name
  without matching shim semantics is insufficient.
- **`RegistryObject` ≠ all of `DeferredHolder`:** the mapping verifies only
  `get()Object`, `getId()ResourceLocation`, and `getKey()ResourceKey`.
  `orElse` is not covered. Check erased descriptors and registry lifecycle;
  owner conversion does not make every legacy member valid.
- **Capability renames are not a full API port:** some current owner entries
  have no member allowlist. Verify actual target signatures/semantics rather
  than treating an existing mapping as evidence that every call works.
- **Optional plugin classloading:** a plugin can fail while JEI scans or
  instantiates it because an optional API is missing or incompatible. Inspect
  the plugin's owning jar and optional dependency boundaries before patching
  the loader. This is a triage pattern, not an implemented JEI fix here.
- **Dev/gametest reports:** a missing `binnie.core.gametest.CoreGameTests` from
  `BinnieCore.registerGameTests` is a reported investigation example, not a
  checked-in regression fixture. Verify packaging and environment first;
  native NeoForge mods should not be patched by the legacy transform path.
- **Successful degradation is still degradation:** removed model loaders and
  dropped mixins may hide broken rendering or gameplay. Exercise the affected
  feature, not only the title screen.
- **Simulator vs runtime:** the React UI and
  [`src/data/javaSources.ts`](../src/data/javaSources.ts) contain simulator/source
  presentation data. Use actual Java sources, produced jars, and game logs as
  runtime evidence; a simulator success is not a compatibility test.

## Validation before opening or updating a PR

Run commands from the repository root. Use Java 21 and the existing
[`build.gradle`](../build.gradle) JUnit infrastructure; do not add a new test
framework for a compat rule.

- [ ] Run the smallest relevant suite first, for example
  `./gradlew test --tests '*LazyOptionalRemapTest'`.
  Existing suites also include `LegacyJarDetectorTest`, `ForgeOwnerRemapTest`,
  `ResourceLocationClassTransformStepTest`, `SrgCompatTest`, and `JarTransformerTest`.
- [ ] After the patch is complete, run `./gradlew test build`. Check cache
  invalidation, metadata/signature conversion, native/dual-descriptor bypass,
  unrelated-class/resource preservation, unsupported members, and mixin scope.
- [ ] For UI changes only, use the existing `npm run lint` and `npm run build`.
  Docs-only updates need source/link/content checks, not game/build runs.
- [ ] Cold smoke-run: confirm the expected candidate → cache miss → relevant
  remap/drop/fallback diagnostics → transformed jar created → registration.
  Review every new warning and feature degradation; no unexpected class-step
  failures, missing methods/classes, or mixin failures.
- [ ] Warm smoke-run: confirm a cache hit and the same runtime behavior. Confirm
  native jars remain untransformed. Test the affected integration/feature,
  resource reload/rendering where relevant, and world load/play/save in a
  disposable instance. Test server-side behavior if the rule affects common code;
  this build currently configures a client run, not a dedicated server run.
- [ ] Record unrun checks and why. Do not call jar creation a smoke-run success.
- [ ] Review the diff for unrelated changes and secrets. Keep private logs out
  of commits; attach only sanitized excerpts.

PR description checklist (copy and fill):

- **Reproduction / ownership:** versions, side, suspect jar, primary fatal chain,
  minimal instance, and upstream-vs-transformed comparison.
- **Rule / boundaries:** exact change, why target semantics are valid, native
  bypass and unaffected cases, cache-version change, expected log differences.
- **Validation:** commands/results, cold/warm smoke results, feature/regression
  checks, remaining warnings, behavior loss, and checks not run.
- **Maintenance:** link this playbook and its dated note, or explain why
  behavior/diagnostics/workflow guidance did not change.

## Workflow guidance changelog

| Date | PR | Guidance change |
| --- | --- | --- |
## t26: retyped game fields, PlantType (log t0-b3ae421ed24b)

- `transformer/jar/GameFieldRepairStep.java` (after `SrgMemberRemapStep`) repairs `net/minecraft` field accesses whose 1.21.1 type differs from the legacy descriptor:
  - Holder-wrapped statics (MobEffects, Attributes, Potions, SoundEvents, GameEvent...; table `forgestuck/game-field-holders.txt`, generated from the 1.21.1 jar): GETSTATIC target desc + `Holder.value()` + CHECKCAST legacy type. Limit: `value()` needs a bootstrapped registry; identity differs from the Holder.
  - `BuiltInRegistries.POTION` DefaultedRegistry -> Registry; `invokeinterface DefaultedRegistry.*` (except `getDefaultKey`, no counterpart) re-owned to `Registry`.
  - `BuiltInLootTables.EMPTY` -> ResourceKey + `location()`; `AttributeModifier$Operation` and `EquipmentSlot$Type.ARMOR` renames.
  - `Enchantments.*` (now ResourceKey): per-call failure.
- `compat/PlantType` replaces the removed Forge `PlantType`: interned by name, legacy constants, `getName`. No engine behavior (`IPlantable.getPlantType`, `canSustainPlant` unmapped).
- Scanned but NOT repaired (retyped): `Advancement$Builder.criteria`, `ModelBakery.MISSING_MODEL_LOCATION`, `CauldronInteraction.EMPTY/WATER`, `CraftingBookCategory.CODEC`, `LootPool.entries`, `PotionBrewing$Mix.from/to`. Missing names left to call-time failure (e.g. `ItemStack` tag field, `Item` UUID fields, `Options.f_92063_`).
- Top remaining unmapped/unverified owners in the log: RegistryObject, IEventBus, Mod, FluidStack, GatherDataEvent, FMLJavaModLoadingContext, FMLClientSetupEvent (counts are mentions, not all failures); `MobEffectEvent$Remove`, `NotCondition.<init>`, `RenderGuiOverlayEvent$Post.<init>` have no verified equivalent.
- Not verified in game.
| 2026-10-17 | [#13](https://github.com/Minionade/forge-stuck/pull/13) | Establish source-backed triage, log taxonomy, narrow patch boundaries, cache-aware validation, and the agent maintenance contract; link from README. |
| 2026-10-16 | [#14](https://github.com/Minionade/forge-stuck/pull/14) | Registration adapters, `LegacyItemStackShim`, jar-wide owner blocking, owner/descriptor-aware SRG lookup, data-pack migration, preserve-unknown-model-loaders; see "Remaining blockers" below. |
| 2026-10-17 | follow-up PR (t11) | Root cause of `fml.modloadingissue.javafml.dangling_entrypoint [thermal_foundation; …]` from the real-jar run of #14 (t10): `JarTransformer.convertModsToml` renamed modId `thermal_foundation`→`thermal` while `cofh.thermal.foundation.ThermalFoundation` still had `@Mod("thermal_foundation")`, so no container matched and ModLoader entered a broken state (all later "Cowardly refusing" errors are downstream). Fix: no id rewriting; `@Mod`/modId validation + regression tests; `Object` methods no longer block owner mapping; `enqueueWork(Supplier)` supported; `TRANSFORMER_VERSION` 11. Only the next real `runClient` can verify pack-level behavior. |
| 2026-10-17 | [#15](https://github.com/Minionade/forge-stuck/pull/15) follow-up (t12) | Transform legacy jar-in-jar nested jars (t11 log: all thermal_* modules failed with `Mod ID thermal ... [MISSING]`). `TRANSFORMER_VERSION` 12. Read-only scan of the 19 local mod jars: only nested legacy jar is thermal_core; other nested jars are native; remaining missing dependency ids (`jei`, `jsonthings`, `mezz_config_gui`) are optional/not-legacy gaps. |
| 2026-10-17 | [#16](https://github.com/Minionade/forge-stuck/pull/16) | Transformed-jar cache moved from `<gameDir>/.forgestuck/cache` to `<gameDir>/mods/.forgestuck` (`ForgeStuckCandidateLocator.CACHE_DIR_NAME`). NeoForge's `ModsFolderLocator` (verified with javap on loader 4.0.42) lists `mods/` non-recursively and accepts only regular `*.jar` files, and `isScannable` is also non-recursive, so subfolder jars are not loaded twice. The old `.forgestuck/cache` is left untouched and logged. Output unchanged, so `TRANSFORMER_VERSION` stays 12. |
| 2026-10-17 | follow-up PR (t13, config/dist/closure) | t12 real-run "Failed to wait for future Mod Construction, 6 errors". Adapters (all members reflection-checked in `ForgeOwnerMappingTargetsTest`): `ForgeConfigSpec`(+`Builder`, `ConfigValue`, Boolean/Int/Long/Double/Enum values)→`ModConfigSpec`, `ModConfig`/`ModConfig$Type`/`ModConfigEvent`, `MinecraftForge.EVENT_BUS`→`NeoForge.EVENT_BUS`, `FMLEnvironment`/`FMLPaths`/`ModList`/`ModContainer`/`LogicalSide`, compat `DistExecutor` and `ModLoadingContext` (`registerConfig` forwards to the active `ModContainer`), widened `IEventBus.addListener` overloads. New generic **dependency closure** in `ForgeOwnerRemapStep`: if a member of a mapped owner has a descriptor mentioning a blocked mapped owner, that owner is blocked too (e.g. `MinecraftForge.EVENT_BUS` when `IEventBus` is blocked) so field descriptors never diverge. `TRANSFORMER_VERSION` 13. Only the next real `runClient` can verify pack-level behavior. |
| 2026-10-17 | follow-up PR (t14, link policy) | t13 real run: adapters from the t13 PR did not take effect because blocking was **jar-wide**: one unsupported member left the owner unmapped in every class (cofh_core: `ForgeConfigSpec.setConfig(CommentedConfig)V` blocked `ForgeConfigSpec$Builder`; tconstruct: `ModContainer.getModInfo()` blocked `ModContainer` and, via the closure, `ModLoadingContext`; mantle: `IForgeRegistry.register`, `IEventBus.post`, `RegisterEvent.getForgeRegistry`). New policy: owners are always mapped; each call/field access to a non-whitelisted member of a mapped owner (not `<init>`, not an `Object` method) is replaced in place by `UnsupportedForgeApi.fail(member, caller)` (stack-neutral, throws `UnsupportedOperationException` with a precise message only if that path runs) and is logged (`OwnerRemapReport` WARN per member, ERROR per owner still left unmapped). Constructors, method handles/invokedynamic and Mixin classes still block the owner **in that class only** (Mixins are dropped as before). Event/lifecycle owners (`event/server/*`, `event/entity/**`, `event/level/*`, `client/event/*`, `fml/event/lifecycle/InterModProcessEvent`, `InterModComms`, `eventbus.api.Event` with `<init>` only) are generated by the test-side `OwnerTableGenerator` into `forge-owner-members.txt` (same-named NeoForge class exists + member present with identical owner-mapped descriptor; `LivingDamage`/`TickEvent` excluded, `LivingHurt`/`LivingAttack`/`PlayerTickEvent` have no same-named target so stay unmapped). `ResidualOwnerGuardTest` fails if a transformed class still references a mapped owner (set `FORGESTUCK_SCAN_DIR` to scan real transformed jars). `TRANSFORMER_VERSION` 14. Only the next real `runClient` can verify pack-level behavior. |
| 2026-10-17 | follow-up PR (t15, game-layer compat + startup progress) | t14 real run: thermal `TCoreRecipeTypes` failed with `NoClassDefFoundError: net/minecraft/core/registries/Registries` thrown from `LAYER SERVICE/main/...compat.ForgeRegistries.<clinit>`. Root cause (from the stack, not a mangled owner): Forge Stuck is both a service (locator) and a mod, FML loads it once into the **service layer**, which cannot see Minecraft/NeoForge game classes, so every compat class that touches them fails when a transformed mod (game layer) links to it. Fix (generic): the locator writes a deterministic relocated copy of `net.neoforged.forgestuck.compat` as `mods/.forgestuck/forgestuck-compat-<hash>.jar` (`CompatRelocation`, `FMLModType: LIBRARY`, module `forgestuck_gamecompat`, package `...forgestuck.gamecompat`), and `JarTransformer.forRuntime()` appends `CompatRelocationStep` so transformed classes reference the game-layer copy. New compat classes must be added to `compat-classes.txt` (`CompatRelocationTest` enforces it). Also: `GatherDataEvent` mapped (`data/event/*` added to the generator). Startup progress: `TransformProgress` callbacks in `JarTransformer`, `StartupProgress` formats `Forge Stuck: transforming X (3/19)` / `using cached` / `scanning nested jars` / `done (N transformed, M cached)` and `FmlStartupDisplay` drives loader 4.0.45 `StartupNotificationManager.addProgressBar/popBar/addModMessage` + `ProgressMeter.setAbsolute/label/complete` (javap-verified), every call guarded so a missing API degrades to log-only; per-jar timing is logged. `TRANSFORMER_VERSION` is now fixed at `0` until the official release (cache is rebuilt by hand during testing). Not verified in game. |

## Documented blockers from the t14 log
- `ItemDisplayContext.create(String,ResourceLocation,ItemDisplayContext)` (tconstruct `TinkerItemDisplays`): javap on NeoForge 21.1.262 shows `ItemDisplayContext` implements `net.neoforged.fml.common.asm.enumextension.IExtensibleEnum` with only `getExtensionInfo()`; there is no runtime factory. NeoForge extends enums declaratively (`META-INF/enumextensions.json`), which cannot be derived from a bytecode call, so this stays a blocker (`NoSuchMethodError`, tconstruct construction fails).
- `net/minecraftforge/network/NetworkRegistry$ChannelBuilder` / `SimpleChannel` (cofh_core): NeoForge payload networking (`RegisterPayloadHandlersEvent`, `CustomPacketPayload` + `StreamCodec`) has no 1:1 API; no adapter was faked.
- `IConditionSerializer` (mantle, only reached from its JEI plugin): codec-based `ICondition` in NeoForge; no verified equivalent.
- Remaining unmapped owners ranked on the t14 jars (refs/classes/jars): `FluidStack` 5025/459/12, `IFluidHandler$FluidAction` 949, `IFluidHandler` 786, `ICondition` 773, `FluidType$Properties` 767, `Capability` 677, `Tags$Items` 608, `ItemModelBuilder` 432, `IItemHandler` 368, `IGeometryBakingContext` 362, `ExistingFileHelper` 342, `ModelData` 320, `ToolAction` 276, `FluidType` 219, `ModelProperty` 206, `GatherDataEvent` 164 (now mapped), `IEnergyStorage` 156, `NetworkDirection` 148.
## Link policy: unsupported members

Moving failure from link/class-init time to the exact unsupported call is not swallowing: the call still throws, and it is reported at transform time. Remaining unmapped (blocker) owners ranked from the t13 transformed jars: `FluidStack`, `RegistryObject` (one jar), `IFluidHandler(+FluidAction)`, `ICondition`/`IConditionSerializer` (no verified NeoForge equivalent; codec based), `FluidType$Properties`, `Capability`/`ForgeCapabilities`, `Tags$Items` (`forge:` vs `c:`), datagen builders (`ItemModelBuilder`, `ExistingFileHelper`, `GatherDataEvent`), `ModelData`, `IItemHandler`, `ToolAction`, `NetworkDirection`, `IEnergyStorage`, `ForgeHooks`, `ForgeMod`. Scala mods (bdlib, advgenerators) fail with "must have exactly 1 public constructor, found 0": a language-provider blocker, not a link error. gendustry (not Forestry, which is native) was the real referrer of the `ServerStoppingEvent` failure.

## Remaining blockers and validation status
- Validation run: `./gradlew test build` (Java 21). Baseline before this work:
  32 of 51 tests failed with `NoClassDefFoundError: com/google/gson/JsonElement`
  (fixed with a gson test dependency). Tests needing Minecraft classes use the MDG
  `unitTest` config plus `testsupport/TestBootstrap`; because of the split
  classloaders, fixture tests must resolve NeoForge types through the same loader
  (see `RegistrationLifecycleTest`).
- **Not run:** cold/warm game launch, world create/save/reload, any real mod jar
  (Thermal, TConstruct, Mantle, BdLib, …). Pack-level compatibility is unproven.
- **Likely next registration blockers (from the t10 real-jar log; jar-wide blocking was removed in t14, see "Link policy"):** these members now fail at call time instead of unmapping the owner. Seen: `ForgeRegistries$Keys.FLUID_TYPES`/`GLOBAL_LOOT_MODIFIER_SERIALIZERS`, `ForgeRegistries.ENCHANTMENTS`, `IEventBus.post(Event)` (NeoForge returns the event), `IForgeRegistry.tags()`, `DeferredRegister.createOptional`, `IEventBus.addGenericListener`, and the nested Forge `thermal_core` jar-in-jar. `kotori_scala` mods (bdlib, cofh_core) are only metadata-converted.
- **Unsupported:** Forge capability API, Forge events beyond the three lifecycle
  events (NeoForge uses `ICancellableEvent`), `DeferredRegister.createOptional`/
  `makeRegistry`/tag helpers, ENCHANTMENTS/BIOMES in compat `ForgeRegistries`,
  Scala mods, Forge `forge:` tag → `c:` migration, recipe result schema.
- **Hypotheses not yet proven with real jars:** Thermal `FURNACE_RECIPE` field
  linkage and cross-jar descriptor mismatches (jar-wide blocking only removes
  intra-jar divergence); the symptom list (`thermal:config`, `tconstruct:island`,
  slime trees, shield banner sprite source, trim-material ingredients, JEI
  linkage) is not explained by any real-jar reproduction yet.
- Next steps: obtain real jars and reproduce with the transformed cache output;
  extend `ForgeOwnerMappings` only with members verified by
  `ForgeOwnerMappingTargetsTest`.
| 2026-10-17 | follow-up PR (t16, module reads + cache key) | t0/PR #19 real run: forestry constructs, but IllegalAccessError: class ...gamecompat.ForgeRegistries (in module forgestuck_gamecompat) cannot access ...Registries (in module minecraft) because module forgestuck_gamecompat does not read module minecraft; the stack shows LAYER PLUGIN/forgestuck_gamecompat. Root cause: the relocated compat jar was FMLModType: LIBRARY, which FML (loader 4.0.42, IModFile.Type = MOD/LIBRARY/GAMELIBRARY) puts in the PLUGIN layer, which is built before and without Minecraft. Fix: manifest is now GAMELIBRARY, so the jar is an automatic module in the GAME layer with minecraft (automatic modules read every module of their configuration). CompatRelocationTest resolves a real ModuleLayer with a fake explicit minecraft module and asserts canRead (the isolated-layer variant cannot). Cache key is now <sha>-t<VERSION>-<fp>.jar where p hashes Forge Stuck's own jar (or class/resource tree in dev) plus the transformer step list (JarTransformer.cacheFileName), so code changes invalidate caches while TRANSFORMER_VERSION stays  . Not verified in game. |

## Status of the remaining t0 blockers (t16)
- ItemDisplayContext.create: NeoForge extends enums only through META-INF/enumextensions.json (+ IExtensibleEnum.getExtensionInfo); generating it would need the constant args, ordinal/id semantics and a rewrite of every static-field initializer to `valueOf`. Not implemented; not verifiable without the format spec and a real run. It already fails per call (NoSuchMethodError), but tconstruct calls it from a static initializer.
- NetworkRegistry/SimpleChannel (cofh_core): the owner is unmapped, so linking fails with NoClassDefFoundError. A stub mapping with per-call failure would only move the failure, since cofh_core builds its channel during construction; no faithful payload adapter exists. Blocker.
- IConditionSerializer (mantle JEI plugin) and Scala mods (bdlib, advgenerators: language provider kotori_scala finds no public constructor): unchanged blockers.

## t17 notes (2026-10-17)

- **Root cause of NoSuchFieldError ... Registries f_279569_:** `srg-members.properties` held ~6 hand-written entries. `f_279569_` = `Registries.CREATIVE_MODE_TAB` (exists in 1.21.1). The table is now generated (~50k entries) by `tools/SrgTableGenerator.java` from Mojang 1.20.1 client.txt + MCPConfig 1.20.1 joined.tsrg + the 1.21.1 merged jar; an entry is emitted only if the Mojang-named declaring class exists in 1.21.1 and declares a same-named member (name + arg count checked, not full descriptors).
- `SrgResidualTest` guards residual `f_/m_` refs to game owners (opt-in `FORGESTUCK_SCAN_DIR` for real jars). Remaining residuals on real jars are 1.20.1 members removed in 1.21 (e.g. `VertexConsumer.endVertex`, vertex/uv/color builders, `Tesselator.end`): genuinely unsupported, fail with `NoSuchMethodError` at the call.
- `Event.<init>` is protected in NeoForge; mapping now declares it.
- `ForgeClassTransformer` now also matches the SRG names of ItemStack `getTag/getOrCreateTag/setTag/hasTag` (production jars use SRG); `LegacyItemStackShim` moved to `compat` (game-layer relocated jar) and gained `hasTag`.
- **Blockers (documented, not mapped):** `ModelEvent$RegisterAdditional` (NeoForge takes `ModelResourceLocation`, not `ResourceLocation`), `MobEffectEvent$Remove` (requires new `EffectCure`), `IConditionSerializer`, `NetworkRegistry$ChannelBuilder`, `ItemDisplayContext.create`, Scala mods.
- Not verified in game; only a real runClient can verify.

## Scala mods (kotori_scala) - t17 (2026-10-17)

- **Root cause:** `JarTransformer.convertModsToml` rewrote every non-javafml `modLoader` to `javafml`, so FML's Java provider tried to construct Scala `object` mods (private ctor, `MODULE$`) and failed with *exactly 1 public constructor, found 0* (bdlib `BdLib`, advgenerators `Generators`).
- **Finding:** `ScalableCatsForce-NeoForge-3.7.1-build-11-with-library.jar` in the pack is a NeoForge 1.21.1 `IModLanguageLoader` named `kotori_scala` (`com.kotori316.scala_lib.ScalaLanguageProvider`, FMLModType LIBRARY, bundles the Scala library). javap shows its `ScalaModContainer` reads the `MODULE$` field of Scala objects, falls back to a constructor taking IEventBus/ModContainer/Dist, creates the mod event bus and runs `AutomaticEventSubscriber.inject`. It already does what a Forge Stuck provider would, so no second provider was written.
- **Fix:** non-javafml loaders keep their loader id and `loaderVersion` in `neoforge.mods.toml`; javafml still maps to `[1,)`. If no matching provider jar is installed FML reports it explicitly.
- **Unverified:** that 1.20.1 Scala bytecode (bdlib/advgenerators, Scala library version) links under the bundled Scala library, and that the version `3.7.1-build-11` satisfies `[3.3.0,)`. Only a real runClient verifies this.

## t18 notes (2026-10-17)

- `ForgeRegistries$Keys` now maps the constants with a reflection-verified `NeoForgeRegistries.Keys` counterpart: GLOBAL_LOOT_MODIFIER_SERIALIZERS, BIOME_MODIFIER_SERIALIZERS, STRUCTURE_MODIFIER_SERIALIZERS, FLUID_TYPES, HOLDER_SET_TYPES, BIOME_MODIFIERS, STRUCTURE_MODIFIERS, ENTITY_DATA_SERIALIZERS (plus `ForgeRegistries.ENTITY_DATA_SERIALIZERS`). This removes the `UnsupportedOperationException` in `ThermalCore.<clinit>`. Caveat: key mapping is not value mapping; Forge `Codec` loot-modifier values vs NeoForge `MapCodec` still differ and fail at the registering call if used.
- `DatagenModLoader` (`isRunningDataGen`) -> `net/neoforged/neoforge/data/loading/DatagenModLoader` (javap-verified); the referrers are advgenerators' ic2c/mekanism integrations, not thermal. `OwnerTableGenerator` also maps `data/loading/*`.
- **Blocker, not mapped:** `net/minecraft/world/level/storage/loot/Serializer` (removed in 1.21; used by TConstruct `*ConditionSerializer` classes). Loot conditions/functions are codec-based now; no faithful adapter exists.
- Still-unmapped owner ranking on the converted pack (counts of class-file references): fluids (FluidStack 2366, IFluidHandler($FluidAction) ~1180, FluidType 200, ForgeFlowingFluid), capabilities (Capability 574, IItemHandler 284, IEnergyStorage 95, ForgeCapabilities), ExistingFileHelper 378, ICondition 366, client model geometry (IGeometryBakingContext 264, ModelData 211), NetworkEvent$Context 178, ToolAction 156. These are API redesigns in 1.21 and need dedicated, individually verified adapters; none is a cheap rename.
- Unchanged blockers: IConditionSerializer, NetworkRegistry$ChannelBuilder, ItemDisplayContext.create, MobEffectEvent$Remove, ModelEvent$RegisterAdditional. Not verified in game.

## t19 (2026-10-17): Scala mod event bus, loot `Serializer` analysis

Log: t0-cd293aa1fed8 (PR #22 code).

### Root cause fixed: "The active mod container has no event bus" (second uncaught parallel error)
`advgenerators` `Generators$.<clinit>` -> `bdlib` `RegistryManager.init` -> `FMLJavaModLoadingContext.getModEventBus()` threw, during `ScalaModContainer.constructMod` (javap of the kotori_scala container). That container sets the active `ModLoadingContext` container to `null` before reading `MODULE$`; `ModLoadingContext.getActiveContainer()` then falls back to the `minecraft` container, whose `getEventBus()` is `null`. (Not a pre-existing bdlib bug: Forge's context was always the mod being constructed.)
Fix (`compat/FMLJavaModLoadingContext`): when the active container has no bus, resolve the caller's named module via `StackWalker`, match it to `IModInfo.getOwningFile().moduleName()` in `ModList`, and use that mod container's bus. Same behaviour as before whenever the active container has a bus. Not verified in game.

### Analysis, not implemented (documented blockers)
- `net/minecraft/world/level/storage/loot/Serializer` (bdlib `BdLib$.<clinit>`, TConstruct/Mantle/CoFH condition serializers): in bdlib it is not a bare class reference. `LootFunctionTypes$` does `new LootItemFunctionType(Serializer)` with a `KeepDataLootFunctionSerializer`. In 1.21.1 `LootItemFunctionType` is a record over `MapCodec<T>`, so a compat `Serializer` interface alone would only move the failure to `NoSuchMethodError` on the constructor. A faithful fix needs a `new/dup/invokespecial` rewrite to a factory that wraps a JSON `Serializer` as a `MapCodec` (JsonOps bridge, plus `LootItemConditionType` and `LootNbtProviderType` equivalents). Not done: not verifiable without running the codecs; no stub registrations were added.
- `ItemDisplayContext.create(String, ResourceLocation, ItemDisplayContext)`: 1.21.1 uses `IExtensibleEnum` + `META-INF/enumextensions.json`. Feasible design: statically detect constant args in `TinkerItemDisplays.create`, emit the JSON entry (ctor descriptor from the 1.21.1 enum) into the transformed jar, and rewrite the call to `ItemDisplayContext.valueOf(name)`. Not implemented (needs bytecode constant tracing and the real enum constructor signature check).
- `IConditionSerializer` and `NetworkRegistry$ChannelBuilder`: unchanged. Conditions are now `MapCodec`-based, and SimpleChannel has no 1:1 payload-registration equivalent (needs per-message codecs from reflection on legacy encoder/decoder lambdas). No verified adapter.

| Date | Change |
| --- | --- |
| 2026-10-17 | t19: mod-event-bus fallback for providers that clear the active container (Scala); loot `Serializer`, `ItemDisplayContext.create`, conditions, `ChannelBuilder` analysed and left documented. |
## t20 (2026-10-17): `ItemDisplayContext.create` enum extensions, legacy loot `Serializer` bridge

Supersedes the "not implemented" bullets for these two items in t16/t19 above.

### `ItemDisplayContext.create` -> `META-INF/enumextensions.json`
- Schema verified against FML loader 4.0.42 `EnumPrototype.load` / `RuntimeEnumExtender`: entry `{enum, name, constructor, parameters}`; `name` must be a Java identifier starting (case-insensitively) with the owning mod id; the `@NamedEnum` parameter must be `modid:path`; the `@IndexedEnum` parameter is `-1`. The 1.21.1 constructor is `ItemDisplayContext(int, String, String fallbackName)`; the `(int,String)` one is `@ReservedConstructor`. The file is referenced by `enumExtensions` in the mod's `[[mods]]` entry (read by `LoadingModList.addEnumExtenders`).
- `transformer/jar/LegacyEnumExtensionPlanner` is a small concrete bytecode interpreter run on the **original** classes (before `ResourceLocation` rewriting). Roots: `<clinit>` and static no-arg methods of classes mentioning `ItemDisplayContext`. It follows same-jar static calls with real arguments, `getstatic` of `ItemDisplayContext` constants, `Locale.ROOT`, `String.toUpperCase/toLowerCase/concat`, `makeConcatWithConstants`, `ResourceLocation` constructors/factories, and branches on known values. Any unknown value on a branch aborts that root; create sites that are never resolved are logged as WARN.
- `EnumExtensionJson` writes `META-INF/forgestuck/enumextensions-<modId>.json` per mod id, containing only entries whose id namespace is that mod id (FML validates the prefix per mod). Constant names are `MODID_KEY`-style and unique.
- `LegacyEnumCreateRewriteStep` redirects every `ItemDisplayContext.create(String,ResourceLocation,ItemDisplayContext)` to `compat/LegacyEnumExtensions.itemDisplayContext`, which looks up the generated constant by serialized id at run time. If no constant exists (non-constant args, wrong namespace) it throws `UnsupportedOperationException` naming the id, only when that call runs.
- Limits: roots are static initializers/static no-arg methods only; instance-state or event-driven creation is not resolved. Ordinals differ from Forge (constants are appended by NeoForge). Verified by unit tests (planner, schema, end-to-end jar, rewritten call); the real `RuntimeEnumExtender` was not run on the generated file and nothing was run in game.

### Legacy loot `Serializer` -> `MapCodec`
- 1.21.1 `LootItemFunctionType`, `LootItemConditionType`, `LootNbtProviderType`, `LootPoolEntryType`, `LootNumberProviderType`, `LootScoreProviderType` are records over `MapCodec` (javap-verified). 1.20's `Serializer<T>` is mapped to `compat/LegacyLootSerializer` (same erased `serialize(JsonObject,Object,JsonSerializationContext)V` / `deserialize(JsonObject,JsonDeserializationContext)Object`).
- `LegacyLootTypeRewriteStep` (runs before owner remapping) turns `new XType(Serializer)` into `LegacyLootBridge.xType(serializer)`, which builds a `MapCodec` over the legacy JSON methods: decode converts the `MapLike` to a `JsonObject` (JsonOps), calls `deserialize`, errors become `DataResult` errors; encode calls `serialize` and converts the result back. A runtime test round-trips a JSON object through rewritten fixture classes in a real classloader.
- Limits: the serialization/deserialization contexts are plain Gson, so nested loot elements requested through the context fail with an error naming the type. Subclasses of Forge/vanilla 1.20 serializer base classes (e.g. `LootItemConditionalFunction.Serializer`) and `getSerializer()` callers are not covered and still fail per call or at link time.

### `IConditionSerializer` (Mantle): evaluated, not implemented
Mantle uses `IConditionSerializer`, `ICondition`, `ICondition$IContext` (its own `DataLoadedConditionContext` implements it), `CraftingHelper` registration/JSON lookup, `TrueCondition`/`FalseCondition`/`NotCondition`. NeoForge 1.21.1 conditions are `MapCodec`-based, `ICondition.IContext` differs, and `CraftingHelper` has no equivalent, so a linking-only interface would move the failure to the first `CraftingHelper` call without preserving registration or JSON semantics. Not done; `NetworkRegistry$ChannelBuilder` unchanged.

| Date | Change |
| --- | --- |
| 2026-10-17 | t20: enum-extension generation for `ItemDisplayContext.create`, loot `Serializer` -> `MapCodec` bridge; conditions and `ChannelBuilder` still documented blockers. Not verified in game. |

## t21 (2026-10-17): ResourceLocation subclasses and legacy conditions

- ResourceId-style subclasses: ctor adaptation + AT (public-f ResourceLocation) in META-INF/accesstransformer.cfg; AT application unverified in game.
- IConditionSerializer/CraftingHelper.register/getCondition/processConditions/serialize map to compat LegacyConditions (MapCodec bridge registered at RegisterEvent for CONDITION_CODECS). Legacy ICondition implementers get a generated codec(). Not preserved: forge:* builtin ids, And/Or array ctors, ConditionalRecipe builders, ICondition.getID() calls.
- NetworkRegistry/SimpleChannel (cofh_core, bdlib, advgenerators): compat LegacySimpleChannel/LegacyNetworkRegistry/LegacyNetworkContext/LegacyNetworkDirection/LegacyPacketDistributor over NeoForge payload registration (RegisterPayloadHandlersEvent on the registering mod's bus; one payload type <ns>:<path>/<id> per registerMessage). Preserved: encoder/decoder over FriendlyByteBuf, consumer on the network thread, enqueueWork/getSender/getDirection/setPacketHandled, direction restriction, PLAYER/ALL/SERVER/DIMENSION/NEAR/TRACKING_* targets. Not preserved (fail at the call): accepted-version predicates (exact version compare; ACCEPTVANILLA/ABSENT -> optional), LOGIN messages, messageBuilder, isRemotePresent, registering after the registry event. Timing relative to FMLCommonSetupEvent unverified. Tested: codec round trip + handler/context with a proxy IPayloadContext (LegacySimpleChannelTest).
- FluidType/FluidType.Properties map to the NeoForge classes with a reflection-verified whitelist (BlockPathTypes-based members are not whitelisted).
- LanguageLoaderCheck: before transforming, a legacy jar with a non-javafml modLoader is checked against FMLLoader.getLanguageLoadingProvider(); an ERROR naming mods, loader id, range and install hint (kotori_scala -> ScalableCatsForce NeoForge 1.21.1 with-library) is logged. Metadata keeps modLoader/loaderVersion; FML still reports the missing provider itself. If providers are not yet known at locator time the check is skipped (debug log). Not verified in game.

| 2026-10-17 | t21: ResourceLocation subclasses, conditions, network adapter, FluidType, language-loader check | PR #25 |



## t22 (2026-10-17): EventNetworkChannel, ItemAbility, BaseFlowingFluid, RegisterCapabilitiesEvent

- `NetworkRegistry.ChannelBuilder.eventNetworkChannel()` -> `compat/LegacyEventChannel` (cofh_core `PacketHandler`: `registerObject` with `@SubscribeEvent` handlers taking `NetworkEvent$ClientCustomPayloadEvent`/`ServerCustomPayloadEvent`, `getPayload()`, `getSource().get()` context, and `NetworkDirection.buildPacket(Pair, ResourceLocation).getThis()` for sending). One NeoForge payload type named after the channel carries the raw remaining bytes both ways; events map to `compat/LegacyNetworkEvent`, `ICustomPacket` to `LegacyCustomPacket`. Not preserved: `ChannelRegistryEvent`, `addListener(Consumer)` (fails per call: Forge inferred the event type from generics), accepted-version predicates, LOGIN packets. Tested with raw codec round trip + dispatch by direction (`LegacyEventChannelTest`).
- `ToolAction`/`ToolActions` -> `ItemAbility`/`ItemAbilities` (reflection-checked constants and `get`/`name`/`getActions`); `canPerformAction(ItemStack, ToolAction)` overrides get their descriptors rewritten by the owner remap. `ForgeFlowingFluid` (+`$Source`/`$Flowing`/`$Properties`) -> `BaseFlowingFluid` (same erased ctor/builder signatures; `canMultiply` etc. fail per call).
- `RegisterCapabilitiesEvent` is mapped to NeoForge's event so Mantle/bdlib link; `register(Class)` is redirected by `ForgeOwnerRemapStep.REDIRECTS` to `LegacyCapabilities.register`, which only records the declared legacy capability class. NOT done: `Capability`/`CapabilityManager`/`ICapabilityProvider`/`getCapability` routing onto Block/Item/EntityCapability; those still fail or stay unmapped (574 refs, top remaining blocker).
- bdlib `BdLib$.<clinit>` failure was a nested `NoClassDefFoundError RegisterCapabilitiesEvent`; expected resolved by the mapping, unverified.
- Validation: `.\gradlew.bat test build` passes. Not verified in game.

| 2026-10-17 | t22: EventNetworkChannel, ItemAbility, BaseFlowingFluid, RegisterCapabilitiesEvent link | PR (see GitHub) |

## t23 (2026-10-17): leftover SRG members, ENCHANTMENTS, RenderGuiOverlayEvent, registerMessage descriptor

- `BuiltInRegistries.f_256876_` is `ENCHANTMENT`. 1.21.1 has no such registry (enchantments are datapack-driven), so `srg-members.properties` correctly has no entry; this was a removed member, not a descriptor mismatch. `SrgMemberRemapStep` now rewrites any still-SRG member reference to a Minecraft/NeoForge owner (non-Mixin classes, not own-class) into a call-time failure via the shared `ForgeOwnerRemapStep.failAtCall`; the class links, the path throws `UnsupportedOperationException` naming the member. This does NOT make `Loadables.<clinit>` succeed: mantle reads that registry in a static initializer, so Mantle/TConstruct remain blocked until that code path is replaced. Test: `SrgResidualTest.unmappedSrgMemberOnGameOwnerLinksAndFailsOnlyWhenExecuted` (loads the rewritten class and executes it).
- Retyped fields (name resolves, descriptor differs) cannot be repaired at transform time (no target class data; `SrgTableGenerator` needs Mojang/MCP files not available here). Opt-in guard `FieldDescriptorGuardTest` (`FORGESTUCK_SCAN_DIR`) reflects every game-owner field ref in transformed jars against the 1.21.1 classes for name+descriptor mismatches.
- `ForgeRegistries.ENCHANTMENTS`/`Keys.ENCHANTMENTS` -> `compat/DatapackRegistryView`: holds `Registries.ENCHANTMENT`, so `CoFHCore.<clinit>`'s `new DeferredRegisterCoFH<>(ForgeRegistries.ENCHANTMENTS, ID)` links; lookups and `LegacyDeferredRegister.register` fail per call (needs a RegistryAccess; code cannot register datapack objects). Whether cofh's own `DeferredRegisterCoFH` then works is unverified.
- `RenderGuiOverlayEvent(+$Pre/$Post)` -> `RenderGuiLayerEvent(+$Pre/$Post)`; only `getGuiGraphics()` (and `isCanceled/setCanceled` on Pre) are mapped. `getPartialTick()F` (layer: `DeltaTracker`), `getOverlay()` (NamedGuiOverlay vs layer `ResourceLocation`, mantle compares against `VanillaGuiOverlay`, which is unmapped), `getWindow()` and constructors fail per call. Mantle's overlay handlers therefore link but fail when they run.
- `SimpleChannel.registerMessage` call sites use return type `IndexedMessageCodec$MessageHandler`; the whitelist had `I`, so the Optional overload (bdlib `NetChannel`, Mantle) was never matched. Whitelist and adapter now return `compat/LegacyMessageHandler` (holds only the index; builder-style members fail per call); both overloads incl. `Optional<NetworkDirection>` are supported (`LegacySimpleChannelTest`).
- Validation: `.\gradlew.bat test build` passes. Not verified in game.

## t24: enchantment registry view, ForgeMod, instance subscribers, TextureStitchEvent

- `BuiltInRegistries.f_256876_` (ENCHANTMENT) no longer exists in 1.21.1 (datapack registry). `SrgMemberRemapStep.REPLACEMENTS` redirects it to `compat/LegacyEnchantmentRegistry.ENCHANTMENT`, a `Registry` proxy forwarding to the live `RegistryAccess` (server via `ServerLifecycleHooks`, else client level). Static init (Mantle `Loadables`) links; lookups with no server/level fail per call with a message naming `RegistryAccess`. No entries are added. Unverified: whether datapack-load-time lookups occur after the server is set.
- `ForgeMod` -> `compat/LegacyForgeMod`: `LAVA_TYPE`, `WATER_TYPE`, `SWIM_SPEED`, `NAMETAG_DISTANCE`, `enableMilkFluid()` (backed by `NeoForgeMod`). Reach/gravity/step-height attributes have no NeoForge field (vanilla attributes in 1.21) and fail per call.
- `LegacyInstanceSubscriberStep`: on `@Mod.EventBusSubscriber`/`@EventBusSubscriber` classes, `@SubscribeEvent` is removed from instance methods. Forge 1.20.1 `register(Class)` only registered static methods and ignored instance ones; NeoForge 1.21.1 throws. Static bridges were rejected: Scala companions already emit static forwarders, so bridges would double-register.
- `TextureStitchEvent$Post` -> `TextureAtlasStitchedEvent` (`getAtlas()` only). `$Pre` and other members fail per call.
- Validation: `.\gradlew.bat test build` passes. Not verified in game.
| 2026-10-17 | t23: leftover SRG -> call-time failure, ENCHANTMENTS view, RenderGuiLayerEvent, registerMessage return type | PR (see GitHub) |
## t25: fixes to t24 side effects (log t0-4637014005f8)

- bdlib regression: `RecipeReloadListener$` (module class) and the companion `RecipeReloadListener` are both `@Mod.EventBusSubscriber`; only the companion has static `@SubscribeEvent` forwarders (javap-verified). NeoForge `register(Class)` throws for a class without handlers, Forge registered nothing. `LegacyInstanceSubscriberStep` now also removes the class-level annotation when no static `@SubscribeEvent` method remains (tested against a real `IEventBus`).
- Mantle `RegistryLoadable(Registry)` does `BuiltInRegistries.REGISTRY.getKey(registry)` + `requireNonNull`; the enchantment view is not in the root registry. `f_257047_` is redirected to `compat/LegacyRegistryRoot`, a forwarding view of the root registry whose only addition is `getKey(enchantment view) = minecraft:enchantment`. Nothing is registered. Other registries are unaffected by the answer.
- `net.minecraftforge.registries.NewRegistryEvent` -> NeoForge `NewRegistryEvent` (owner exists; verified). Legacy `create(RegistryBuilder)` returned `Supplier<IForgeRegistry>`, so no member is whitelisted: any call fails per call. cofh only declares it as a handler parameter (`CoFHCore.registrySetup`, which calls nothing on the event).
- advgenerators `ClassFormatError: Class is both outer and inner class` (`Icons`): `TextureStitchEvent$Post` -> top-level `TextureAtlasStitchedEvent` left an InnerClasses entry with inner == outer. `ForgeOwnerRemapStep.dropBrokenInnerClasses` drops entries whose remapped triple is not `outer$simple` (tested by defining the class in a loader). A full define-scan of all transformed jars was not added.
- Validation: `.\gradlew.bat test build` passes. Not verified in game.
| 2026-10-17 | t24: enchantment registry view, ForgeMod, instance subscribers, TextureStitchEvent | PR (see GitHub) |
| 2026-10-17 | t25: subscriber class annotation, root registry view, NewRegistryEvent, InnerClasses fix | PR (see GitHub) |
| 2026-10-17 | t26: retyped game field repair, PlantType | PR (see GitHub) |

## t27: sapling growth and legacy spawn message linkage

- `SaplingGrowTreeEvent` maps to the actual NeoForge `BlockGrowFeatureEvent`, so
  transformed event subscribers receive real growth events. Shared constructor,
  level/position/feature access and both feature setters are allowlisted.
  Ordinary calls to `getRandomSource` redirect to `getRandom`; result access
  redirects through `LegacySaplingGrowth`. `DENY` cancels growth; `ALLOW` and
  `DEFAULT` clear cancellation. Explicit `ALLOW` is retained in a weak-key map;
  a native listener's cancellation is exposed as `DENY`. The result enum maps
  to `LegacyEventResult`; this does not adapt result semantics for other events.
- `PlayMessages$SpawnEntity` maps to `LegacySpawnEntity`, a receive-side data
  adapter with Forge's getters and decoder. Rotation bytes and velocity shorts
  retain their original units. Invalid payload lengths are rejected; decoded
  additional-data buffers belong to the caller and must be released.
- Scope: this fixes these missing type references and the listed calls. It does
  **not** register Forge's spawn transport, implement `NetworkHooks`, or port
  `EntityType.Builder.setCustomClientFactory`. Spawn `encode`/`handle` remain
  unsupported and use the existing fail-at-call policy. Unsupported method
  handles/constructors and mixins retain the existing conservative behavior.
  Sapling calls through the base `Event` owner are not adapted.
- All three adapters are included in `compat-classes.txt`, so they are relocated
  into the game layer. Transformer cache version is 27 to invalidate old output.
- Runtime tests were left to the user as requested.

| Date | Change |
| --- | --- |
| 2026-10-17 | t27: sapling event/result adapter and spawn-message type/data mapping; [PR #40](https://github.com/Minionade/forge-stuck/pull/40). |

- Local `assemble -x test` could not reach Java compilation: NeoForm runtime failed
  in `ExternalJavaToolAction.run` during `mergeMappings` with `NoSuchElementException`
  (the sandbox does not expose the Java process executable path). No compiled JAR
  or passing build is claimed for this patch.

## t28: LivingAttackEvent and AbstractTreeGrower translation

- `LivingAttackEvent` maps to NeoForge `LivingIncomingDamageEvent`: entity/source
  access and cancellation operate on the real damage event. `getAmount` reads
  original damage, not another listener's modified amount. `isCancelable` is
  true and `hasResult` false. The old `(LivingEntity,DamageSource,float)`
  constructor is adapted by constructing a `DamageContainer` before invoking
  the new constructor. Timing differs: NeoForge's event fires after initial
  invulnerability checks; this does not reproduce Forge callbacks for attacks
  rejected before that point. Calls through the base `Event` owner and method
  handles to redirected helpers are outside this adaptation.
- `AbstractTreeGrower` maps to `LegacyAbstractTreeGrower`, preserving the no-arg
  constructor and named/SRG virtual feature-selection and growth callbacks.
  The constructor step renames these two exact SRG signatures consistently in
  declarations, calls and handles, including indirect subclasses and super calls.
  SRG signatures `m_213888_` and `m_213817_` were checked against MCP
  1.20.1-20230612.114412 and Mojang's 1.20.1 mappings. Single-sapling growth
  checks nearby flowers, resolves the configured feature, fires the real
  NeoForge growth event, places the feature, and restores the sapling on failure.
- `LegacyAttackAndTreeStep` runs before owner remapping. Classes calling the
  removed `SaplingBlock(AbstractTreeGrower, Properties)` constructor get a
  `LegacySaplingBlock` construction path; direct subclasses use it as their
  superclass. SaplingBlock descriptors/casts elsewhere remain vanilla, and
  native NeoForge jars remain outside transformation. Legacy saplings keep
  their original grower callbacks in `advanceTree`; no access transformer opens
  the final native `TreeGrower` class. The adapter's native-grower constructor
  preserves mixed constructor use in an affected class.
- Limits: `AbstractMegaTreeGrower` and removed concrete vanilla grower classes
  are not covered. Encoding a legacy sapling using the inherited block codec
  explicitly fails rather than silently encoding the placeholder native grower.
  A mod-provided overriding codec is unchanged. Mixins are not structurally adapted.
- All three adapters are listed in `compat-classes.txt` for game-layer relocation.
  Cache version 28 invalidates prior transformed jars. Compilation and tests
  were not run, as requested by the user.

| Date | Change |
| --- | --- |
| 2026-10-17 | t28: incoming attack event and legacy single-sapling grower adapters; PR link to follow. |
