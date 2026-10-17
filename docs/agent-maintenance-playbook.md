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
