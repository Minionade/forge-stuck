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
    private final Map<FieldKey, String> fieldAliases = new java.util.HashMap<>();

    private record FieldKey(String owner, String name, String descriptor) {}

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

    ForgeOwnerMappings addFieldAlias(String owner, String oldName, String descriptor, String newName) {
        fieldAliases.put(new FieldKey(owner, oldName, descriptor), newName);
        return this;
    }

    public Entry get(String owner) {
        return entries.get(owner);
    }

    boolean supportsMember(String owner, String name, String descriptor) {
        Entry entry = entries.get(owner);
        return entry != null && (entry.supports(name, descriptor)
                || fieldAliases.containsKey(new FieldKey(owner, name, descriptor)));
    }

    String mappedFieldName(String owner, String name, String descriptor) {
        return fieldAliases.getOrDefault(new FieldKey(owner, name, descriptor), name);
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
        ForgeOwnerMappings mappings = new ForgeOwnerMappings()
                .add("net/minecraft/world/entity/MobType", COMPAT + "LegacyMobType")
                .add(new Entry("net/minecraftforge/eventbus/api/IGenericEvent",
                        COMPAT + "LegacyIGenericEvent", Set.of(
                                "getGenericType()Ljava/lang/reflect/Type;")))
                .add(new Entry("net/minecraftforge/eventbus/api/GenericEvent",
                        COMPAT + "LegacyGenericEvent", Set.of(
                                "<init>()V",
                                "<init>(Ljava/lang/Class;)V",
                                "getGenericType()Ljava/lang/reflect/Type;")))
                .add(new Entry("net/minecraftforge/fml/ModLoader", COMPAT + "LegacyModLoader", Set.of(
                        "get()Lnet/minecraftforge/fml/ModLoader;",
                        "postEvent(Lnet/minecraftforge/eventbus/api/Event;)V",
                        "runEventGenerator(Ljava/util/function/Function;)V")))
                .add(new Entry("net/minecraftforge/event/AttachCapabilitiesEvent",
                        COMPAT + "LegacyAttachCapabilitiesEvent", Set.of(
                                "<init>(Ljava/lang/Class;Ljava/lang/Object;)V",
                                "getObject()Ljava/lang/Object;",
                                "addCapability(Lnet/minecraft/resources/ResourceLocation;"
                                        + "Lnet/minecraftforge/common/capabilities/ICapabilityProvider;)V",
                                "getCapabilities()Ljava/util/Map;",
                                "addListener(Ljava/lang/Runnable;)V",
                                "getListeners()Ljava/util/List;")))
                .add(new Entry("net/minecraftforge/common/SoundAction",
                        "net/neoforged/neoforge/common/SoundAction", Set.of(
                                "get(Ljava/lang/String;)Lnet/minecraftforge/common/SoundAction;",
                                "name()Ljava/lang/String;",
                                "toString()Ljava/lang/String;")))
                .add(new Entry("net/minecraftforge/common/SoundActions",
                        "net/neoforged/neoforge/common/SoundActions", Set.of(
                                "BUCKET_FILLLnet/minecraftforge/common/SoundAction;",
                                "BUCKET_EMPTYLnet/minecraftforge/common/SoundAction;",
                                "FLUID_VAPORIZELnet/minecraftforge/common/SoundAction;")))
                .add(new Entry("net/minecraftforge/common/Tags$Items",
                        "net/neoforged/neoforge/common/Tags$Items", tagFields(
                                "ARMORS", "BONES", "CHESTS_WOODEN", "CROPS_BEETROOT", "CROPS_CARROT",
                                "CROPS_POTATO", "CROPS_WHEAT", "CROPS", "DUSTS_GLOWSTONE", "DUSTS_REDSTONE",
                                "DUSTS", "DYES_BLACK", "DYES_BLUE", "DYES_BROWN", "DYES_CYAN", "DYES_GRAY",
                                "DYES_GREEN", "DYES_LIGHT_BLUE", "DYES_LIGHT_GRAY", "DYES_LIME", "DYES_MAGENTA",
                                "DYES_ORANGE", "DYES_PINK", "DYES_PURPLE", "DYES_RED", "DYES_YELLOW", "DYES",
                                "END_STONES", "ENDER_PEARLS", "FEATHERS", "FENCE_GATES_WOODEN", "FENCES_WOODEN",
                                "GEMS_AMETHYST", "GEMS_DIAMOND", "GEMS_EMERALD", "GEMS_LAPIS", "GEMS_PRISMARINE",
                                "GEMS_QUARTZ", "GEMS", "GLASS_PANES_COLORLESS", "GLASS_PANES", "INGOTS_COPPER",
                                "INGOTS_GOLD", "INGOTS_IRON", "INGOTS_NETHERITE", "INGOTS", "MUSHROOMS",
                                "NUGGETS_GOLD", "NUGGETS_IRON", "NUGGETS", "ORE_RATES_DENSE", "ORE_RATES_SINGULAR",
                                "ORE_RATES_SPARSE", "ORES_DIAMOND", "ORES_EMERALD", "ORES_IN_GROUND_NETHERRACK",
                                "ORES_NETHERITE_SCRAP", "ORES", "RAW_MATERIALS", "RODS_BLAZE", "RODS_WOODEN",
                                "RODS", "SEEDS", "SLIMEBALLS", "STORAGE_BLOCKS_COAL", "STORAGE_BLOCKS_COPPER",
                                "STORAGE_BLOCKS_GOLD", "STORAGE_BLOCKS_IRON", "STORAGE_BLOCKS_LAPIS",
                                "STORAGE_BLOCKS_REDSTONE", "STORAGE_BLOCKS")))
                .add(new Entry("net/minecraftforge/client/ForgeRenderTypes",
                        "net/neoforged/neoforge/client/NeoForgeRenderTypes", Set.of(
                                "ITEM_LAYERED_CUTOUTLnet/minecraftforge/client/ForgeRenderTypes;",
                                "ITEM_UNSORTED_TRANSLUCENTLnet/minecraftforge/client/ForgeRenderTypes;",
                                "get()Lnet/minecraft/client/renderer/RenderType;")))
                .add(new Entry("net/minecraftforge/client/model/ForgeFaceData",
                        "net/neoforged/neoforge/client/model/ExtraFaceData", Set.of("blockLight()I")))
                .add("net/minecraftforge/client/model/IDynamicBakedModel",
                        "net/neoforged/neoforge/client/model/IDynamicBakedModel")
                .add(new Entry("net/minecraftforge/common/ForgeI18n", COMPAT + "LegacyForgeI18n", Set.of(
                        "getPattern(Ljava/lang/String;)Ljava/lang/String;")))
                .add(new Entry("net/minecraftforge/forgespi/language/IModInfo",
                        "net/neoforged/neoforgespi/language/IModInfo",
                        Set.of("getDisplayName()Ljava/lang/String;")))
                .add(new Entry("net/minecraftforge/common/Tags$Blocks",
                                "net/neoforged/neoforge/common/Tags$Blocks", tagFields(
                                        "FENCE_GATES_WOODEN", "FENCES_WOODEN", "FENCES", "GLASS_PANES_COLORLESS",
                                        "GLASS_PANES", "NEEDS_GOLD_TOOL", "NEEDS_NETHERITE_TOOL", "ORE_RATES_SINGULAR",
                                        "ORES_GOLD", "ORES_IN_GROUND_DEEPSLATE", "ORES_IN_GROUND_NETHERRACK",
                                        "ORES_IN_GROUND_STONE", "ORES", "STORAGE_BLOCKS_NETHERITE", "STORAGE_BLOCKS")))
                .add(entry("net/minecraft/core/particles/ParticleOptions", COMPAT + "LegacyParticleOptions", null))
                .add(entry("net/minecraft/core/particles/ParticleOptions$Deserializer",
                        COMPAT + "LegacyParticleDeserializer", null))
                .add(new Entry("net/minecraftforge/common/loot/LootModifier",
                        "net/neoforged/neoforge/common/loot/LootModifier", Set.of(
                                "<init>([Lnet/minecraft/world/level/storage/loot/predicates/LootItemCondition;)V",
                                "codecStart(Lcom/mojang/serialization/codecs/RecordCodecBuilder$Instance;)"
                                        + "Lcom/mojang/datafixers/Products$P1;",
                                "apply(Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"
                                        + "Lnet/minecraft/world/level/storage/loot/LootContext;)"
                                        + "Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",
                                "doApply(Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"
                                        + "Lnet/minecraft/world/level/storage/loot/LootContext;)"
                                        + "Lit/unimi/dsi/fastutil/objects/ObjectArrayList;")))
                .add(new Entry("net/minecraftforge/common/capabilities/ICapabilityProvider",
                        COMPAT + "LegacyICapabilityProvider", Set.of()))
                .add(new Entry("net/minecraftforge/common/capabilities/Capability",
                        COMPAT + "LegacyCapability", Set.of(
                                "getName()Ljava/lang/String;",
                                "orEmpty(Lnet/minecraftforge/common/capabilities/Capability;"
                                        + "Lnet/minecraftforge/common/util/LazyOptional;)"
                                        + "Lnet/minecraftforge/common/util/LazyOptional;")))
                .add(new Entry("net/minecraftforge/common/capabilities/CapabilityManager",
                        COMPAT + "LegacyCapabilityManager", Set.of(
                                "get(Lnet/minecraftforge/common/capabilities/CapabilityToken;)"
                                        + "Lnet/minecraftforge/common/capabilities/Capability;")))
                .add(new Entry("net/minecraftforge/common/capabilities/CapabilityToken",
                        COMPAT + "LegacyCapabilityToken", Set.of("<init>()V")))
                .add(new Entry("net/minecraftforge/common/capabilities/ForgeCapabilities",
                        COMPAT + "LegacyForgeCapabilities", Set.of(
                                "ENERGYLnet/minecraftforge/common/capabilities/Capability;",
                                "FLUID_HANDLERLnet/minecraftforge/common/capabilities/Capability;",
                                "FLUID_HANDLER_ITEMLnet/minecraftforge/common/capabilities/Capability;",
                                "ITEM_HANDLERLnet/minecraftforge/common/capabilities/Capability;")))
                .add(new Entry("net/minecraftforge/common/IPlantable",
                        COMPAT + "LegacyIPlantable", Set.of(
                                "getPlant(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)"
                                        + "Lnet/minecraft/world/level/block/state/BlockState;",
                                "getPlantType(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)"
                                        + "Lnet/minecraftforge/common/PlantType;")))
                .add(new Entry("net/minecraftforge/common/crafting/IShapedRecipe",
                        "net/minecraft/world/item/crafting/ShapedRecipe", Set.of()))
                .add(new Entry("net/minecraftforge/common/crafting/VanillaIngredientSerializer",
                        COMPAT + "LegacyVanillaIngredientSerializer", Set.of(
                                "INSTANCELnet/minecraftforge/common/crafting/VanillaIngredientSerializer;",
                                "parse(Lcom/google/gson/JsonObject;)"
                                        + "Lnet/minecraft/world/item/crafting/Ingredient;")))
                .add(new Entry("net/minecraftforge/common/util/TablePrinter",
                        COMPAT + "LegacyTablePrinter", Set.of(
                                "<init>()V",
                                "add(Ljava/lang/Object;)Lnet/minecraftforge/common/util/TablePrinter;",
                                "add(Ljava/util/Collection;)Lnet/minecraftforge/common/util/TablePrinter;",
                                "build(Ljava/lang/StringBuilder;)V",
                                "header(Ljava/lang/String;Ljava/util/function/Function;)"
                                        + "Lnet/minecraftforge/common/util/TablePrinter;",
                                "header(Ljava/lang/String;Ljava/util/function/Function;"
                                        + "Z)Lnet/minecraftforge/common/util/TablePrinter;")))
                .add(blockPathTypes())
                .add(new Entry("net/minecraft/network/FriendlyByteBuf$Writer",
                        COMPAT + "LegacyByteBufWriter", Set.of(
                                "accept(Ljava/lang/Object;Ljava/lang/Object;)V",
                                "andThen(Ljava/util/function/BiConsumer;)Ljava/util/function/BiConsumer;",
                                "m_236883_()Lnet/minecraft/network/FriendlyByteBuf$Writer;",
                                "optional()Lnet/minecraft/network/FriendlyByteBuf$Writer;")))
                .add(new Entry("net/minecraft/network/FriendlyByteBuf$Reader",
                        COMPAT + "LegacyByteBufReader", Set.of(
                                "apply(Ljava/lang/Object;)Ljava/lang/Object;",
                                "andThen(Ljava/util/function/Function;)Ljava/util/function/Function;",
                                "compose(Ljava/util/function/Function;)Ljava/util/function/Function;",
                                "m_236879_()Lnet/minecraft/network/FriendlyByteBuf$Reader;",
                                "optional()Lnet/minecraft/network/FriendlyByteBuf$Reader;")))
                .add(new Entry("net/minecraftforge/registries/RegistryBuilder",
                        COMPAT + "LegacyRegistryBuilder", Set.of(
                                "<init>()V",
                                "setName(Lnet/minecraft/resources/ResourceLocation;)"
                                        + "Lnet/minecraftforge/registries/RegistryBuilder;",
                                "setMaxID(I)Lnet/minecraftforge/registries/RegistryBuilder;",
                                "setDefaultKey(Lnet/minecraft/resources/ResourceLocation;)"
                                        + "Lnet/minecraftforge/registries/RegistryBuilder;",
                                "disableSaving()Lnet/minecraftforge/registries/RegistryBuilder;",
                                "disableSync()Lnet/minecraftforge/registries/RegistryBuilder;",
                                "disableOverrides()Lnet/minecraftforge/registries/RegistryBuilder;",
                                "allowModification()Lnet/minecraftforge/registries/RegistryBuilder;",
                                "hasTags()Lnet/minecraftforge/registries/RegistryBuilder;",
                                "create()Lnet/minecraftforge/registries/IForgeRegistry;")))
                .add(new Entry("net/minecraft/world/level/block/StemGrownBlock",
                        COMPAT + "LegacyStemGrownBlock", Set.of(
                                "<init>(Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)V",
                                "m_7161_()Lnet/minecraft/world/level/block/StemBlock;",
                                "getStem()Lnet/minecraft/world/level/block/StemBlock;",
                                "m_7810_()Lnet/minecraft/world/level/block/AttachedStemBlock;",
                                "getAttachedStem()Lnet/minecraft/world/level/block/AttachedStemBlock;")))
                .add(new Entry("net/minecraft/world/level/block/SandBlock",
                        COMPAT + "LegacySandBlock", Set.of(
                                "<init>(ILnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)V",
                                "m_6248_(Lnet/minecraft/world/level/block/state/BlockState;"
                                        + "Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)I",
                                "getDustColor(Lnet/minecraft/world/level/block/state/BlockState;"
                                        + "Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)I")))
                .add(new Entry("net/minecraftforge/event/entity/living/LootingLevelEvent",
                        COMPAT + "LegacyLootingLevelEvent", Set.of(
                                "<init>(Lnet/minecraft/world/entity/LivingEntity;"
                                        + "Lnet/minecraft/world/damagesource/DamageSource;I)V",
                                "getEntity()Lnet/minecraft/world/entity/LivingEntity;",
                                "getDamageSource()Lnet/minecraft/world/damagesource/DamageSource;",
                                "getLootingLevel()I", "setLootingLevel(I)V")))
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
                .add(new Entry("net/minecraftforge/common/extensions/IForgeBlockEntity",
                        "net/neoforged/neoforge/common/extensions/IBlockEntityExtension", Set.of(
                                "onChunkUnloaded()V",
                                "onLoad()V",
                                "getPersistentData()Lnet/minecraft/nbt/CompoundTag;",
                                "requestModelDataUpdate()V",
                                "getModelData()Lnet/minecraftforge/client/model/data/ModelData;",
                                "hasCustomOutlineRendering(Lnet/minecraft/world/entity/player/Player;)Z")))
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
                                "initialize(Lnet/minecraft/world/item/ItemStack;)V",
                                "m_8010_(Lnet/minecraft/world/entity/player/Player;)Z")))
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
                .add(entry("net/minecraft/world/level/storage/loot/entries/LootPoolEntryContainer$Serializer",
                        COMPAT + "LegacyLootEntrySerializer", null))
                .add(entry("net/minecraft/world/level/storage/loot/entries/LootPoolSingletonContainer$Serializer",
                        COMPAT + "LegacyLootSingletonSerializer", null))
                .add(entry("net/minecraft/world/level/storage/loot/functions/LootItemConditionalFunction$Serializer",
                        COMPAT + "LegacyLootConditionalFunctionSerializer", null))
                .add(new Entry(FU + "NonNullConsumer", COMPAT + "NonNullConsumer",
                        Set.of("accept(Ljava/lang/Object;)V")))
                .add(new Entry(FU + "NonNullPredicate", COMPAT + "NonNullPredicate",
                        Set.of("test(Ljava/lang/Object;)Z")))
                .add(new Entry(FU + "NonNullFunction", COMPAT + "NonNullFunction",
                        Set.of("apply(Ljava/lang/Object;)Ljava/lang/Object;")))
                .add(new Entry(FU + "NonNullSupplier", COMPAT + "NonNullSupplier",
                        Set.of("get()Ljava/lang/Object;")))
                // Forge capability lookups have no direct NeoForge equivalent. ICapabilityProvider is mapped to a
                // link-only marker; its unsupported calls are replaced with explicit fail-at-call stubs.
                // Capability, CapabilityManager and CapabilityToken remain unmapped.
                .add(registryObject())
                .add(entry(R + "IForgeRegistry", COMPAT + "IForgeRegistry", registryMembers()))
                .addAll(TagMappings.entries())
                .add(entry(R + "ForgeRegistries", COMPAT + "ForgeRegistries", forgeRegistriesFields(false)))
                .add(entry(R + "ForgeRegistries$Keys", COMPAT + "ForgeRegistries$Keys", forgeRegistriesFields(true)))
                .add(entry(R + "DeferredRegister", COMPAT + "LegacyDeferredRegister", Set.of(
                        "create(Lnet/minecraftforge/registries/IForgeRegistry;Ljava/lang/String;)" + dr,
                        "create(Lnet/minecraft/resources/ResourceKey;Ljava/lang/String;)" + dr,
                        "create(Lnet/minecraft/resources/ResourceLocation;Ljava/lang/String;)" + dr,
                        "register(Ljava/lang/String;Ljava/util/function/Supplier;)" + ro,
                        "register(Lnet/minecraftforge/eventbus/api/IEventBus;)V",
                        "makeRegistry(Ljava/util/function/Supplier;)Ljava/util/function/Supplier;",
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
                .addAll(IngredientMappings.entries())
                .addAll(CommonApiMappings.entries())
                .addAll(ModelGeneratorMappings.entries())
                .addAll(ClientModelMappings.entries())
                .addAll(CommonHookMappings.entries())
                .addAll(FluidUtilityMappings.entries())
                .addAll(WorldGenerationMappings.entries())
                .addAll(ItemHandlerMappings.entries())
                .addAll(ItemAbilityAndFluidMappings.entries())
                .addAll(KeyConflictMappings.entries())
                .addAll(NetworkMappings.entries())
                .addAll(TickMappings.entries())
                .addAll(GeneratedOwnerTable.entries())
                .addAll(DataMappings.entries())
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

        String tagKey = "Lnet/minecraft/tags/TagKey;";
        mappings.add(entry("net/minecraftforge/common/Tags$Fluids", "net/neoforged/neoforge/common/Tags$Fluids",
                        tagFields("MILK")))
                .add(entry("net/minecraftforge/common/Tags$Biomes", "net/neoforged/neoforge/common/Tags$Biomes",
                        tagFields("IS_SANDY", "IS_SNOWY")));

        String items = "net/minecraftforge/common/Tags$Items";
        mappings.addFieldAlias(items, "COBBLESTONE", tagKey, "COBBLESTONES")
                .addFieldAlias(items, "GLASS", tagKey, "GLASS_BLOCKS")
                .addFieldAlias(items, "GLASS_COLORLESS", tagKey, "GLASS_BLOCKS_COLORLESS")
                .addFieldAlias(items, "GLASS_TINTED", tagKey, "GLASS_BLOCKS_TINTED")
                .addFieldAlias(items, "GRAVEL", tagKey, "GRAVELS")
                .addFieldAlias(items, "GUNPOWDER", tagKey, "GUNPOWDERS")
                .addFieldAlias(items, "LEATHER", tagKey, "LEATHERS")
                .addFieldAlias(items, "OBSIDIAN", tagKey, "OBSIDIANS")
                .addFieldAlias(items, "SAND", tagKey, "SANDS")
                .addFieldAlias(items, "SAND_COLORLESS", tagKey, "SANDS_COLORLESS")
                .addFieldAlias(items, "SAND_RED", tagKey, "SANDS_RED")
                .addFieldAlias(items, "SANDSTONE", tagKey, "SANDSTONE_BLOCKS")
                .addFieldAlias(items, "SHEARS", tagKey, "TOOLS_SHEAR")
                .addFieldAlias(items, "STONE", tagKey, "STONES")
                .addFieldAlias(items, "STRING", tagKey, "STRINGS")
                .addFieldAlias(items, "TOOLS_BOWS", tagKey, "TOOLS_BOW")
                .addFieldAlias(items, "TOOLS_CROSSBOWS", tagKey, "TOOLS_CROSSBOW")
                .addFieldAlias(items, "TOOLS_FISHING_RODS", tagKey, "TOOLS_FISHING_ROD")
                .addFieldAlias(items, "TOOLS_SHIELDS", tagKey, "TOOLS_SHIELD");

        String blocks = "net/minecraftforge/common/Tags$Blocks";
        mappings.addFieldAlias(blocks, "GLASS", tagKey, "GLASS_BLOCKS")
                .addFieldAlias(blocks, "GLASS_COLORLESS", tagKey, "GLASS_BLOCKS_COLORLESS")
                .addFieldAlias(blocks, "GLASS_TINTED", tagKey, "GLASS_BLOCKS_TINTED")
                .addFieldAlias(blocks, "GLASS_SILICA", tagKey, "GLASS_BLOCKS_CHEAP");
        mappings.addFieldAlias(items, "GLASS_SILICA", tagKey, "GLASS_BLOCKS_CHEAP")
                .addFieldAlias(items, "TOOLS_TRIDENTS", tagKey, "TOOLS_SPEAR");

        return mappings;
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
            "FEATURES", "ENTITY_DATA_SERIALIZERS", "ENCHANTMENTS", "COMMAND_ARGUMENT_TYPES");

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
        if (keys) {
            for (String f : NEOFORGE_KEY_FIELDS) out.add(f + desc);
            out.add("DISPLAY_CONTEXTS" + desc);
        } else {
            out.add("FLUID_TYPES" + "Ljava/util/function/Supplier;");
            out.add("DISPLAY_CONTEXTS" + "Ljava/util/function/Supplier;");
        }
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
                "getEntries()Ljava/util/Set;",
                "tags()Lnet/minecraftforge/registries/tags/ITagManager;",
                "iterator()Ljava/util/Iterator;",
                "register(Lnet/minecraft/resources/ResourceLocation;Ljava/lang/Object;)V");
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

    private static Entry blockPathTypes() {
        String oldOwner = "net/minecraft/world/level/pathfinder/BlockPathTypes";
        String desc = "L" + oldOwner + ";";
        Set<String> members = new java.util.HashSet<>();
        for (String name : Set.of(
                "BLOCKED", "OPEN", "WALKABLE", "WALKABLE_DOOR", "TRAPDOOR", "POWDER_SNOW", "DANGER_POWDER_SNOW",
                "FENCE", "LAVA", "WATER", "WATER_BORDER", "RAIL", "UNPASSABLE_RAIL", "DANGER_FIRE", "DAMAGE_FIRE",
                "DANGER_OTHER", "DAMAGE_OTHER", "DOOR_OPEN", "DOOR_WOOD_CLOSED", "DOOR_IRON_CLOSED", "BREACH",
                "LEAVES", "STICKY_HONEY", "COCOA", "DAMAGE_CAUTIOUS")) {
            members.add(name + desc);
        }

        members.addAll(Set.of(
                "values()[" + desc,
                "valueOf(Ljava/lang/String;)" + desc,
                "name()Ljava/lang/String;",
                "ordinal()I",
                "toString()Ljava/lang/String;",
                "compareTo(Ljava/lang/Enum;)I",
                "compareTo(Ljava/lang/Object;)I",
                "getDeclaringClass()Ljava/lang/Class;",
                "getMalus()F"));
        return entry(oldOwner, "net/minecraft/world/level/pathfinder/PathType", Set.copyOf(members));
    }

    private static Set<String> tagFields(String... names) {
        Set<String> fields = new java.util.HashSet<>();
        for (String name : names) fields.add(name + "Lnet/minecraft/tags/TagKey;");
        return Set.copyOf(fields);
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

            @Override
            public String mapFieldName(String owner, String name, String descriptor) {
                if (blocked.contains(owner)) return name;
                return mappedFieldName(owner, name, descriptor);
            }
        };
    }
}
