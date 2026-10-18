package net.neoforged.forgestuck.transformer.jar;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Forge 1.20.1 {@code ToolAction}/{@code ToolActions} (NeoForge: {@code ItemAbility}/{@code ItemAbilities}, same
 * string-id model and member names), {@code ForgeFlowingFluid} (NeoForge: {@code BaseFlowingFluid}, identical
 * erased constructor and builder signatures) and the {@code RegisterCapabilitiesEvent} owner. Constants and
 * members are reflection-checked in {@code ForgeOwnerMappingTargetsTest}; anything NeoForge lacks (for example
 * {@code ForgeFlowingFluid.Properties.canMultiply}) fails at the call. {@code RegisterCapabilitiesEvent.register(Class)}
 * is redirected to {@code compat.LegacyCapabilities} by {@code ForgeOwnerRemapStep}.
 */
final class ItemAbilityAndFluidMappings {
    private ItemAbilityAndFluidMappings() {}

    private static final String F = "net/minecraftforge/fluids/ForgeFlowingFluid";
    private static final String NF = "net/neoforged/neoforge/fluids/BaseFlowingFluid";

    private static Set<String> rarityMembers() {
        String d = "Lnet/minecraft/world/item/enchantment/Enchantment$Rarity;";
        return Set.of("COMMON" + d, "UNCOMMON" + d, "RARE" + d, "VERY_RARE" + d, "getWeight()I");
    }

    private static Set<String> categoryMembers() {
        String d = "Lnet/minecraft/world/item/enchantment/EnchantmentCategory;";
        Set<String> out = new java.util.HashSet<>();
        out.add("create(Ljava/lang/String;Ljava/util/function/Predicate;)" + d);
        for (String n : List.of("ARMOR", "ARMOR_FEET", "ARMOR_LEGS", "ARMOR_CHEST", "ARMOR_HEAD", "WEAPON", "DIGGER",
                "FISHING_ROD", "TRIDENT", "BREAKABLE", "BOW", "WEARABLE", "CROSSBOW", "VANISHABLE")) {
            out.add(n + d);
        }
        return out;
    }

    static List<ForgeOwnerMappings.Entry> entries() {
        String ta = "Lnet/minecraftforge/common/ToolAction;";
        String ro = "Lnet/minecraftforge/registries/RegistryObject;";
        Set<String> actions = new java.util.HashSet<>();
        for (String n : List.of("AXE_DIG", "PICKAXE_DIG", "SHOVEL_DIG", "HOE_DIG", "SWORD_DIG", "SHEARS_DIG",
                "AXE_STRIP", "AXE_SCRAPE", "AXE_WAX_OFF", "SHOVEL_FLATTEN", "SWORD_SWEEP", "SHEARS_HARVEST",
                "SHEARS_CARVE", "SHEARS_DISARM", "HOE_TILL", "SHIELD_BLOCK", "FISHING_ROD_CAST", "BRUSH_BRUSH")) {
            actions.add(n + ta);
        }
        for (String n : List.of("DEFAULT_AXE_ACTIONS", "DEFAULT_HOE_ACTIONS", "DEFAULT_SHOVEL_ACTIONS",
                "DEFAULT_PICKAXE_ACTIONS", "DEFAULT_SWORD_ACTIONS", "DEFAULT_SHEARS_ACTIONS",
                "DEFAULT_SHIELD_ACTIONS", "DEFAULT_FISHING_ROD_ACTIONS", "DEFAULT_BRUSH_ACTIONS")) {
            actions.add(n + "Ljava/util/Set;");
        }
        String props = "L" + F + "$Properties;";
        String sup = "Ljava/util/function/Supplier;";
        String fluidStack = "Lnet/minecraftforge/fluids/FluidStack;";
        String fluidAction = "Lnet/minecraftforge/fluids/capability/IFluidHandler$FluidAction;";
        List<ForgeOwnerMappings.Entry> list = new ArrayList<>(List.of(
                e("net/minecraftforge/common/ToolAction", "net/neoforged/neoforge/common/ItemAbility", Set.of(
                        "get(Ljava/lang/String;)" + ta, "name()Ljava/lang/String;",
                        "getActions()Ljava/util/Collection;")),
                e("net/minecraftforge/common/ToolActions", "net/neoforged/neoforge/common/ItemAbilities", actions),
                e("net/minecraftforge/fluids/capability/IFluidHandler",
                        "net/neoforged/neoforge/fluids/capability/IFluidHandler", Set.of(
                                "getTanks()I",
                                "getFluidInTank(I)" + fluidStack,
                                "getTankCapacity(I)I",
                                "isFluidValid(I" + fluidStack + ")Z",
                                "fill(" + fluidStack + fluidAction + ")I",
                                "drain(" + fluidStack + fluidAction + ")" + fluidStack,
                                "drain(I" + fluidAction + ")" + fluidStack)),
                e("net/minecraftforge/fluids/IFluidTank", "net/neoforged/neoforge/fluids/IFluidTank", Set.of(
                        "getFluid()" + fluidStack, "getFluidAmount()I", "getCapacity()I",
                        "isFluidValid(" + fluidStack + ")Z",
                        "fill(" + fluidStack + fluidAction + ")I",
                        "drain(I" + fluidAction + ")" + fluidStack,
                        "drain(" + fluidStack + fluidAction + ")" + fluidStack)),
                e("net/minecraftforge/fluids/capability/templates/FluidTank",
                        "net/neoforged/neoforge/fluids/capability/templates/FluidTank", Set.of(
                                "<init>(I)V",
                                "<init>(ILjava/util/function/Predicate;)V",
                                "validatorLjava/util/function/Predicate;",
                                "fluid" + fluidStack,
                                "capacityI",
                                "setCapacity(I)Lnet/minecraftforge/fluids/capability/templates/FluidTank;",
                                "setValidator(Ljava/util/function/Predicate;)"
                                        + "Lnet/minecraftforge/fluids/capability/templates/FluidTank;",
                                "isFluidValid(" + fluidStack + ")Z",
                                "getCapacity()I",
                                "getFluid()" + fluidStack,
                                "getFluidAmount()I",
                                "getTanks()I",
                                "getFluidInTank(I)" + fluidStack,
                                "getTankCapacity(I)I",
                                "isFluidValid(I" + fluidStack + ")Z",
                                "fill(" + fluidStack + fluidAction + ")I",
                                "drain(" + fluidStack + fluidAction + ")" + fluidStack,
                                "drain(I" + fluidAction + ")" + fluidStack,
                                "onContentsChanged()V",
                                "setFluid(" + fluidStack + ")V",
                                "isEmpty()Z",
                                "getSpace()I")),
                e("net/minecraftforge/common/brewing/IBrewingRecipe",
                        "net/neoforged/neoforge/common/brewing/IBrewingRecipe", Set.of(
                                "isInput(Lnet/minecraft/world/item/ItemStack;)Z",
                                "isIngredient(Lnet/minecraft/world/item/ItemStack;)Z",
                                "getOutput(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)"
                                        + "Lnet/minecraft/world/item/ItemStack;")),
                e("net/minecraftforge/common/brewing/BrewingRecipe",
                        "net/neoforged/neoforge/common/brewing/BrewingRecipe", Set.of(
                                "<init>(Lnet/minecraft/world/item/crafting/Ingredient;"
                                        + "Lnet/minecraft/world/item/crafting/Ingredient;"
                                        + "Lnet/minecraft/world/item/ItemStack;)V",
                                "isInput(Lnet/minecraft/world/item/ItemStack;)Z",
                                "isIngredient(Lnet/minecraft/world/item/ItemStack;)Z",
                                "getOutput(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)"
                                        + "Lnet/minecraft/world/item/ItemStack;",
                                "getInput()Lnet/minecraft/world/item/crafting/Ingredient;",
                                "getIngredient()Lnet/minecraft/world/item/crafting/Ingredient;",
                                "getOutput()Lnet/minecraft/world/item/ItemStack;")),
                e("net/minecraftforge/event/entity/player/EntityItemPickupEvent",
                        "net/neoforged/neoforge/event/entity/player/ItemEntityPickupEvent$Pre", Set.of(
                                "<init>(Lnet/minecraft/world/entity/player/Player;"
                                        + "Lnet/minecraft/world/entity/item/ItemEntity;)V")),
                e("net/minecraftforge/fluids/capability/IFluidHandler$FluidAction",
                        "net/neoforged/neoforge/fluids/capability/IFluidHandler$FluidAction", Set.of(
                                "EXECUTEL" + "net/minecraftforge/fluids/capability/IFluidHandler$FluidAction;",
                                "SIMULATEL" + "net/minecraftforge/fluids/capability/IFluidHandler$FluidAction;",
                                "values()[Lnet/minecraftforge/fluids/capability/IFluidHandler$FluidAction;",
                                "valueOf(Ljava/lang/String;)Lnet/minecraftforge/fluids/capability/IFluidHandler$FluidAction;",
                                "execute()Z",
                                "simulate()Z")),
                e("net/minecraftforge/fluids/capability/IFluidHandlerItem",
                        "net/neoforged/neoforge/fluids/capability/IFluidHandlerItem",
                        Set.of(
                                "getContainer()Lnet/minecraft/world/item/ItemStack;",
                                "getTanks()I",
                                "getFluidInTank(I)" + fluidStack,
                                "getTankCapacity(I)I",
                                "isFluidValid(I" + fluidStack + ")Z",
                                "fill(" + fluidStack + fluidAction + ")I",
                                "drain(" + fluidStack + fluidAction + ")" + fluidStack,
                                "drain(I" + fluidAction + ")" + fluidStack)),
                e("net/minecraftforge/fluids/FluidStack", "net/neoforged/neoforge/fluids/FluidStack", Set.of(
                        "<init>(Lnet/minecraft/world/level/material/Fluid;I)V",
                        "EMPTYLnet/minecraftforge/fluids/FluidStack;",
                        "getFluid()Lnet/minecraft/world/level/material/Fluid;", "getAmount()I", "setAmount(I)V",
                        "grow(I)V", "shrink(I)V", "isEmpty()Z",
                        "copy()Lnet/minecraftforge/fluids/FluidStack;",
                        "isFluidEqual(Lnet/minecraftforge/fluids/FluidStack;)Z",
                        "areFluidStackTagsEqual(Lnet/minecraftforge/fluids/FluidStack;"
                                + "Lnet/minecraftforge/fluids/FluidStack;)Z",
                        "getDisplayName()Lnet/minecraft/network/chat/Component;")),
                e("net/minecraftforge/common/loot/LootModifierManager",
                        "net/neoforged/forgestuck/compat/LegacyLootModifierManager",
                        Set.of("GSON_INSTANCELcom/google/gson/Gson;")),
                e("net/minecraftforge/entity/IEntityAdditionalSpawnData",
                        "net/neoforged/forgestuck/compat/LegacyEntityAdditionalSpawnData",
                        Set.of("writeSpawnData(Lnet/minecraft/network/FriendlyByteBuf;)V",
                                "readSpawnData(Lnet/minecraft/network/FriendlyByteBuf;)V")),
                e("net/minecraftforge/event/entity/living/PotionColorCalculationEvent",
                        "net/neoforged/forgestuck/compat/LegacyPotionColorCalculationEvent", Set.of()),
                e("net/minecraftforge/registries/MissingMappingsEvent",
                        "net/neoforged/forgestuck/compat/LegacyMissingMappingsEvent", Set.of()),
                e("net/minecraft/world/item/enchantment/Enchantment$Rarity",
                        "net/neoforged/forgestuck/compat/LegacyEnchantmentRarity", rarityMembers()),
                e("net/minecraft/world/item/enchantment/EnchantmentCategory",
                        "net/neoforged/forgestuck/compat/LegacyEnchantmentCategory", categoryMembers()),
                e("net/minecraft/advancements/critereon/AbstractCriterionTriggerInstance",
                        "net/neoforged/forgestuck/compat/LegacyCriterionInstance",
                        Set.of("<init>(Lnet/minecraft/resources/ResourceLocation;"
                                + "Lnet/minecraft/advancements/critereon/ContextAwarePredicate;)V",
                                "getCriterion()Lnet/minecraft/resources/ResourceLocation;")),
                e("net/minecraft/world/level/block/AbstractGlassBlock", "net/minecraft/world/level/block/TransparentBlock",
                        Set.of("<init>(Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)V")),
                e("net/minecraft/world/level/block/GlassBlock", "net/minecraft/world/level/block/TransparentBlock",
                        Set.of("<init>(Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)V")),
                e(F, NF, Set.of("<init>(" + props + ")V")),
                e(F + "$Source", NF + "$Source", Set.of("<init>(" + props + ")V")),
                e(F + "$Flowing", NF + "$Flowing", Set.of("<init>(" + props + ")V")),
                e(F + "$Properties", NF + "$Properties", Set.of(
                        "<init>(" + sup + sup + sup + ")V",
                        "bucket(" + sup + ")" + props, "block(" + sup + ")" + props,
                        "slopeFindDistance(I)" + props, "levelDecreasePerBlock(I)" + props,
                        "explosionResistance(F)" + props, "tickRate(I)" + props)),
                e("net/minecraftforge/common/capabilities/RegisterCapabilitiesEvent",
                        "net/neoforged/neoforge/capabilities/RegisterCapabilitiesEvent", Set.of()),
                // Overlays became layers: only the members with identical descriptors are mapped; getPartialTick
                // (float vs DeltaTracker), getOverlay, getWindow and the constructors fail at the call.
                e("net/minecraftforge/common/ForgeMod", "net/neoforged/forgestuck/compat/LegacyForgeMod", Set.of(
                        "LAVA_TYPE" + ro, "WATER_TYPE" + ro, "SWIM_SPEED" + ro, "NAMETAG_DISTANCE" + ro,
                        "ENTITY_GRAVITY" + ro,
                        "enableMilkFluid()V")),
                // NeoForge 1.21.1 removed PlantType; the compat class is a name-interned token holder with the 1.20.1
                // constants. Engine hooks (IPlantable.getPlantType, canSustainPlant) are not mapped.
                e("net/minecraftforge/common/PlantType", "net/neoforged/forgestuck/compat/PlantType", Set.of(
                        "get(Ljava/lang/String;)"+"Lnet/minecraftforge/common/PlantType;","getName()Ljava/lang/String;",
                        "PLAINS"+"Lnet/minecraftforge/common/PlantType;","DESERT"+"Lnet/minecraftforge/common/PlantType;","BEACH"+"Lnet/minecraftforge/common/PlantType;","CAVE"+"Lnet/minecraftforge/common/PlantType;","WATER"+"Lnet/minecraftforge/common/PlantType;","NETHER"+"Lnet/minecraftforge/common/PlantType;","CROP"+"Lnet/minecraftforge/common/PlantType;")),
                // Atlas stitching: only the Post event has a counterpart (TextureAtlasStitchedEvent, mod bus);
                // Pre is unmapped.
                e("net/minecraftforge/client/event/TextureStitchEvent",
                        "net/neoforged/neoforge/client/event/TextureAtlasStitchedEvent",
                        Set.of("getAtlas()Lnet/minecraft/client/renderer/texture/TextureAtlas;")),
                e("net/minecraftforge/client/event/TextureStitchEvent$Post",
                        "net/neoforged/neoforge/client/event/TextureAtlasStitchedEvent",
                        Set.of("getAtlas()Lnet/minecraft/client/renderer/texture/TextureAtlas;")),
                e("net/minecraftforge/client/event/RenderGuiOverlayEvent",
                        "net/neoforged/neoforge/client/event/RenderGuiLayerEvent",
                        Set.of("getGuiGraphics()Lnet/minecraft/client/gui/GuiGraphics;")),
                e("net/minecraftforge/client/event/RenderGuiOverlayEvent$Post",
                        "net/neoforged/neoforge/client/event/RenderGuiLayerEvent$Post",
                        Set.of("getGuiGraphics()Lnet/minecraft/client/gui/GuiGraphics;")),
                e("net/minecraftforge/client/event/RenderGuiOverlayEvent$Pre",
                        "net/neoforged/neoforge/client/event/RenderGuiLayerEvent$Pre",
                        Set.of("getGuiGraphics()Lnet/minecraft/client/gui/GuiGraphics;", "isCanceled()Z",
                                "setCanceled(Z)V"))));
        return list;
    }

    private static ForgeOwnerMappings.Entry e(String oldOwner, String newOwner, Set<String> members) {
        return new ForgeOwnerMappings.Entry(oldOwner, newOwner, members);
    }
}