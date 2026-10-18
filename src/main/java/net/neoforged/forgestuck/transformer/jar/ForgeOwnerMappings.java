package net.neoforged.forgestuck.transformer.jar;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/**
 * Table of legacy Forge class internal names -> NeoForge class internal names. Only exact internal names are ever
 * rewritten. An entry may list the members ({@code name + descriptor}) that are known to exist with the same
 * signature on the target class; references to any other member cannot be adapted safely and leave the owner
 * unmapped in that class (see {@link ForgeOwnerRemapStep}).
 */
public final class ForgeOwnerMappings {
    /** @param supportedMembers {@code name+desc} entries valid on the target, or {@code null} when every member is valid. */
    public record Entry(String oldOwner, String newOwner, Set<String> supportedMembers) {
        boolean supports(String name, String desc) {
            return supportedMembers == null || supportedMembers.contains(name + desc);
        }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public ForgeOwnerMappings() {}

    public ForgeOwnerMappings(Iterable<Entry> initial) {
        for (Entry e : initial) add(e);
    }

    public ForgeOwnerMappings add(Entry entry) {
        entries.put(entry.oldOwner(), entry);
        return this;
    }

    public ForgeOwnerMappings add(String oldOwner, String newOwner) {
        return add(new Entry(oldOwner, newOwner, null));
    }

    public ForgeOwnerMappings addAll(Iterable<Entry> more) {
        for (Entry e : more) add(e);
        return this;
    }

    public Entry get(String owner) {
        return entries.get(owner);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    private static final String FU = "net/minecraftforge/common/util/";
    private static final String COMPAT = "net/neoforged/forgestuck/compat/";

    /**
     * NeoForge 1.21.1 ships no equivalent of Forge's LazyOptional/NonNull* types, so they map to the stand-ins in
     * {@code net.neoforged.forgestuck.compat}. Supported members are listed with their legacy (Forge) descriptors.
     */
    public static ForgeOwnerMappings defaults() {
        String lo = "L" + FU + "LazyOptional;";
        String nnc = "L" + FU + "NonNullConsumer;";
        String nnf = "L" + FU + "NonNullFunction;";
        String nns = "L" + FU + "NonNullSupplier;";
        String dr = "L" + R + "DeferredRegister;";
        String ro = "L" + R + "RegistryObject;";
        return new ForgeOwnerMappings()
                .add(new Entry("net/minecraft/world/item/ArmorMaterial",
                        COMPAT + "LegacyArmorMaterial", armorMaterialMembers()))
                .add(new Entry("net/minecraftforge/client/model/data/ModelData",
                        "net/neoforged/neoforge/client/model/data/ModelData", Set.of(
                                "EMPTYLnet/minecraftforge/client/model/data/ModelData;",
                                "builder()Lnet/minecraftforge/client/model/data/ModelData$Builder;",
                                "derive()Lnet/minecraftforge/client/model/data/ModelData$Builder;",
                                "getProperties()Ljava/util/Set;",
                                "has(Lnet/minecraftforge/client/model/data/ModelProperty;)Z",
                                "get(Lnet/minecraftforge/client/model/data/ModelProperty;)Ljava/lang/Object;")))
                .add(new Entry("net/minecraftforge/client/model/data/ModelData$Builder",
                        "net/neoforged/neoforge/client/model/data/ModelData$Builder", Set.of(
                                "with(Lnet/minecraftforge/client/model/data/ModelProperty;Ljava/lang/Object;)"
                                        + "Lnet/minecraftforge/client/model/data/ModelData$Builder;",
                                "build()Lnet/minecraftforge/client/model/data/ModelData;")))
                .add(new Entry("net/minecraftforge/client/model/data/ModelProperty",
                        "net/neoforged/neoforge/client/model/data/ModelProperty", Set.of(
                                "<init>()V", "<init>(Ljava/util/function/Predicate;)V",
                                "test(Ljava/lang/Object;)Z")))
                .add(new Entry("net/minecraftforge/common/extensions/IForgeItem",
                        "net/neoforged/neoforge/common/extensions/IItemExtension", Set.of(
                                "isRepairable(Lnet/minecraft/world/item/ItemStack;)Z",
                                "getXpRepairRatio(Lnet/minecraft/world/item/ItemStack;)F",
                                "getMaxStackSize(Lnet/minecraft/world/item/ItemStack;)I",
                                "getDamage(Lnet/minecraft/world/item/ItemStack;)I",
                                "getMaxDamage(Lnet/minecraft/world/item/ItemStack;)I",
                                "isDamaged(Lnet/minecraft/world/item/ItemStack;)Z",
                                "setDamage(Lnet/minecraft/world/item/ItemStack;I)V")))
                .add(new Entry("net/minecraft/world/item/DyeableLeatherItem",
                        COMPAT + "LegacyDyeableLeatherItem", Set.of(
                                "TAG_COLORLjava/lang/String;",
                                "TAG_DISPLAYLjava/lang/String;",
                                "DEFAULT_LEATHER_COLORI",
                                "hasCustomColor(Lnet/minecraft/world/item/ItemStack;)Z",
                                "getColor(Lnet/minecraft/world/item/ItemStack;)I",
                                "clearColor(Lnet/minecraft/world/item/ItemStack;)V",
                                "setColor(Lnet/minecraft/world/item/ItemStack;I)V")))
                .add(new Entry("net/minecraftforge/common/extensions/IForgeBlock",
                        "net/neoforged/neoforge/common/extensions/IBlockExtension", Set.of(
                                "getFriction(Lnet/minecraft/world/level/block/state/BlockState;"
                                        + "Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;"
                                        + "Lnet/minecraft/world/entity/Entity;)F",
                                "getLightEmission(Lnet/minecraft/world/level/block/state/BlockState;"
                                        + "Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)I",
                                "isLadder(Lnet/minecraft/world/level/block/state/BlockState;"
                                        + "Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;"
                                        + "Lnet/minecraft/world/entity/LivingEntity;)Z",
                                "isBurning(Lnet/minecraft/world/level/block/state/BlockState;"
                                        + "Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Z",
                                "canHarvestBlock(Lnet/minecraft/world/level/block/state/BlockState;"
                                        + "Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;"
                                        + "Lnet/minecraft/world/entity/player/Player;)Z",
                                "setBedOccupied(Lnet/minecraft/world/level/block/state/BlockState;"
                                        + "Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                                        + "Lnet/minecraft/world/entity/LivingEntity;Z)V",
                                "getBedDirection(Lnet/minecraft/world/level/block/state/BlockState;"
                                        + "Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)"
                                        + "Lnet/minecraft/core/Direction;",
                                "getExplosionResistance(Lnet/minecraft/world/level/block/state/BlockState;"
                                        + "Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;"
                                        + "Lnet/minecraft/world/level/Explosion;)F",
                                "getCloneItemStack(Lnet/minecraft/world/level/block/state/BlockState;"
                                        + "Lnet/minecraft/world/phys/HitResult;Lnet/minecraft/world/level/LevelReader;"
                                        + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)"
                                        + "Lnet/minecraft/world/item/ItemStack;",
                                "addLandingEffects(Lnet/minecraft/world/level/block/state/BlockState;"
                                        + "Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;"
                                        + "Lnet/minecraft/world/level/block/state/BlockState;"
                                        + "Lnet/minecraft/world/entity/LivingEntity;I)Z",
                                "addRunningEffects(Lnet/minecraft/world/level/block/state/BlockState;"
                                        + "Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                                        + "Lnet/minecraft/world/entity/Entity;)Z",
                                "isFertile(Lnet/minecraft/world/level/block/state/BlockState;"
                                        + "Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Z",
                                "isConduitFrame(Lnet/minecraft/world/level/block/state/BlockState;"
                                        + "Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;"
                                        + "Lnet/minecraft/core/BlockPos;)Z",
                                "isPortalFrame(Lnet/minecraft/world/level/block/state/BlockState;"
                                        + "Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Z",
                                "rotate(Lnet/minecraft/world/level/block/state/BlockState;"
                                        + "Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;"
                                        + "Lnet/minecraft/world/level/block/Rotation;)"
                                        + "Lnet/minecraft/world/level/block/state/BlockState;",
                                "getEnchantPowerBonus(Lnet/minecraft/world/level/block/state/BlockState;"
                                        + "Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)F",
                                "onNeighborChange(Lnet/minecraft/world/level/block/state/BlockState;"
                                        + "Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;"
                                        + "Lnet/minecraft/core/BlockPos;)V",
                                "shouldCheckWeakPower(Lnet/minecraft/world/level/block/state/BlockState;"
                                        + "Lnet/minecraft/world/level/SignalGetter;Lnet/minecraft/core/BlockPos;"
                                        + "Lnet/minecraft/core/Direction;)Z",
                                "getWeakChanges(Lnet/minecraft/world/level/block/state/BlockState;"
                                        + "Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z",
                                "isSlimeBlock(Lnet/minecraft/world/level/block/state/BlockState;)Z",
                                "isStickyBlock(Lnet/minecraft/world/level/block/state/BlockState;)Z",
                                "canStickTo(Lnet/minecraft/world/level/block/state/BlockState;"
                                        + "Lnet/minecraft/world/level/block/state/BlockState;)Z",
                                "isFlammable(Lnet/minecraft/world/level/block/state/BlockState;"
                                        + "Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;"
                                        + "Lnet/minecraft/core/Direction;)Z",
                                "getFireSpreadSpeed(Lnet/minecraft/world/level/block/state/BlockState;"
                                        + "Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;"
                                        + "Lnet/minecraft/core/Direction;)I",
                                "isFireSource(Lnet/minecraft/world/level/block/state/BlockState;"
                                        + "Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;"
                                        + "Lnet/minecraft/core/Direction;)Z",
                                "canEntityDestroy(Lnet/minecraft/world/level/block/state/BlockState;"
                                        + "Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;"
                                        + "Lnet/minecraft/world/entity/Entity;)Z",
                                "canDropFromExplosion(Lnet/minecraft/world/level/block/state/BlockState;"
                                        + "Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;"
                                        + "Lnet/minecraft/world/level/Explosion;)Z",
                                "onBlockExploded(Lnet/minecraft/world/level/block/state/BlockState;"
                                        + "Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                                        + "Lnet/minecraft/world/level/Explosion;)V")))
                .add(new Entry("net/minecraftforge/items/IItemHandler",
                        "net/neoforged/neoforge/items/IItemHandler", Set.of(
                                "getSlots()I",
                                "getStackInSlot(I)Lnet/minecraft/world/item/ItemStack;",
                                "insertItem(ILnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;",
                                "extractItem(IIZ)Lnet/minecraft/world/item/ItemStack;",
                                "getSlotLimit(I)I",
                                "isItemValid(ILnet/minecraft/world/item/ItemStack;)Z")))
                .add(new Entry("net/minecraftforge/items/IItemHandlerModifiable",
                        "net/neoforged/neoforge/items/IItemHandlerModifiable",
                        Set.of("setStackInSlot(ILnet/minecraft/world/item/ItemStack;)V")))
                .add(new Entry("net/minecraftforge/items/SlotItemHandler",
                        "net/neoforged/neoforge/items/SlotItemHandler", Set.of(
                                "<init>(Lnet/minecraftforge/items/IItemHandler;III)V",
                                "getItemHandler()Lnet/minecraftforge/items/IItemHandler;",
                                "initialize(Lnet/minecraft/world/item/ItemStack;)V")))
                .add(new Entry("net/minecraftforge/client/event/EntityRenderersEvent$AddLayers",
                        "net/neoforged/neoforge/client/event/EntityRenderersEvent$AddLayers", Set.of(
                                "getEntityTypes()Ljava/util/Set;",
                                "getRenderer(Lnet/minecraft/world/entity/EntityType;)"
                                        + "Lnet/minecraft/client/renderer/entity/EntityRenderer;",
                                "getEntityModels()Lnet/minecraft/client/model/geom/EntityModelSet;",
                                "getContext()Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;")))
                .add(new Entry("net/minecraftforge/common/util/INBTSerializable",
                        COMPAT + "LegacyINBTSerializable", Set.of(
                                "serializeNBT()Lnet/minecraft/nbt/Tag;",
                                "serializeNBT(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/Tag;",
                                "deserializeNBT(Lnet/minecraft/nbt/Tag;)V",
                                "deserializeNBT(Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/nbt/Tag;)V")))
                .add(new Entry("net/minecraftforge/energy/IEnergyStorage",
                        "net/neoforged/neoforge/energy/IEnergyStorage", Set.of(
                                "receiveEnergy(IZ)I",
                                "extractEnergy(IZ)I",
                                "getEnergyStored()I",
                                "getMaxEnergyStored()I",
                                "canExtract()Z",
                                "canReceive()Z")))
                .add(new Entry(FU + "LazyOptional", COMPAT + "LazyOptional", Set.of(
                        "of(" + nns + ")" + lo,
                        "empty()" + lo,
                        "isPresent()Z",
                        "ifPresent(" + nnc + ")V",
                        "resolve()Ljava/util/Optional;",
                        "orElse(Ljava/lang/Object;)Ljava/lang/Object;",
                        "orElseGet(" + nns + ")Ljava/lang/Object;",
                        "orElseThrow(" + nns + ")Ljava/lang/Object;",
                        "map(" + nnf + ")Ljava/util/Optional;",
                        "filter(L" + FU + "NonNullPredicate;)Ljava/util/Optional;",
                        "cast()" + lo,
                        "lazyMap(" + nnf + ")" + lo,
                        "addListener(" + nnc + ")V",
                        "removeListener(" + nnc + ")V",
                        "invalidate()V")))
                .add(new Entry("net/minecraft/world/level/storage/loot/Serializer", COMPAT + "LegacyLootSerializer", Set.of(
                        "serialize(Lcom/google/gson/JsonObject;Ljava/lang/Object;Lcom/google/gson/JsonSerializationContext;)V",
                        "deserialize(Lcom/google/gson/JsonObject;Lcom/google/gson/JsonDeserializationContext;)Ljava/lang/Object;")))
                .add(new Entry(FU + "NonNullConsumer", COMPAT + "NonNullConsumer",
                        Set.of("accept(Ljava/lang/Object;)V")))
                .add(new Entry(FU + "NonNullPredicate", COMPAT + "NonNullPredicate",
                        Set.of("test(Ljava/lang/Object;)Z")))
                .add(new Entry(FU + "NonNullFunction", COMPAT + "NonNullFunction",
                        Set.of("apply(Ljava/lang/Object;)Ljava/lang/Object;")))
                .add(new Entry(FU + "NonNullSupplier", COMPAT + "NonNullSupplier",
                        Set.of("get()Ljava/lang/Object;")))
                // Forge's capability API (Capability, CapabilityManager, ICapabilityProvider, ...) is intentionally NOT
                // mapped: NeoForge's BlockCapability/Capabilities/ICapabilityProvider<O,C,T> have different
                // semantics, and CapabilityToken does not exist. Classes using them stay unmapped and are reported.
                .add(registryObject())
                .add(entry(R + "IForgeRegistry", COMPAT + "IForgeRegistry", registryMembers()))
                .add(entry(R + "ForgeRegistries", COMPAT + "ForgeRegistries", forgeRegistriesFields(false)))
                .add(entry(R + "ForgeRegistries$Keys", COMPAT + "ForgeRegistries$Keys", forgeRegistriesFields(true)))
                .add(entry(R + "DeferredRegister", COMPAT + "LegacyDeferredRegister", Set.of(
                        "create(Lnet/minecraftforge/registries/IForgeRegistry;Ljava/lang/String;)" + dr,
                        "create(Lnet/minecraft/resources/ResourceKey;Ljava/lang/String;)" + dr,
                        "create(Lnet/minecraft/resources/ResourceLocation;Ljava/lang/String;)" + dr,
                        "register(Ljava/lang/String;Ljava/util/function/Supplier;)" + ro,
                        "register(Lnet/minecraftforge/eventbus/api/IEventBus;)V",
                        "getRegistryKey()Lnet/minecraft/resources/ResourceKey;",
                        "getRegistryName()Lnet/minecraft/resources/ResourceLocation;",
                        "getEntries()Ljava/util/Collection;")))
                .add(entry(R + "RegisterEvent", "net/neoforged/neoforge/registries/RegisterEvent", Set.of(
                        "register(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/resources/ResourceLocation;"
                                + "Ljava/util/function/Supplier;)V",
                        "register(Lnet/minecraft/resources/ResourceKey;Ljava/util/function/Consumer;)V",
                        "getRegistryKey()Lnet/minecraft/resources/ResourceKey;")))
                // Legacy create(RegistryBuilder) returned Supplier<IForgeRegistry>; NeoForge returns Registry, so only the type links.
                .add(entry(R + "NewRegistryEvent", "net/neoforged/neoforge/registries/NewRegistryEvent", Set.of()))
                .add(entry(R + "RegisterEvent$RegisterHelper", "net/neoforged/neoforge/registries/RegisterEvent$RegisterHelper",
                        Set.of("register(Lnet/minecraft/resources/ResourceLocation;Ljava/lang/Object;)V",
                                "register(Lnet/minecraft/resources/ResourceKey;Ljava/lang/Object;)V")))
                // Entry-point and event-bus plumbing: FML only scans for net.neoforged.fml.common.Mod.
                .add("net/minecraftforge/fml/common/Mod", "net/neoforged/fml/common/Mod")
                .add("net/minecraftforge/fml/common/Mod$EventBusSubscriber", EBS)
                .add("net/minecraftforge/fml/common/Mod$EventBusSubscriber$Bus", EBS + "$Bus")
                .add(entry(EB + "IEventBus", "net/neoforged/bus/api/IEventBus", Set.of(
                        "register(Ljava/lang/Object;)V",
                        "unregister(Ljava/lang/Object;)V",
                        "addListener(Ljava/util/function/Consumer;)V",
                        "addListener(L" + EB + "EventPriority;Ljava/util/function/Consumer;)V",
                        "addListener(L" + EB + "EventPriority;ZLjava/util/function/Consumer;)V",
                        "addListener(Ljava/lang/Class;Ljava/util/function/Consumer;)V",
                        "addListener(L" + EB + "EventPriority;Ljava/lang/Class;Ljava/util/function/Consumer;)V",
                        "addListener(L" + EB + "EventPriority;ZLjava/lang/Class;Ljava/util/function/Consumer;)V")))
                .add(EB + "SubscribeEvent", "net/neoforged/bus/api/SubscribeEvent")
                .add(EB + "EventPriority", "net/neoforged/bus/api/EventPriority")
                .add(entry("net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext", COMPAT + "FMLJavaModLoadingContext",
                        Set.of("get()Lnet/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext;",
                                "getModEventBus()L" + EB + "IEventBus;")))
                .add(entry("net/minecraftforge/fml/config/IConfigSpec", "net/neoforged/fml/config/IConfigSpec", Set.of()))
                .add("net/minecraftforge/api/distmarker/Dist", "net/neoforged/api/distmarker/Dist")
                .add("net/minecraftforge/api/distmarker/OnlyIn", "net/neoforged/api/distmarker/OnlyIn")
                .addAll(configAndEnvironment())
                .addAll(ConditionMappings.entries())
                .addAll(ItemAbilityAndFluidMappings.entries())
                .addAll(NetworkMappings.entries())
                .addAll(TickMappings.entries())
                .addAll(GeneratedOwnerTable.entries())
                .addAll(GrowthAndSpawnMappings.entries())
                .addAll(AttackAndTreeMappings.entries())
                .addAll(HurtAndShearingMappings.entries())
                .addAll(SpawnEggMappings.entries())
                .addAll(LivingRenderAndSpawnMappings.entries())
                .addAll(SpawnPlacementMappings.entries())
                .add(entry(FE + "FMLCommonSetupEvent", "net/neoforged/fml/event/lifecycle/FMLCommonSetupEvent", enqueue()))
                .add(entry(FE + "FMLClientSetupEvent", "net/neoforged/fml/event/lifecycle/FMLClientSetupEvent", enqueue()))
                .add(entry(FE + "FMLDedicatedServerSetupEvent",
                        "net/neoforged/fml/event/lifecycle/FMLDedicatedServerSetupEvent", enqueue()));
    }

    private static final String R = "net/minecraftforge/registries/";
    private static final String EB = "net/minecraftforge/eventbus/api/";
    private static final String FE = "net/minecraftforge/fml/event/lifecycle/";
    private static final String EBS = "net/neoforged/fml/common/EventBusSubscriber";

    private static Set<String> armorMaterialMembers() {
        Set<String> members = new java.util.HashSet<>();
        // Both development names and production SRG calls link after SrgMemberRemapStep adds override bridges.
        for (String member : java.util.List.of(
                "m_266425_(Lnet/minecraft/world/item/ArmorItem$Type;)I",
                "m_7366_(Lnet/minecraft/world/item/ArmorItem$Type;)I",
                "m_6646_()I", "m_7344_()Lnet/minecraft/sounds/SoundEvent;",
                "m_6230_()Lnet/minecraft/world/item/crafting/Ingredient;",
                "m_6082_()Ljava/lang/String;", "m_6651_()F", "m_6649_()F")) {
            int paren = member.indexOf('(');
            members.add(member);
            members.add(LegacyNameMappings.defaults().map(member.substring(0, paren))
                    + member.substring(paren));
        }
        return Set.copyOf(members);
    }

    /** Registry fields present on the compat {@code ForgeRegistries}; absent fields block the owner in that class. */
    static final java.util.List<String> FORGE_REGISTRY_FIELDS = java.util.List.of("BLOCKS", "FLUIDS", "ITEMS",
            "MOB_EFFECTS", "POTIONS", "ATTRIBUTES", "STAT_TYPES", "SOUND_EVENTS", "PARTICLE_TYPES", "BLOCK_ENTITY_TYPES",
            "ENTITY_TYPES", "MENU_TYPES", "RECIPE_TYPES", "RECIPE_SERIALIZERS", "VILLAGER_PROFESSIONS", "POI_TYPES",
            "FEATURES", "ENTITY_DATA_SERIALIZERS", "ENCHANTMENTS");

    private static Iterable<Entry> configAndEnvironment() {
        return ConfigEnvironmentMappings.entries();
    }

    private static Entry entry(String oldOwner, String newOwner, Set<String> members) {
        return new Entry(oldOwner, newOwner, members);
    }

    private static Set<String> enqueue() {
        return Set.of("enqueueWork(Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;",
                "enqueueWork(Ljava/util/function/Supplier;)Ljava/util/concurrent/CompletableFuture;");
    }

    /** Forge Keys constants whose NeoForgeRegistries.Keys counterpart exists (reflection-verified, 21.1.262). */
    static final java.util.List<String> NEOFORGE_KEY_FIELDS = java.util.List.of("GLOBAL_LOOT_MODIFIER_SERIALIZERS",
            "BIOME_MODIFIER_SERIALIZERS", "STRUCTURE_MODIFIER_SERIALIZERS", "FLUID_TYPES", "HOLDER_SET_TYPES",
            "BIOME_MODIFIERS", "STRUCTURE_MODIFIERS");

    private static Set<String> forgeRegistriesFields(boolean keys) {
        String desc = keys ? "Lnet/minecraft/resources/ResourceKey;" : "L" + R + "IForgeRegistry;";
        Set<String> out = new java.util.TreeSet<>();
        for (String f : FORGE_REGISTRY_FIELDS) out.add(f + desc);
        if (keys) for (String f : NEOFORGE_KEY_FIELDS) out.add(f + desc);
        return out;
    }

    private static Set<String> registryMembers() {
        return Set.of("getRegistryKey()Lnet/minecraft/resources/ResourceKey;",
                "getRegistryName()Lnet/minecraft/resources/ResourceLocation;",
                "containsKey(Lnet/minecraft/resources/ResourceLocation;)Z",
                "containsValue(Ljava/lang/Object;)Z",
                "getValue(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/Object;",
                "getKey(Ljava/lang/Object;)Lnet/minecraft/resources/ResourceLocation;",
                "getResourceKey(Ljava/lang/Object;)Ljava/util/Optional;",
                "getKeys()Ljava/util/Set;",
                "getValues()Ljava/util/Collection;",
                "getEntries()Ljava/util/Set;");
    }

    private static Entry registryObject() {
        String ro = "L" + R + "RegistryObject;";
        return entry(R + "RegistryObject", COMPAT + "RegistryObject", Set.of(
                "get()Ljava/lang/Object;",
                "getId()Lnet/minecraft/resources/ResourceLocation;",
                "getKey()Lnet/minecraft/resources/ResourceKey;",
                "isPresent()Z",
                "ifPresent(Ljava/util/function/Consumer;)V",
                "stream()Ljava/util/stream/Stream;",
                "filter(Ljava/util/function/Predicate;)" + ro,
                "orElse(Ljava/lang/Object;)Ljava/lang/Object;",
                "orElseGet(Ljava/util/function/Supplier;)Ljava/lang/Object;",
                "orElseThrow(Ljava/util/function/Supplier;)Ljava/lang/Object;",
                "getHolder()Ljava/util/Optional;",
                "create(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceKey;Ljava/lang/String;)" + ro,
                "create(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;Ljava/lang/String;)" + ro,
                "create(Lnet/minecraft/resources/ResourceLocation;L" + R + "IForgeRegistry;)" + ro));
    }

    /** Cheap check on raw class bytes: could this class mention any mapped owner? */
    boolean mayReference(byte[] bytes) {
        String raw = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        for (String owner : entries.keySet()) if (raw.contains(owner)) return true;
        return false;
    }

    /** Replaces exact {@code L<old>;} tokens (descriptors in Mixin target strings); counts hits per owner. */
    String remapDescriptorTokens(String s, Set<String> blocked, Map<String, Integer> counts) {
        String result = s;
        for (Entry e : entries.values()) {
            if (blocked.contains(e.oldOwner())) continue;
            String from = "L" + e.oldOwner() + ";";
            if (!result.contains(from)) continue;
            int hits = result.split(java.util.regex.Pattern.quote(from), -1).length - 1;
            result = result.replace(from, "L" + e.newOwner() + ";");
            counts.merge(e.oldOwner(), hits, Integer::sum);
        }
        return result;
    }

    /** Owners from this table that the class still references (used to detect incomplete remaps). */
    public java.util.Set<String> remainingOwners(byte[] bytes) {
        java.util.Set<String> found = new java.util.TreeSet<>();
        if (!mayReference(bytes)) return found;
        Remapper recorder = new Remapper() {
            @Override
            public String map(String internalName) {
                if (entries.containsKey(internalName)) found.add(internalName);
                return internalName;
            }
        };
        new ClassReader(bytes).accept(new ClassRemapper(new ClassVisitor(Opcodes.ASM9) {}, recorder), 0);
        return found;
    }

    /** Remapper rewriting mapped owners (except {@code blocked}) and counting rewrites per old owner. */
    Remapper remapper(Set<String> blocked, TreeMap<String, Integer> counts) {
        return new Remapper() {
            @Override
            public String map(String internalName) {
                Entry e = entries.get(internalName);
                if (e == null || blocked.contains(internalName)) return internalName;
                counts.merge(internalName, 1, Integer::sum);
                return e.newOwner();
            }
        };
    }
}
