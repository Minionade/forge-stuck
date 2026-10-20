package net.neoforged.forgestuck.transformer.jar;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Remaps legacy SRG member names to runtime names where mapping data exists:
 * <ul>
 * <li>method/field instruction references whose owner is a Minecraft/NeoForge class,</li>
 * <li>{@code @Shadow} member declarations of Mixin classes (and references to them from the same class),</li>
 * <li>Mixin annotation {@code method}/{@code target} strings.</li>
 * </ul>
 * Members without a mapping are left untouched.
 */
public class SrgMemberRemapStep implements ClassTransformStep {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgestuck");
    private static final String TAG = "[Forge Stuck!!]";
    private static final String INHERITED_RESOURCE_LOCATION_WITH_SUFFIX =
            "m_266382_(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;";
    private static final String FLUID = "net/minecraft/world/level/material/Fluid";
    private static final String ITEM = "net/minecraft/world/item/Item";
    private static final String FLUID_STATE_DEFINITION_DESC =
            "(Lnet/minecraft/world/level/block/state/StateDefinition$Builder;)V";
    private static final String BLOCK_STATE_DEFINITION_DESC =
            "(Lnet/minecraft/world/level/block/state/StateDefinition$Builder;)V";
    private static final String HOLDER = "net/minecraft/core/Holder";
    private static final String ENCHANTMENT_INSTANCE =
            "net/minecraft/world/item/enchantment/EnchantmentInstance";
    private static final String LEGACY_ENCHANTMENT_INSTANCE_DESC =
            "(Lnet/minecraft/world/item/enchantment/Enchantment;I)V";
    private static final String LEGACY_ENCHANTMENT_REGISTRY =
            "net/neoforged/forgestuck/compat/LegacyEnchantmentRegistry";
    private static final String RECIPE_MANAGER = "net/minecraft/world/item/crafting/RecipeManager";
    private static final String RECIPE_TYPE = "net/minecraft/world/item/crafting/RecipeType";
    private static final String RECIPE = "net/minecraft/world/item/crafting/Recipe";
    private static final String RESOURCE_LOCATION = "net/minecraft/resources/ResourceLocation";
    private static final String LEGACY_RECIPE_ID_DESC = "()L" + RESOURCE_LOCATION + ";";
    private static final String RECIPE_ID_DESC = "(L" + RECIPE + ";)L" + RESOURCE_LOCATION + ";";
    private static final String LEGACY_POTION_GET_DESC =
            "(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/item/alchemy/Potion;";
    private static final String LEGACY_POTION_COLOR_DESC =
            "(Lnet/minecraft/world/item/ItemStack;)I";
    private static final String LEGACY_POTION_NAME_DESC =
            "(Ljava/lang/String;)Ljava/lang/String;";
    private static final String LEGACY_POTION_EFFECTS_DESC =
            "(Lnet/minecraft/nbt/CompoundTag;)Ljava/util/List;";
    private static final String LEGACY_POTION_SHIM = "net/neoforged/forgestuck/compat/LegacyPotion";
    private static final String RECIPE_MANAGER_GET_RECIPES_FOR_DESC =
            "(L" + RECIPE_TYPE + ";Lnet/minecraft/world/Container;"
                    + "Lnet/minecraft/world/level/Level;)Ljava/util/List;";
    private static final String RECIPE_MANAGER_GET_RECIPE_FOR_DESC =
            "(L" + RECIPE_TYPE + ";Lnet/minecraft/world/Container;"
                    + "Lnet/minecraft/world/level/Level;)Ljava/util/Optional;";
    private static final String LEGACY_RECIPE_MANAGER =
            "net/neoforged/forgestuck/compat/LegacyRecipeManager";
    private static final String LEGACY_LIQUID_BLOCK = "net/neoforged/forgestuck/compat/LegacyLiquidBlock";
    private static final String LEGACY_RECIPE_MANAGER_BY_TYPE_DESC =
            "(L" + RECIPE_MANAGER + ";L" + RECIPE_TYPE + ";)Ljava/util/Map;";
    private static final String LEGACY_RECIPE_MANAGER_GET_ALL_RECIPES_FOR_DESC =
            "(L" + RECIPE_MANAGER + ";L" + RECIPE_TYPE + ";)Ljava/util/List;";
    private static final String LEGACY_RECIPE_MANAGER_GET_RECIPES_FOR_DESC =
            "(L" + RECIPE_MANAGER + ";L" + RECIPE_TYPE + ";Lnet/minecraft/world/Container;"
                    + "Lnet/minecraft/world/level/Level;)Ljava/util/List;";
    private static final String LEGACY_RECIPE_MANAGER_GET_RECIPE_FOR_DESC =
            "(L" + RECIPE_MANAGER + ";L" + RECIPE_TYPE + ";Lnet/minecraft/world/Container;"
                    + "Lnet/minecraft/world/level/Level;)Ljava/util/Optional;";
    private static final String LEGACY_RECIPE_RESULT_DESC =
            "(Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;";
    private static final String RECIPE_RESULT_DESC =
            "(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;";
    private static final String ITEMSTACK = "net/minecraft/world/item/ItemStack";
    private static final String ITEMSTACK_TAG = "()Lnet/minecraft/nbt/CompoundTag;";
    private static final String LEGACY_ITEMSTACK_SAVE_DESC =
            "(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;";
    private static final String LEGACY_ITEMSTACK_PARSE_DESC =
            "(Lnet/minecraft/nbt/CompoundTag;)L" + ITEMSTACK + ";";
    private static final String LEGACY_ITEMSTACK_SHIM =
            "net/neoforged/forgestuck/compat/LegacyItemStackShim";
    private static final String LEGACY_DYE_COLOR_SHIM =
            "net/neoforged/forgestuck/compat/LegacyDyeColorShim";
    private static final String LEGACY_ENCHANTMENT_SHIM =
            "net/neoforged/forgestuck/compat/LegacyEnchantmentShim";
    private static final String LEGACY_ITEM_EDIBLE_DESC = "(L" + ITEM + ";)Z";
    private static final String LEGACY_ITEM_FOOD_PROPERTIES =
            "Lnet/minecraft/world/food/FoodProperties;";
    private static final String LEGACY_CRITERIA_HELPER =
            "net/neoforged/forgestuck/compat/LegacyCriteriaTriggers";
    private static final String LEGACY_TOOL_STACK_PREDICATE =
            "net/neoforged/forgestuck/compat/LegacyToolStackItemPredicateAdapter";
    private static final String LEGACY_INGREDIENT_BRIDGE =
            "net/neoforged/forgestuck/compat/LegacyIngredientBridge";
    private static final String LOADABLE_INGREDIENT_SERIALIZER =
            "slimeknights/mantle/recipe/helper/LoadableIngredientSerializer";
    private static final String MODIFIABLE_ARMOR_MATERIAL =
            "slimeknights/tconstruct/library/tools/definition/ModifiableArmorMaterial";
    private static final String LEGACY_ARMOR_TYPE_SHIM =
            "net/neoforged/forgestuck/compat/LegacyArmorTypeShim";
    private static final String LEGACY_STEM_BLOCK_SHIM =
            "net/neoforged/forgestuck/compat/LegacyStemBlockShim";
    private static final String COFH_STEM_BLOCK = "cofh/lib/common/block/StemBlockCoFH";
    private static final String COFH_ATTACHED_STEM_BLOCK = "cofh/lib/common/block/AttachedStemBlockCoFH";
    private static final String LEGACY_MOB_TYPE = "net/neoforged/forgestuck/compat/LegacyMobType";
    private static final String LEGACY_MOB_EFFECT = "net/neoforged/forgestuck/compat/LegacyMobEffect";
    private static final String LEGACY_MOB_EFFECT_HAS_EFFECT_DESC =
            "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/effect/MobEffect;)Z";
    private static final String LEGACY_MOB_EFFECT_REMOVE_DESC =
            "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/effect/MobEffect;)Z";
    private static final String LEGACY_MOB_EFFECT_GET_EFFECT_DESC =
            "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/effect/MobEffect;)"
                    + "Lnet/minecraft/world/effect/MobEffectInstance;";
    private static final String LEGACY_ATTRIBUTE_LOOKUP =
            "net/neoforged/forgestuck/compat/LegacyAttribute";
    private static final String LEGACY_ATTRIBUTE_VALUE_DESC =
            "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/ai/attributes/Attribute;)D";
    private static final String LEGACY_ENTITY_EQUIPMENT =
            "net/neoforged/forgestuck/compat/LegacyEntityEquipment";
    private static final String LEGACY_ENTITY_ARMOR_SLOTS_DESC =
            "(Lnet/minecraft/world/entity/Entity;)Ljava/lang/Iterable;";
    private static final String LEGACY_TCONSTRUCT_EQUIPMENT_CONTEXT =
            "slimeknights/tconstruct/library/tools/context/EquipmentContext";
    private static final String LEGACY_TCONSTRUCT_MOB_EQUIPMENT =
            "slimeknights/tconstruct/library/json/loot/equipment/MobEquipment";
    private static final String LEGACY_TCONSTRUCT_TINTED_VERTEX_BUILDER =
            "slimeknights/tconstruct/smeltery/client/util/TintedVertexBuilder";
    private static final String LEGACY_MANTLE_MULTI_MODULE_SCREEN =
            "slimeknights/mantle/client/screen/MultiModuleScreen";
    private static final String LEGACY_SCREEN_BACKGROUND_DESC =
            "(Lnet/minecraft/client/gui/GuiGraphics;)V";
    private static final String SCREEN_BACKGROUND_DESC =
            "(Lnet/minecraft/client/gui/GuiGraphics;IIF)V";
    private static final String LEGACY_BLOCK_ENTITY_NBT_DESC =
            "(Lnet/minecraft/nbt/CompoundTag;)V";
    private static final String BLOCK_ENTITY_NBT_PROVIDER_DESC =
            "(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V";
    private static final String BLOCK_ENTITY = "net/minecraft/world/level/block/entity/BlockEntity";
    private static final String LEGACY_BLOCK_POS_READ_DESC =
            "(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/core/BlockPos;";
    private static final String LEGACY_BLOCK_POS_WRITE_DESC =
            "(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/nbt/CompoundTag;";
    private static final String LEGACY_BLOCK_POS_NBT =
            "net/neoforged/forgestuck/compat/LegacyBlockPosNbt";
    private static final String LEGACY_FLUID_TANK =
            "net/neoforged/forgestuck/compat/LegacyFluidTank";
    private static final String NEOFORGE_FLUID_TANK =
            "net/neoforged/neoforge/fluids/capability/templates/FluidTank";
    private static final String NEOFORGE_FLUID_STACK = "net/neoforged/neoforge/fluids/FluidStack";
    private static final String LEGACY_TCONSTRUCT_HEATING_STRUCTURE =
            "slimeknights/tconstruct/smeltery/block/entity/controller/HeatingStructureBlockEntity";
    private static final String LEGACY_TCONSTRUCT_CREATE_MENU_DESC =
            "(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/entity/player/Player;)"
                    + "Lnet/minecraft/world/inventory/AbstractContainerMenu;";
    private static final String PATCHOULI_VARIABLE_PROVIDER = "vazkii/patchouli/api/IVariableProvider";
    private static final String LEGACY_FIREWORK_SHAPE = "net/minecraft/world/item/FireworkRocketItem$Shape";
    private static final String FIREWORK_SHAPE = "net/minecraft/world/item/component/FireworkExplosion$Shape";
    private static final String LEGACY_POTION_MIX =
            "net/minecraft/world/item/alchemy/PotionBrewing$Mix";
    private static final String LEGACY_POTION_BREWING =
            "net/minecraft/world/item/alchemy/PotionBrewing";
    private static final String LEGACY_BREWING_REGISTRY =
            "net/neoforged/forgestuck/compat/LegacyBrewingRecipeRegistry";
    private static final String LEGACY_TCONSTRUCT_CONFIG =
            "slimeknights/tconstruct/common/config/Config$Common";
    private static final String LEGACY_RF_DRILL =
            "cofh/thermal/innovation/common/item/RFDrillItem";
    private static final String LEGACY_RF_SAW =
            "cofh/thermal/innovation/common/item/RFSawItem";
    private static final String LEGACY_COFH_NBT_SYNC =
            "cofh/lib/common/loot/TileNBTSync";
    private static final String LEGACY_LOOT_FUNCTION_REGISTRATION =
            "net/neoforged/forgestuck/compat/LegacyLootFunctionRegistration";
    private static final String COFH_ARMOR_ITEM = "cofh/core/common/item/ArmorItemCoFH";
    private static final String LEGACY_ARMOR_MATERIAL_SHIM =
            "net/neoforged/forgestuck/compat/LegacyArmorMaterialShim";
    private static final String TOOL_STACK_ITEM_PREDICATE =
            "slimeknights/tconstruct/library/json/predicate/tool/ToolStackItemPredicate";
    private static final String ITEM_PREDICATE = "net/minecraft/advancements/critereon/ItemPredicate";
    private static final String DAMAGE_SOURCE = "net/minecraft/world/damagesource/DamageSource";
    private static final String STRING_REPRESENTABLE = "net/minecraft/util/StringRepresentable";
    private static final String LEGACY_PROJECTILE_DISPENSE_BEHAVIOR =
            "net/minecraft/core/dispenser/AbstractProjectileDispenseBehavior";
    private static final String LEGACY_PROJECTILE_DISPENSE_SHIM =
            "net/neoforged/forgestuck/compat/LegacyProjectileDispenseBehavior";
    private static final String LEGACY_VANISHABLE = "net/minecraft/world/item/Vanishable";
    private static final String THERMAL_CLIENT_CONFIG =
            "cofh/thermal/core/common/config/ThermalClientConfig";
    private static final String LEGACY_SPRITE_SOURCES =
            "net/minecraft/client/renderer/texture/atlas/SpriteSources";
    private static final String LEGACY_SPRITE_SOURCE_REGISTRATION =
            "net/neoforged/forgestuck/compat/LegacySpriteSources";
    private static final String VERTEX_CONSUMER = "com/mojang/blaze3d/vertex/VertexConsumer";
    private static final String LEGACY_VERTEX_CONSUMER_SHIM =
            "net/neoforged/forgestuck/compat/LegacyVertexConsumerShim";
    private static final String VERTEX_FORMAT = "com/mojang/blaze3d/vertex/VertexFormat";
    private static final String LEGACY_VERTEX_FORMAT_SHIM =
            "net/neoforged/forgestuck/compat/LegacyVertexFormatShim";
    private static final String VERTEX_FORMAT_ELEMENT = "com/mojang/blaze3d/vertex/VertexFormatElement";
    private static final String LEGACY_DEFAULT_VERTEX_FORMAT =
            "com/mojang/blaze3d/vertex/DefaultVertexFormat";
    private static final String VERTEX_FORMAT_ELEMENTS_DESC =
            "Lcom/google/common/collect/ImmutableList;";
    private static final String LEGACY_VERTEX_FORMAT_ELEMENTS_DESC = "()" + VERTEX_FORMAT_ELEMENTS_DESC;
    private static final String LEGACY_LOOT_DATA_TYPE =
            "net/neoforged/forgestuck/compat/LegacyLootDataType";
    private static final String LEGACY_MODEL_RESOURCE_LOCATIONS =
            "net/neoforged/forgestuck/compat/LegacyModelResourceLocations";
    private static final String TEXTURE_ATLAS_SPRITE =
            "net/minecraft/client/renderer/texture/TextureAtlasSprite";
    private static final String BLOCK_MODEL =
            "net/minecraft/client/renderer/block/model/BlockModel";
    private static final String FACE_BAKERY =
            "net/minecraft/client/renderer/block/model/FaceBakery";
    private static final String LEGACY_FACE_BAKERY_RECOMPUTE_UVS_DESC =
            "(Lnet/minecraft/client/renderer/block/model/BlockFaceUV;"
                    + "Lnet/minecraft/core/Direction;"
                    + "Lcom/mojang/math/Transformation;"
                    + "Lnet/minecraft/resources/ResourceLocation;)"
                    + "Lnet/minecraft/client/renderer/block/model/BlockFaceUV;";
    private static final String FACE_BAKERY_RECOMPUTE_UVS_DESC =
            "(Lnet/minecraft/client/renderer/block/model/BlockFaceUV;"
                    + "Lnet/minecraft/core/Direction;"
                    + "Lcom/mojang/math/Transformation;)"
                    + "Lnet/minecraft/client/renderer/block/model/BlockFaceUV;";
    private static final String LEGACY_BLOCK_MODEL_BAKE_FACE_DESC =
            "(Lnet/minecraft/client/renderer/block/model/BlockElement;"
                    + "Lnet/minecraft/client/renderer/block/model/BlockElementFace;"
                    + "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"
                    + "Lnet/minecraft/core/Direction;"
                    + "Lnet/minecraft/client/resources/model/ModelState;"
                    + "Lnet/minecraft/resources/ResourceLocation;)"
                    + "Lnet/minecraft/client/renderer/block/model/BakedQuad;";
    private static final String BLOCK_MODEL_BAKE_FACE_DESC =
            "(Lnet/minecraft/client/renderer/block/model/BlockElement;"
                    + "Lnet/minecraft/client/renderer/block/model/BlockElementFace;"
                    + "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"
                    + "Lnet/minecraft/core/Direction;"
                    + "Lnet/minecraft/client/resources/model/ModelState;)"
                    + "Lnet/minecraft/client/renderer/block/model/BakedQuad;";
    private static final String POST_CHAIN = "net/minecraft/client/renderer/PostChain";
    private static final String LEGACY_POST_CHAIN_CONSTRUCTOR_DESC =
            "(Lnet/minecraft/client/renderer/texture/TextureManager;"
                    + "Lnet/minecraft/server/packs/resources/ResourceManager;"
                    + "Lcom/mojang/blaze3d/pipeline/RenderTarget;"
                    + "Lnet/minecraft/resources/ResourceLocation;)V";
    private static final String POST_CHAIN_CONSTRUCTOR_DESC =
            "(Lnet/minecraft/client/renderer/texture/TextureManager;"
                    + "Lnet/minecraft/server/packs/resources/ResourceProvider;"
                    + "Lcom/mojang/blaze3d/pipeline/RenderTarget;"
                    + "Lnet/minecraft/resources/ResourceLocation;)V";
    private static final String TEXT_COLOR = "net/minecraft/network/chat/TextColor";
    private static final String LEGACY_TEXT_COLOR_PARSE_DESC =
            "(Ljava/lang/String;)Lnet/minecraft/network/chat/TextColor;";
    private static final String LEGACY_TEXT_COLOR_SHIM =
            "net/neoforged/forgestuck/compat/LegacyTextColorShim";
    private static final String PLACEMENT_MODIFIER_TYPE =
            "net/minecraft/world/level/levelgen/placement/PlacementModifierType";
    private static final String LEGACY_PLACEMENT_CODEC_DESC = "()Lcom/mojang/serialization/Codec;";
    private static final String LEGACY_PLACEMENT_TYPE_ADAPTER =
            "net/neoforged/forgestuck/compat/LegacyPlacementModifierType";
    private static final String STRUCTURE_TYPE =
            "net/minecraft/world/level/levelgen/structure/StructureType";
    private static final String LEGACY_STRUCTURE_TYPE_ADAPTER =
            "net/neoforged/forgestuck/compat/LegacyStructureType";

    private final LegacyNameMappings mappings;
    private final Map<String, Set<String>> declaredMethods = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> declaredSupertypes = new ConcurrentHashMap<>();

    public SrgMemberRemapStep() {
        this(LegacyNameMappings.defaults());
    }

    private static boolean rewriteLegacyEnchantmentInstanceConstructor(MethodNode method, MethodInsnNode call) {
        if (call.getOpcode() != Opcodes.INVOKESPECIAL || !call.owner.equals(ENCHANTMENT_INSTANCE)
                || !call.name.equals("<init>") || !call.desc.equals(LEGACY_ENCHANTMENT_INSTANCE_DESC)) return false;
        TypeInsnNode allocation = null;
        for (AbstractInsnNode previous = call.getPrevious(); previous != null; previous = previous.getPrevious()) {
            if (previous instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW
                    && type.desc.equals(ENCHANTMENT_INSTANCE)) {
                allocation = type;
                break;
            }
        }
        if (allocation == null) return false;
        AbstractInsnNode duplicate = allocation.getNext();
        while (duplicate != null && duplicate.getOpcode() < 0) duplicate = duplicate.getNext();
        if (duplicate == null || duplicate.getOpcode() != Opcodes.DUP) return false;
        method.instructions.remove(allocation);
        method.instructions.remove(duplicate);
        method.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, LEGACY_ENCHANTMENT_REGISTRY,
                "createInstance",
                "(Lnet/minecraft/world/item/enchantment/Enchantment;I)L" + ENCHANTMENT_INSTANCE + ";", false));
        return true;
    }

    private static boolean isLegacyHolderGet(MethodInsnNode call) {
        return (call.owner.equals(HOLDER) || call.owner.equals(HOLDER + "$Reference"))
                && call.name.equals("get") && call.desc.equals("()Ljava/lang/Object;");
    }

    private static boolean isLegacyHolderGet(org.objectweb.asm.Handle handle) {
        return (handle.getOwner().equals(HOLDER) || handle.getOwner().equals(HOLDER + "$Reference"))
                && handle.getName().equals("get")
                && handle.getDesc().equals("()Ljava/lang/Object;");
    }

    private static boolean isLegacyRecipeManagerByType(MethodInsnNode call) {
        return call.owner.equals(RECIPE_MANAGER)
                && (call.name.equals("byType") || call.name.equals("m_44054_"))
                && call.desc.equals("(L" + RECIPE_TYPE + ";)Ljava/util/Map;");
    }

    private static boolean isLegacyRecipeId(MethodInsnNode call) {
        return (call.getOpcode() == Opcodes.INVOKEINTERFACE || call.getOpcode() == Opcodes.INVOKEVIRTUAL)
                && call.name.equals("m_6423_")
                && call.desc.equals(LEGACY_RECIPE_ID_DESC);
    }

    private static boolean rewriteLegacyRecipeMatches(MethodInsnNode call) {
        String descriptor = "(Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;)Z";
        if ((call.getOpcode() != Opcodes.INVOKEINTERFACE && call.getOpcode() != Opcodes.INVOKEVIRTUAL)
                || !(call.name.equals("m_5818_") || call.name.equals("matches"))
                || !call.desc.equals(descriptor)) return false;
        call.setOpcode(Opcodes.INVOKESTATIC);
        call.owner = LEGACY_RECIPE_MANAGER;
        call.name = "matchesLegacy";
        call.desc = "(Lnet/minecraft/world/item/crafting/Recipe;" + descriptor.substring(1);
        call.itf = false;
        return true;
    }

    private static boolean rewriteLegacyParticleCall(MethodInsnNode call) {
        String particleOptions = "Lnet/minecraft/core/particles/ParticleOptions;";
        String legacyPrefix = "(Lnet/neoforged/forgestuck/compat/LegacyParticleOptions;";
        String gamecompatPrefix = "(Lnet/neoforged/forgestuck/gamecompat/LegacyParticleOptions;";
        if (!(call.name.equals("addParticle") || call.name.equals("m_7106_"))) return false;
        String prefix = call.desc.startsWith(legacyPrefix) ? legacyPrefix
                : call.desc.startsWith(gamecompatPrefix) ? gamecompatPrefix : null;
        if (prefix == null) return false;
        call.name = "addParticle";
        call.desc = "(" + particleOptions + call.desc.substring(prefix.length());
        return true;
    }

    private static boolean rewriteLegacyLiquidBlockGetFluid(MethodInsnNode call) {
        if (call.getOpcode() != Opcodes.INVOKEVIRTUAL
                || !call.owner.equals("net/minecraft/world/level/block/LiquidBlock")
                || !call.name.equals("getFluid")
                || !call.desc.equals("()Lnet/minecraft/world/level/material/FlowingFluid;")) return false;
        call.setOpcode(Opcodes.INVOKESTATIC);
        call.owner = LEGACY_LIQUID_BLOCK;
        call.name = "getFluid";
        call.desc = "(Lnet/minecraft/world/level/block/LiquidBlock;)Lnet/minecraft/world/level/material/FlowingFluid;";
        call.itf = false;
        return true;
    }

    private static boolean rewriteLegacyScreenBackgroundCall(
            ClassNode node, MethodNode method, MethodInsnNode call) {
        if (!call.name.equals("m_280273_") || !call.desc.equals(LEGACY_SCREEN_BACKGROUND_DESC)
                || !(method.name.equals("m_88315_") || method.name.equals("render"))
                || !method.desc.equals(SCREEN_BACKGROUND_DESC)) {
            return false;
        }
        if (node.name.equals(LEGACY_MANTLE_MULTI_MODULE_SCREEN)) {
            method.instructions.set(call, new InsnNode(Opcodes.POP2));
            return true;
        }
        InsnList arguments = new InsnList();
        arguments.add(new VarInsnNode(Opcodes.ILOAD, 2));
        arguments.add(new VarInsnNode(Opcodes.ILOAD, 3));
        arguments.add(new VarInsnNode(Opcodes.FLOAD, 4));
        method.instructions.insertBefore(call, arguments);
        call.name = "renderBackground";
        call.desc = SCREEN_BACKGROUND_DESC;
        return true;
    }

    private static boolean addLegacyBlockEntityNbtBridges(ClassNode node, Set<String> remapped) {
        boolean modified = false;
        boolean directBlockEntityChild = BLOCK_ENTITY.equals(node.superName);
        boolean hasLegacyHook = node.methods.stream().anyMatch(method ->
                (method.name.equals("m_142466_") || method.name.equals("m_183515_"))
                        && method.desc.equals(LEGACY_BLOCK_ENTITY_NBT_DESC));
        if (directBlockEntityChild && hasLegacyHook) {
            for (String name : List.of("m_142466_", "m_183515_")) {
                if (node.methods.stream().anyMatch(method ->
                        method.name.equals(name) && method.desc.equals(LEGACY_BLOCK_ENTITY_NBT_DESC))) {
                    continue;
                }
                MethodNode fallback = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                        name, LEGACY_BLOCK_ENTITY_NBT_DESC, null, null);
                fallback.visitCode();
                fallback.visitInsn(Opcodes.RETURN);
                fallback.visitMaxs(0, 2);
                fallback.visitEnd();
                node.methods.add(fallback);
                remapped.add("BlockEntity legacy " + name + " no-op super bridge");
                modified = true;
            }
        }
        for (String[] hook : new String[][] {
                {"m_142466_", "loadAdditional"},
                {"m_183515_", "saveAdditional"}
        }) {
            boolean hasLegacyThisHook = node.methods.stream().anyMatch(method ->
                    method.name.equals(hook[0]) && method.desc.equals(LEGACY_BLOCK_ENTITY_NBT_DESC)
                            && (method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) == 0);
            if (!hasLegacyThisHook || node.methods.stream().anyMatch(method ->
                    method.name.equals(hook[1]) && method.desc.equals(BLOCK_ENTITY_NBT_PROVIDER_DESC))) {
                continue;
            }
            MethodNode bridge = new MethodNode(Opcodes.ACC_PROTECTED | Opcodes.ACC_SYNTHETIC,
                    hook[1], BLOCK_ENTITY_NBT_PROVIDER_DESC, null, null);
            bridge.visitCode();
            bridge.visitVarInsn(Opcodes.ALOAD, 2);
            bridge.visitMethodInsn(Opcodes.INVOKESTATIC, LEGACY_ITEMSTACK_SHIM, "pushRegistryProvider",
                    "(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/core/HolderLookup$Provider;", false);
            bridge.visitVarInsn(Opcodes.ASTORE, 3);
            Label tryStart = new Label();
            Label tryEnd = new Label();
            Label handler = new Label();
            bridge.visitLabel(tryStart);
            bridge.visitVarInsn(Opcodes.ALOAD, 0);
            bridge.visitVarInsn(Opcodes.ALOAD, 1);
            bridge.visitMethodInsn(Opcodes.INVOKESPECIAL, node.name, hook[0],
                    LEGACY_BLOCK_ENTITY_NBT_DESC, false);
            bridge.visitLabel(tryEnd);
            bridge.visitVarInsn(Opcodes.ALOAD, 3);
            bridge.visitMethodInsn(Opcodes.INVOKESTATIC, LEGACY_ITEMSTACK_SHIM, "restoreRegistryProvider",
                    "(Lnet/minecraft/core/HolderLookup$Provider;)V", false);
            bridge.visitInsn(Opcodes.RETURN);
            bridge.visitLabel(handler);
            bridge.visitFrame(Opcodes.F_FULL, 4,
                    new Object[] {node.name, "net/minecraft/nbt/CompoundTag",
                            "net/minecraft/core/HolderLookup$Provider", "net/minecraft/core/HolderLookup$Provider"},
                    1, new Object[] {"java/lang/Throwable"});
            bridge.visitVarInsn(Opcodes.ASTORE, 4);
            bridge.visitVarInsn(Opcodes.ALOAD, 3);
            bridge.visitMethodInsn(Opcodes.INVOKESTATIC, LEGACY_ITEMSTACK_SHIM, "restoreRegistryProvider",
                    "(Lnet/minecraft/core/HolderLookup$Provider;)V", false);
            bridge.visitVarInsn(Opcodes.ALOAD, 4);
            bridge.visitInsn(Opcodes.ATHROW);
            bridge.visitTryCatchBlock(tryStart, tryEnd, handler, "java/lang/Throwable");
            bridge.visitMaxs(2, 5);
            bridge.visitEnd();
            node.methods.add(bridge);
            remapped.add(hook[0] + " -> " + hook[1] + "(CompoundTag, Provider) bridge");
            modified = true;
        }
        return modified;
    }

    private static boolean rewriteLegacyBlockEntityNbtSuperCall(
            String currentClass, MethodNode method, MethodInsnNode call) {
        if (call.getOpcode() != Opcodes.INVOKESPECIAL
                || call.owner.equals(currentClass)
                || !call.desc.equals(LEGACY_BLOCK_ENTITY_NBT_DESC)
                || !(call.name.equals("m_142466_") || call.name.equals("m_183515_"))) {
            return false;
        }
        String hook = call.name.equals("m_142466_") ? "loadAdditional" : "saveAdditional";
        method.instructions.insertBefore(call, new MethodInsnNode(Opcodes.INVOKESTATIC, LEGACY_ITEMSTACK_SHIM,
                "getRegistryProvider", "()Lnet/minecraft/core/HolderLookup$Provider;", false));
        call.name = hook;
        call.desc = BLOCK_ENTITY_NBT_PROVIDER_DESC;
        method.maxStack = Math.max(method.maxStack, 3);
        return true;
    }

    private static boolean rewriteLegacyBlockPosNbt(MethodInsnNode call) {
        if (call.getOpcode() != Opcodes.INVOKESTATIC
                || !call.owner.equals("net/minecraft/nbt/NbtUtils")) {
            return false;
        }
        if (call.name.equals("m_129239_") && call.desc.equals(LEGACY_BLOCK_POS_READ_DESC)) {
            call.owner = LEGACY_BLOCK_POS_NBT;
            call.name = "read";
            call.desc = LEGACY_BLOCK_POS_READ_DESC;
            call.itf = false;
            return true;
        }
        if (call.name.equals("m_129224_") && call.desc.equals(LEGACY_BLOCK_POS_WRITE_DESC)) {
            call.owner = LEGACY_BLOCK_POS_NBT;
            call.name = "write";
            call.desc = LEGACY_BLOCK_POS_WRITE_DESC;
            call.itf = false;
            return true;
        }
        return false;
    }

    private static boolean rewriteFluidTankSetFluid(MethodInsnNode call) {
        if (!(call.getOpcode() == Opcodes.INVOKEVIRTUAL || call.getOpcode() == Opcodes.INVOKESPECIAL)
                || !call.owner.equals(NEOFORGE_FLUID_TANK)
                || !call.name.equals("setFluid")
                || !call.desc.equals("(L" + NEOFORGE_FLUID_STACK + ";)V")) {
            return false;
        }
        call.setOpcode(Opcodes.INVOKESTATIC);
        call.owner = LEGACY_FLUID_TANK;
        call.name = "setFluid";
        call.desc = "(L" + NEOFORGE_FLUID_TANK + ";L" + NEOFORGE_FLUID_STACK + ";)V";
        call.itf = false;
        return true;
    }

    private static boolean isLegacyRecipeId(org.objectweb.asm.Handle handle) {
        return (handle.getTag() == Opcodes.H_INVOKEINTERFACE || handle.getTag() == Opcodes.H_INVOKEVIRTUAL)
                && handle.getName().equals("m_6423_")
                && handle.getDesc().equals(LEGACY_RECIPE_ID_DESC);
    }

    private static org.objectweb.asm.Handle legacyRecipeIdHandle() {
        return new org.objectweb.asm.Handle(Opcodes.H_INVOKESTATIC, LEGACY_RECIPE_MANAGER, "getRecipeId",
                RECIPE_ID_DESC, false);
    }

    private static boolean isLegacyPotionGet(MethodInsnNode call) {
        return call.getOpcode() == Opcodes.INVOKESTATIC
                && call.owner.equals("net/minecraft/world/item/alchemy/PotionUtils")
                && call.name.equals("m_43577_")
                && call.desc.equals(LEGACY_POTION_GET_DESC);
    }

    private static boolean isLegacyPotionGet(org.objectweb.asm.Handle handle) {
        return handle.getTag() == Opcodes.H_INVOKESTATIC
                && handle.getOwner().equals("net/minecraft/world/item/alchemy/PotionUtils")
                && handle.getName().equals("m_43577_")
                && handle.getDesc().equals(LEGACY_POTION_GET_DESC);
    }

    private static org.objectweb.asm.Handle legacyPotionGetHandle() {
        return new org.objectweb.asm.Handle(Opcodes.H_INVOKESTATIC, LEGACY_POTION_SHIM, "getPotion",
                LEGACY_POTION_GET_DESC, false);
    }

    private static boolean isLegacyPotionColor(MethodInsnNode call) {
        return call.getOpcode() == Opcodes.INVOKESTATIC
                && call.owner.equals("net/minecraft/world/item/alchemy/PotionUtils")
                && call.name.equals("m_43575_")
                && call.desc.equals(LEGACY_POTION_COLOR_DESC);
    }

    private static boolean isLegacyPotionColor(org.objectweb.asm.Handle handle) {
        return handle.getTag() == Opcodes.H_INVOKESTATIC
                && handle.getOwner().equals("net/minecraft/world/item/alchemy/PotionUtils")
                && handle.getName().equals("m_43575_")
                && handle.getDesc().equals(LEGACY_POTION_COLOR_DESC);
    }

    private static org.objectweb.asm.Handle legacyPotionColorHandle() {
        return new org.objectweb.asm.Handle(Opcodes.H_INVOKESTATIC, LEGACY_POTION_SHIM, "getColor",
                LEGACY_POTION_COLOR_DESC, false);
    }

    private static boolean isLegacyRecipeManagerByType(org.objectweb.asm.Handle handle) {
        return (handle.getTag() == Opcodes.H_INVOKEVIRTUAL || handle.getTag() == Opcodes.H_INVOKEINTERFACE)
                && handle.getOwner().equals(RECIPE_MANAGER)
                && (handle.getName().equals("byType") || handle.getName().equals("m_44054_"))
                && handle.getDesc().equals("(L" + RECIPE_TYPE + ";)Ljava/util/Map;");
    }

    private static boolean isLegacyRecipeManagerGetRecipesFor(MethodInsnNode call) {
        return call.getOpcode() == Opcodes.INVOKEVIRTUAL
                && call.owner.equals(RECIPE_MANAGER)
                && (call.name.equals("getRecipesFor") || call.name.equals("m_44056_"))
                && call.desc.equals(RECIPE_MANAGER_GET_RECIPES_FOR_DESC);
    }

    private static boolean isLegacyRecipeManagerGetAllRecipesFor(MethodInsnNode call) {
        return call.getOpcode() == Opcodes.INVOKEVIRTUAL
                && call.owner.equals(RECIPE_MANAGER)
                && (call.name.equals("getAllRecipesFor") || call.name.equals("m_44013_"))
                && call.desc.equals("(L" + RECIPE_TYPE + ";)Ljava/util/List;");
    }

    private static boolean isLegacyRecipeManagerGetAllRecipesFor(org.objectweb.asm.Handle handle) {
        return handle.getTag() == Opcodes.H_INVOKEVIRTUAL
                && handle.getOwner().equals(RECIPE_MANAGER)
                && (handle.getName().equals("getAllRecipesFor") || handle.getName().equals("m_44013_"))
                && handle.getDesc().equals("(L" + RECIPE_TYPE + ";)Ljava/util/List;");
    }

    private static org.objectweb.asm.Handle legacyRecipeManagerGetAllRecipesForHandle() {
        return new org.objectweb.asm.Handle(Opcodes.H_INVOKESTATIC, LEGACY_RECIPE_MANAGER, "getAllRecipesFor",
                LEGACY_RECIPE_MANAGER_GET_ALL_RECIPES_FOR_DESC, false);
    }

    private static boolean isLegacyRecipeManagerGetRecipesFor(org.objectweb.asm.Handle handle) {
        return handle.getTag() == Opcodes.H_INVOKEVIRTUAL
                && handle.getOwner().equals(RECIPE_MANAGER)
                && (handle.getName().equals("getRecipesFor") || handle.getName().equals("m_44056_"))
                && handle.getDesc().equals(RECIPE_MANAGER_GET_RECIPES_FOR_DESC);
    }

    private static boolean isLegacyRecipeManagerGetRecipeFor(MethodInsnNode call) {
        return call.getOpcode() == Opcodes.INVOKEVIRTUAL
                && call.owner.equals(RECIPE_MANAGER)
                && (call.name.equals("getRecipeFor") || call.name.equals("m_44015_"))
                && call.desc.equals(RECIPE_MANAGER_GET_RECIPE_FOR_DESC);
    }

    private static boolean isLegacyRecipeManagerGetRecipeFor(org.objectweb.asm.Handle handle) {
        return handle.getTag() == Opcodes.H_INVOKEVIRTUAL
                && handle.getOwner().equals(RECIPE_MANAGER)
                && (handle.getName().equals("getRecipeFor") || handle.getName().equals("m_44015_"))
                && handle.getDesc().equals(RECIPE_MANAGER_GET_RECIPE_FOR_DESC);
    }

    private static boolean rewriteTConstructHeatingMenuColumnCount(ClassNode node, MethodNode method) {
        if (!node.name.equals("slimeknights/tconstruct/smeltery/menu/HeatingStructureContainerMenu")
                || !method.name.equals("calcColumns")
                || !method.desc.equals("(I)I")
                || (method.access & Opcodes.ACC_STATIC) == 0) {
            return false;
        }

        boolean modified = false;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn.getOpcode() == Opcodes.IRETURN) {
                InsnList ensureColumn = new InsnList();
                ensureColumn.add(new InsnNode(Opcodes.ICONST_1));
                ensureColumn.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "max", "(II)I", false));
                method.instructions.insertBefore(insn, ensureColumn);
                modified = true;
            }
        }
        return modified;
    }

    private static boolean syncTConstructStructureBeforeMenuOpen(ClassNode node, MethodNode method) {
        if (!node.name.equals(LEGACY_TCONSTRUCT_HEATING_STRUCTURE)
                || !(method.name.equals("m_7208_") || method.name.equals("createMenu"))
                || !method.desc.equals(LEGACY_TCONSTRUCT_CREATE_MENU_DESC)) {
            return false;
        }
        InsnList sync = new InsnList();
        sync.add(new VarInsnNode(Opcodes.ALOAD, 0));
        sync.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "net/neoforged/forgestuck/compat/LegacyTConstructStructureSync",
                "resendStructure", "(Ljava/lang/Object;)V", false));
        method.instructions.insert(sync);
        return true;
    }

    private static boolean isLegacyDyeableLeatherColor(MethodInsnNode call) {
        return (call.getOpcode() == Opcodes.INVOKEINTERFACE || call.getOpcode() == Opcodes.INVOKEVIRTUAL)
                && call.owner.equals("net/minecraft/world/item/DyeableLeatherItem")
                && (call.name.equals("getColor") || call.name.equals("m_41121_"))
                && call.desc.equals("(Lnet/minecraft/world/item/ItemStack;)I");
    }

    private static boolean isLegacyDyeableLeatherColor(org.objectweb.asm.Handle handle) {
        return (handle.getTag() == Opcodes.H_INVOKEINTERFACE || handle.getTag() == Opcodes.H_INVOKEVIRTUAL)
                && handle.getOwner().equals("net/minecraft/world/item/DyeableLeatherItem")
                && (handle.getName().equals("getColor") || handle.getName().equals("m_41121_"))
                && handle.getDesc().equals("(Lnet/minecraft/world/item/ItemStack;)I");
    }

    private static boolean isLegacyItemEdible(MethodInsnNode call) {
        return call.owner.equals(ITEM) && call.name.equals("m_41472_") && call.desc.equals("()Z")
                && call.getOpcode() == Opcodes.INVOKEVIRTUAL;
    }

    private static boolean isLegacyItemDamageable(MethodInsnNode call) {
        return call.owner.equals(ITEM)
                && call.name.equals("m_41465_")
                && call.desc.equals("()Z")
                && call.getOpcode() == Opcodes.INVOKEVIRTUAL;
    }

    private static boolean isLegacyItemEdible(org.objectweb.asm.Handle handle) {
        return handle.getTag() == Opcodes.H_INVOKEVIRTUAL
                && handle.getOwner().equals(ITEM)
                && handle.getName().equals("m_41472_")
                && handle.getDesc().equals("()Z");
    }

    private static org.objectweb.asm.Handle legacyItemEdibleHandle() {
        return new org.objectweb.asm.Handle(Opcodes.H_INVOKESTATIC, LEGACY_ITEMSTACK_SHIM, "isEdible",
                LEGACY_ITEM_EDIBLE_DESC, false);
    }

    private static org.objectweb.asm.Handle legacyRecipeManagerByTypeHandle(org.objectweb.asm.Handle handle) {
        return new org.objectweb.asm.Handle(Opcodes.H_INVOKESTATIC, LEGACY_RECIPE_MANAGER, "byType",
                LEGACY_RECIPE_MANAGER_BY_TYPE_DESC, false);
    }

    private static org.objectweb.asm.Handle legacyRecipeManagerGetRecipesForHandle() {
        return new org.objectweb.asm.Handle(Opcodes.H_INVOKESTATIC, LEGACY_RECIPE_MANAGER, "getRecipesFor",
                LEGACY_RECIPE_MANAGER_GET_RECIPES_FOR_DESC, false);
    }

    private static org.objectweb.asm.Handle legacyRecipeManagerGetRecipeForHandle() {
        return new org.objectweb.asm.Handle(Opcodes.H_INVOKESTATIC, LEGACY_RECIPE_MANAGER, "getRecipeFor",
                LEGACY_RECIPE_MANAGER_GET_RECIPE_FOR_DESC, false);
    }

    private boolean addLegacyBlockUseBridges(ClassNode node, Set<String> remapped) {
        String legacyUseDescriptor = "(Lnet/minecraft/world/level/block/state/BlockState;"
                + "Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                + "Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;"
                + "Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;";
        if (!"net/minecraft/world/level/block/Block".equals(node.superName)
                && !hasSupertype(node.name, "net/minecraft/world/level/block/Block")) return false;
        MethodNode legacyUse = node.methods.stream().filter(method ->
                (method.name.equals("m_6227_") || method.name.equals("use"))
                        && method.desc.equals(legacyUseDescriptor)
                        && (method.access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_PRIVATE)) == 0)
                .findFirst().orElse(null);
        if (legacyUse == null) {
            if (!"net/minecraft/world/level/block/Block".equals(node.superName)
                    || node.methods.stream().anyMatch(method ->
                            method.name.equals("m_6227_") && method.desc.equals(legacyUseDescriptor))) {
                return false;
            }
            MethodNode fallback = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                    "m_6227_", legacyUseDescriptor, null, null);
            fallback.visitCode();
            fallback.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/world/InteractionResult", "PASS",
                    "Lnet/minecraft/world/InteractionResult;");
            fallback.visitInsn(Opcodes.ARETURN);
            fallback.visitMaxs(1, 7);
            fallback.visitEnd();
            node.methods.add(fallback);
            remapped.add("Block.use -> m_6227_ (fallback)");
            return true;
        }

        boolean modified = false;
        String useWithoutItemDescriptor = "(Lnet/minecraft/world/level/block/state/BlockState;"
                + "Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                + "Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/BlockHitResult;)"
                + "Lnet/minecraft/world/InteractionResult;";
        if (node.methods.stream().noneMatch(method ->
                method.name.equals("useWithoutItem") && method.desc.equals(useWithoutItemDescriptor))) {
            MethodNode bridge = new MethodNode(Opcodes.ACC_PROTECTED | Opcodes.ACC_SYNTHETIC,
                    "useWithoutItem", useWithoutItemDescriptor, null, null);
            bridge.visitCode();
            bridge.visitVarInsn(Opcodes.ALOAD, 0);
            bridge.visitVarInsn(Opcodes.ALOAD, 1);
            bridge.visitVarInsn(Opcodes.ALOAD, 2);
            bridge.visitVarInsn(Opcodes.ALOAD, 3);
            bridge.visitVarInsn(Opcodes.ALOAD, 4);
            bridge.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/world/InteractionHand", "MAIN_HAND",
                    "Lnet/minecraft/world/InteractionHand;");
            bridge.visitVarInsn(Opcodes.ALOAD, 5);
            bridge.visitMethodInsn(Opcodes.INVOKEVIRTUAL, node.name, legacyUse.name,
                    legacyUseDescriptor, false);
            bridge.visitInsn(Opcodes.ARETURN);
            bridge.visitMaxs(7, 6);
            bridge.visitEnd();
            node.methods.add(bridge);
            remapped.add(legacyUse.name + " -> BlockBehaviour.useWithoutItem (bridge)");
            modified = true;
        }

        String useItemOnDescriptor = "(Lnet/minecraft/world/item/ItemStack;"
                + "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;"
                + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;"
                + "Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)"
                + "Lnet/minecraft/world/ItemInteractionResult;";
        if (node.methods.stream().noneMatch(method ->
                method.name.equals("useItemOn") && method.desc.equals(useItemOnDescriptor))) {
            MethodNode bridge = new MethodNode(Opcodes.ACC_PROTECTED | Opcodes.ACC_SYNTHETIC,
                    "useItemOn", useItemOnDescriptor, null, null);
            bridge.visitCode();
            bridge.visitVarInsn(Opcodes.ALOAD, 0);
            bridge.visitVarInsn(Opcodes.ALOAD, 2);
            bridge.visitVarInsn(Opcodes.ALOAD, 3);
            bridge.visitVarInsn(Opcodes.ALOAD, 4);
            bridge.visitVarInsn(Opcodes.ALOAD, 5);
            bridge.visitVarInsn(Opcodes.ALOAD, 6);
            bridge.visitVarInsn(Opcodes.ALOAD, 7);
            bridge.visitMethodInsn(Opcodes.INVOKEVIRTUAL, node.name, legacyUse.name,
                    legacyUseDescriptor, false);
            bridge.visitMethodInsn(Opcodes.INVOKESTATIC,
                    "net/neoforged/forgestuck/compat/LegacyBlockInteractionShim",
                    "toItemInteractionResult",
                    "(Lnet/minecraft/world/InteractionResult;)Lnet/minecraft/world/ItemInteractionResult;", false);
            bridge.visitInsn(Opcodes.ARETURN);
            bridge.visitMaxs(7, 8);
            bridge.visitEnd();
            node.methods.add(bridge);
            remapped.add(legacyUse.name + " -> BlockBehaviour.useItemOn (bridge)");
            modified = true;
        }
        return modified;
    }

    private static boolean isLegacyRecipeResult(MethodInsnNode call) {
        return (call.name.equals("m_8043_") || call.name.equals("getResultItem"))
                && call.desc.equals(LEGACY_RECIPE_RESULT_DESC);
    }

    private static boolean isLegacyRecipeResult(org.objectweb.asm.Handle handle) {
        return (handle.getTag() == Opcodes.H_INVOKEVIRTUAL || handle.getTag() == Opcodes.H_INVOKEINTERFACE)
                && (handle.getName().equals("m_8043_") || handle.getName().equals("getResultItem"))
                && handle.getDesc().equals(LEGACY_RECIPE_RESULT_DESC);
    }

    private static org.objectweb.asm.Handle currentRecipeResultHandle(org.objectweb.asm.Handle handle) {
        return new org.objectweb.asm.Handle(handle.getTag(), handle.getOwner(), "getResultItem",
                RECIPE_RESULT_DESC, handle.isInterface());
    }

    private static boolean isLegacyArmorTypeValues(MethodInsnNode call) {
        return call.getOpcode() == org.objectweb.asm.Opcodes.INVOKESTATIC
                && call.owner.equals("net/minecraft/world/item/ArmorItem$Type")
                && call.name.equals("values")
                && call.desc.equals("()[Lnet/minecraft/world/item/ArmorItem$Type;");
    }

    private boolean rewriteLegacyTextureAtlasSpriteUv(MethodNode method, MethodInsnNode call) {
        if (!call.owner.equals(TEXTURE_ATLAS_SPRITE) || !call.desc.equals("(D)F")) return false;
        String targetName = call.name;
        if (!targetName.equals("getU") && !targetName.equals("getV")) {
            targetName = mappings.map(call.owner, call.name, call.desc);
        }
        if (!"getU".equals(targetName) && !"getV".equals(targetName)) return false;
        method.instructions.insertBefore(call, new InsnNode(Opcodes.D2F));
        // Legacy sprite UVs span 0-16; 1.21 getU/getV inputs are normalized to 0-1.
        method.instructions.insertBefore(call, new org.objectweb.asm.tree.LdcInsnNode(0.0625f));
        method.instructions.insertBefore(call, new InsnNode(Opcodes.FMUL));
        call.name = targetName;
        call.desc = "(F)F";
        return true;
    }

    private static boolean rewriteLegacyAabbBlockPosConstructor(MethodNode method, MethodInsnNode call) {
        String aabb = "net/minecraft/world/phys/AABB";
        String blockPosPair = "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)V";
        if (call.getOpcode() != Opcodes.INVOKESPECIAL
                || !call.owner.equals(aabb)
                || !call.name.equals("<init>")
                || !call.desc.equals(blockPosPair)) return false;
        for (AbstractInsnNode instruction = call.getPrevious(); instruction != null;
                instruction = instruction.getPrevious()) {
            if (!(instruction instanceof TypeInsnNode allocation)
                    || allocation.getOpcode() != Opcodes.NEW
                    || !allocation.desc.equals(aabb)) continue;
            AbstractInsnNode duplicate = nextMeaningful(allocation);
            if (duplicate == null || duplicate.getOpcode() != Opcodes.DUP) return false;
            method.instructions.remove(duplicate);
            method.instructions.remove(allocation);
            call.setOpcode(Opcodes.INVOKESTATIC);
            call.name = "encapsulatingFullBlocks";
            call.desc = "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)L" + aabb + ";";
            call.itf = false;
            return true;
        }
        return false;
    }

    private static boolean rewriteLegacyVertexFormatConstructor(MethodNode method, MethodInsnNode call) {
        String legacyDescriptor = "(Lcom/google/common/collect/ImmutableMap;)V";
        if (call.getOpcode() != Opcodes.INVOKESPECIAL
                || !call.owner.equals(VERTEX_FORMAT)
                || !call.name.equals("<init>")
                || !call.desc.equals(legacyDescriptor)) return false;
        for (AbstractInsnNode instruction = call.getPrevious(); instruction != null;
                instruction = instruction.getPrevious()) {
            if (!(instruction instanceof TypeInsnNode allocation)
                    || allocation.getOpcode() != Opcodes.NEW
                    || !allocation.desc.equals(VERTEX_FORMAT)) continue;
            AbstractInsnNode duplicate = nextMeaningful(allocation);
            if (duplicate == null || duplicate.getOpcode() != Opcodes.DUP) return false;
            method.instructions.remove(duplicate);
            method.instructions.remove(allocation);
            call.setOpcode(Opcodes.INVOKESTATIC);
            call.owner = LEGACY_VERTEX_FORMAT_SHIM;
            call.name = "create";
            call.desc = "(Lcom/google/common/collect/ImmutableMap;)L" + VERTEX_FORMAT + ";";
            call.itf = false;
            return true;
        }
        return false;
    }

    private boolean rewriteLegacyVertexFormatElements(MethodInsnNode call) {
        if (!call.owner.equals(VERTEX_FORMAT) || !call.desc.equals(LEGACY_VERTEX_FORMAT_ELEMENTS_DESC)) return false;
        String targetName = call.name.equals("getElements")
                ? call.name
                : mappings.map(call.owner, call.name, call.desc);
        if (!"getElements".equals(targetName)) return false;
        call.setOpcode(Opcodes.INVOKESTATIC);
        call.owner = LEGACY_VERTEX_FORMAT_SHIM;
        call.name = targetName;
        call.desc = "(L" + VERTEX_FORMAT + ";)" + VERTEX_FORMAT_ELEMENTS_DESC;
        call.itf = false;
        return true;
    }

    private static String legacyMobTypeField(FieldInsnNode field) {
        if (field.getOpcode() != org.objectweb.asm.Opcodes.GETSTATIC
                || !field.owner.equals(LEGACY_MOB_TYPE)
                || !field.desc.equals("L" + LEGACY_MOB_TYPE + ";")) return null;
        return switch (field.name) {
            case "f_21640_" -> "UNDEFINED";
            case "f_21641_" -> "UNDEAD";
            case "f_21642_" -> "ARTHROPOD";
            case "f_21643_" -> "ILLAGER";
            case "f_21644_" -> "WATER";
            default -> null;
        };
    }

    private static boolean rewriteLegacyVertexFormatElementField(MethodNode method, FieldInsnNode field) {
        if (isLegacyVertexFormatPadding(field.owner, field.name, field.desc)) {
            method.instructions.set(field, new MethodInsnNode(Opcodes.INVOKESTATIC, LEGACY_VERTEX_FORMAT_SHIM,
                    "paddingElementPlaceholder", "()L" + VERTEX_FORMAT_ELEMENT + ";", false));
            return true;
        }
        String targetName = legacyVertexFormatElementName(field.owner, field.name, field.desc);
        if (targetName == null) return false;
        field.owner = VERTEX_FORMAT_ELEMENT;
        field.name = targetName;
        return true;
    }

    private static org.objectweb.asm.Handle legacyVertexFormatElementHandle(org.objectweb.asm.Handle handle) {
        if (handle.getTag() != Opcodes.H_GETSTATIC) return null;
        if (isLegacyVertexFormatPadding(handle.getOwner(), handle.getName(), handle.getDesc())) {
            return new org.objectweb.asm.Handle(Opcodes.H_INVOKESTATIC, LEGACY_VERTEX_FORMAT_SHIM,
                    "paddingElementPlaceholder", "()L" + VERTEX_FORMAT_ELEMENT + ";", false);
        }
        String targetName = legacyVertexFormatElementName(handle.getOwner(), handle.getName(), handle.getDesc());
        return targetName == null ? null
                : new org.objectweb.asm.Handle(handle.getTag(), VERTEX_FORMAT_ELEMENT, targetName,
                        handle.getDesc(), false);
    }

    private static org.objectweb.asm.Handle legacyVertexFormatConstructorHandle(org.objectweb.asm.Handle handle) {
        if (handle.getTag() != Opcodes.H_NEWINVOKESPECIAL
                || !handle.getOwner().equals(VERTEX_FORMAT)
                || !handle.getName().equals("<init>")
                || !handle.getDesc().equals("(Lcom/google/common/collect/ImmutableMap;)V")) return null;
        return new org.objectweb.asm.Handle(Opcodes.H_INVOKESTATIC, LEGACY_VERTEX_FORMAT_SHIM, "create",
                "(Lcom/google/common/collect/ImmutableMap;)L" + VERTEX_FORMAT + ";", false);
    }

    private static org.objectweb.asm.Handle legacyAabbBlockPosConstructorHandle(org.objectweb.asm.Handle handle) {
        String aabb = "net/minecraft/world/phys/AABB";
        if (handle.getTag() != Opcodes.H_NEWINVOKESPECIAL
                || !handle.getOwner().equals(aabb)
                || !handle.getName().equals("<init>")
                || !handle.getDesc().equals("(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)V")) {
            return null;
        }
        return new org.objectweb.asm.Handle(Opcodes.H_INVOKESTATIC, aabb, "encapsulatingFullBlocks",
                "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)L" + aabb + ";", false);
    }

    private static boolean isLegacyVertexFormatPadding(String owner, String name, String descriptor) {
        return LEGACY_DEFAULT_VERTEX_FORMAT.equals(owner)
                && (name.equals("f_85810_") || name.equals("ELEMENT_PADDING"))
                && descriptor.equals("L" + VERTEX_FORMAT_ELEMENT + ";");
    }

    private static String legacyVertexFormatElementName(String owner, String name, String descriptor) {
        if (!LEGACY_DEFAULT_VERTEX_FORMAT.equals(owner)
                || !descriptor.equals("L" + VERTEX_FORMAT_ELEMENT + ";")) return null;
        return switch (name) {
            case "f_85804_", "ELEMENT_POSITION" -> "POSITION";
            case "f_85805_", "ELEMENT_COLOR" -> "COLOR";
            case "f_85806_", "ELEMENT_UV0" -> "UV0";
            case "f_85807_", "ELEMENT_UV1" -> "UV1";
            case "f_85808_", "ELEMENT_UV2" -> "UV2";
            case "f_85809_", "ELEMENT_NORMAL" -> "NORMAL";
            default -> null;
        };
    }

    private static boolean rewriteLegacyArmorItemConstructor(MethodNode method, MethodInsnNode call) {
        String legacyDescriptor = "(Lnet/neoforged/forgestuck/compat/LegacyArmorMaterial;"
                + "Lnet/minecraft/world/item/ArmorItem$Type;"
                + "Lnet/minecraft/world/item/Item$Properties;)V";
        if (call.getOpcode() != org.objectweb.asm.Opcodes.INVOKESPECIAL
                || !call.owner.equals("net/minecraft/world/item/ArmorItem")
                || !call.name.equals("<init>")
                || !call.desc.equals(legacyDescriptor)) return false;
        int materialLocal = method.maxLocals++;
        int typeLocal = method.maxLocals++;
        int propertiesLocal = method.maxLocals++;
        InsnList arguments = new InsnList();
        arguments.add(new VarInsnNode(org.objectweb.asm.Opcodes.ASTORE, propertiesLocal));
        arguments.add(new VarInsnNode(org.objectweb.asm.Opcodes.ASTORE, typeLocal));
        arguments.add(new VarInsnNode(org.objectweb.asm.Opcodes.ASTORE, materialLocal));
        arguments.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, materialLocal));
        arguments.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, typeLocal));
        arguments.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, propertiesLocal));
        arguments.add(new MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESTATIC, LEGACY_ARMOR_MATERIAL_SHIM,
                "setDurability",
                "(Lnet/neoforged/forgestuck/compat/LegacyArmorMaterial;"
                        + "Lnet/minecraft/world/item/ArmorItem$Type;"
                        + "Lnet/minecraft/world/item/Item$Properties;)Lnet/minecraft/world/item/Item$Properties;",
                false));
        arguments.add(new VarInsnNode(org.objectweb.asm.Opcodes.ASTORE, propertiesLocal));
        arguments.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, materialLocal));
        arguments.add(new MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESTATIC, LEGACY_ARMOR_MATERIAL_SHIM,
                "asHolder", "(Lnet/neoforged/forgestuck/compat/LegacyArmorMaterial;)"
                        + "Lnet/minecraft/core/Holder;", false));
        arguments.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, typeLocal));
        arguments.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, propertiesLocal));
        method.instructions.insertBefore(call, arguments);
        method.maxStack = Math.max(method.maxStack, 4);
        call.desc = "(Lnet/minecraft/core/Holder;Lnet/minecraft/world/item/ArmorItem$Type;"
                + "Lnet/minecraft/world/item/Item$Properties;)V";
        return true;
    }

    private static boolean rewriteLegacyPostChainConstructor(MethodInsnNode call) {
        if (call.getOpcode() != Opcodes.INVOKESPECIAL
                || !call.owner.equals(POST_CHAIN)
                || !call.name.equals("<init>")
                || !call.desc.equals(LEGACY_POST_CHAIN_CONSTRUCTOR_DESC)) return false;
        call.desc = POST_CHAIN_CONSTRUCTOR_DESC;
        return true;
    }

    private static boolean rewriteLegacyTextColorParse(MethodInsnNode call) {
        if (call.getOpcode() != Opcodes.INVOKESTATIC
                || !call.owner.equals(TEXT_COLOR)
                || !call.desc.equals(LEGACY_TEXT_COLOR_PARSE_DESC)) return false;
        call.owner = LEGACY_TEXT_COLOR_SHIM;
        call.name = "parseColor";
        call.itf = false;
        return true;
    }

    private static boolean rewriteLegacyMapCodecTypeLambda(MethodNode method,
            org.objectweb.asm.tree.InvokeDynamicInsnNode indy) {
        org.objectweb.asm.Type returnType = org.objectweb.asm.Type.getReturnType(indy.desc);
        if (returnType.getSort() != org.objectweb.asm.Type.OBJECT) return false;
        String legacyType = returnType.getInternalName();
        String adapter = switch (legacyType) {
            case PLACEMENT_MODIFIER_TYPE -> LEGACY_PLACEMENT_TYPE_ADAPTER;
            case STRUCTURE_TYPE -> LEGACY_STRUCTURE_TYPE_ADAPTER;
            default -> null;
        };
        if (adapter == null) return false;
        if (!indy.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")
                || !(indy.bsm.getName().equals("metafactory") || indy.bsm.getName().equals("altMetafactory"))
                || !indy.desc.equals("()L" + legacyType + ";")
                || indy.bsmArgs.length != 3
                || !(indy.bsmArgs[0] instanceof org.objectweb.asm.Type samType)
                || !samType.getDescriptor().equals(LEGACY_PLACEMENT_CODEC_DESC)
                || !(indy.bsmArgs[1] instanceof org.objectweb.asm.Handle implementation)
                || implementation.getTag() != Opcodes.H_INVOKESTATIC
                || !implementation.getDesc().equals(LEGACY_PLACEMENT_CODEC_DESC)
                || !(indy.bsmArgs[2] instanceof org.objectweb.asm.Type instantiatedType)
                || !instantiatedType.getDescriptor().equals(LEGACY_PLACEMENT_CODEC_DESC)) return false;
        InsnList replacement = new InsnList();
        replacement.add(new MethodInsnNode(Opcodes.INVOKESTATIC, implementation.getOwner(),
                implementation.getName(), implementation.getDesc(), implementation.isInterface()));
        replacement.add(new MethodInsnNode(Opcodes.INVOKESTATIC, adapter, "adapt",
                "(Lcom/mojang/serialization/Codec;)L" + legacyType + ";", false));
        method.instructions.insertBefore(indy, replacement);
        method.instructions.remove(indy);
        return true;
    }

    public SrgMemberRemapStep(LegacyNameMappings mappings) {
        this.mappings = mappings;
    }

    @Override
    public void scan(String className, byte[] bytes, OwnerRemapReport report) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        Set<String> methods = new HashSet<>();
        for (MethodNode method : node.methods) {
            methods.add(method.name + method.desc);
        }
        declaredMethods.computeIfAbsent(node.name, ignored -> ConcurrentHashMap.newKeySet()).addAll(methods);
        Set<String> supertypes = new HashSet<>(node.interfaces);
        if (node.superName != null) supertypes.add(node.superName);
        declaredSupertypes.put(node.name, Set.copyOf(supertypes));
    }

    /** Removed game members with a faithful runtime stand-in: {@code owner.name+desc} -> {owner, name}. */
    private static final java.util.Map<String, String[]> REPLACEMENTS = java.util.Map.of(
            "net/minecraft/core/registries/BuiltInRegistries.f_256876_Lnet/minecraft/core/Registry;",
            new String[] {"net/neoforged/forgestuck/compat/LegacyEnchantmentRegistry", "ENCHANTMENT"},
            "net/minecraft/core/registries/BuiltInRegistries.f_257047_Lnet/minecraft/core/Registry;",
            new String[] {"net/neoforged/forgestuck/compat/LegacyRegistryRoot", "REGISTRY"});

    static boolean ownerIsGame(String owner) {
        return owner.startsWith("net/minecraft/") || owner.startsWith("com/mojang/") || owner.startsWith("net/neoforged/");
    }

    @Override
    public byte[] apply(String className, byte[] bytes) {
        if (bytes == null || className.startsWith("net.neoforged.forgestuck")) return bytes;
        // Quick reject: SRG names always contain "_" followed by digits; avoid parsing unrelated classes.
        String raw = new String(bytes, StandardCharsets.ISO_8859_1);
        if (!raw.contains("m_") && !raw.contains("f_") && !raw.contains("c_")
                && !raw.contains("MobEffectInstance")
                && !raw.contains("net/minecraft/world/level/block/StairBlock")
                && !raw.contains("net/minecraft/world/level/block/DoorBlock")
                && !raw.contains("net/minecraft/world/level/block/TrapDoorBlock")
                && !raw.contains("net/minecraft/world/level/block/FenceGateBlock")
                && !raw.contains("net/minecraft/world/level/block/StandingSignBlock")
                && !raw.contains("net/minecraft/world/level/block/WallSignBlock")
                && !raw.contains("net/minecraft/world/level/block/CeilingHangingSignBlock")
                && !raw.contains("net/minecraft/world/level/block/WallHangingSignBlock")
                && !raw.contains("net/minecraft/world/level/block/ButtonBlock")
                && !raw.contains("net/minecraft/world/level/block/PressurePlateBlock")
                && !raw.contains("net/minecraft/world/level/block/LiquidBlock")
                && !raw.contains(NEOFORGE_FLUID_TANK)
                && !raw.contains("net/minecraft/world/level/block/FungusBlock")
                && !raw.contains("m_135832_")
                && !raw.contains(LEGACY_FIREWORK_SHAPE)
                && !raw.contains(LEGACY_POTION_BREWING)
                && !raw.contains(TOOL_STACK_ITEM_PREDICATE)
                && !raw.contains(ITEM_PREDICATE + ".register")
                && !raw.contains(LOADABLE_INGREDIENT_SERIALIZER)
                && !raw.contains(MODIFIABLE_ARMOR_MATERIAL)
                && !raw.contains("net/neoforged/forgestuck/compat/LegacyArmorMaterial")
                && !raw.contains(COFH_STEM_BLOCK)
                && !raw.contains(COFH_ATTACHED_STEM_BLOCK)
                && !className.equals("slimeknights.tconstruct.smeltery.menu.HeatingStructureContainerMenu")
                && !raw.contains("net/minecraft/world/item/BucketItem")
                && !raw.contains("net/minecraft/world/level/block/Blocks")
                && !raw.contains("net/minecraft/world/entity/ai/attributes/AttributeModifier")
                && !raw.contains("net/minecraft/world/item/ArmorItem$Type")
                && !raw.contains("MISSING_MODEL_LOCATION")
                && !raw.contains("com/mojang/serialization/DataResult")
                && !raw.contains("RenderGuiOverlayEvent$Post")
                && !raw.contains("net/neoforged/neoforge/client/model/geometry/IUnbakedGeometry")
                && !raw.contains("CauldronInteraction")
                && !raw.contains("net/minecraft/world/level/block/AmethystClusterBlock")
                && !raw.contains("net/minecraft/world/entity/ai/attributes/AttributeSupplier$Builder")
                && !raw.contains(LEGACY_PROJECTILE_DISPENSE_BEHAVIOR)
                && !raw.contains(LEGACY_VANISHABLE)
                && !raw.contains(THERMAL_CLIENT_CONFIG)
                && !raw.contains(LEGACY_SPRITE_SOURCES)
                && !raw.contains("net/minecraft/client/renderer/block/model/BlockElementFace")
                && !raw.contains(BLOCK_MODEL)
                && !raw.contains(FACE_BAKERY)
                && !raw.contains(TEXTURE_ATLAS_SPRITE)
                && !raw.contains(VERTEX_FORMAT)
                && !raw.contains("net/minecraft/world/phys/AABB")
                && !raw.contains("m_6227_")
                && !raw.contains("DyeableLeatherItem")
                && !raw.contains("net/neoforged/neoforge/client/model/geometry/SimpleUnbakedGeometry")
                && !raw.contains("net/minecraft/world/item/crafting/CraftingBookCategory")
                && !raw.contains("net/minecraft/core/Holder")
                && !raw.contains(ENCHANTMENT_INSTANCE)
                && !raw.contains("getShareTag")
                && !raw.contains("writeNbt")
                && !raw.contains("getOrCreateTagElement")
                && !raw.contains("getTagElement")
                && !raw.contains("m_41737_")
                && !raw.contains("m_41714_")
                && !raw.contains("setHoverName")
                && !raw.contains(PATCHOULI_VARIABLE_PROVIDER)
                && !raw.contains("containsTag")
                && !raw.contains("isVanilla")
                && !raw.contains("m_21023_")
                && !raw.contains("hasEffect(Lnet/minecraft/world/effect/MobEffect;)Z")
                && !raw.contains("removeEffect")
                && !raw.contains("Lnet/minecraft/world/effect/MobEffect;)Lnet/minecraft/world/effect/MobEffectInstance;")
                && !raw.contains("Lnet/minecraft/world/entity/ai/attributes/Attribute;)D")
                && !raw.contains("getArmorSlots")
                && !raw.contains("m_6168_")
                && !raw.contains(LEGACY_TCONSTRUCT_EQUIPMENT_CONTEXT)
                && !raw.contains(LEGACY_TCONSTRUCT_MOB_EQUIPMENT)
                && !raw.contains(LEGACY_TCONSTRUCT_TINTED_VERTEX_BUILDER)) {
            return bytes;
        }

        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        boolean adaptLegacyCriterion = isLegacyCriterionContextClass(node);
        if (adaptLegacyCriterion) {
            rewriteLegacyCriterionContextTypes(node);
        }
        boolean isMixin = hasAnnotation(node.invisibleAnnotations, MixinClassSupport.MIXIN)
                || hasAnnotation(node.visibleAnnotations, MixinClassSupport.MIXIN);

        Set<String> remapped = new TreeSet<>();
        boolean remapProjectileDispenseBehavior = raw.contains(LEGACY_PROJECTILE_DISPENSE_BEHAVIOR);
        boolean remapFireworkShape = raw.contains(LEGACY_FIREWORK_SHAPE);
        boolean remapLegacyPotionMix = raw.contains(LEGACY_POTION_MIX);
        boolean expandEquipmentContextSlots = rewriteLegacyTConstructEquipmentContextArrays(node);
        boolean rewriteTConstructSpawnCalls = rewriteLegacyTConstructSpawnCalls(node);
        boolean rewritePatchouliVariableLookups = rewriteLegacyPatchouliVariableLookups(node);
        boolean modified = adaptLegacyCriterion || remapProjectileDispenseBehavior
                || remapFireworkShape || remapLegacyPotionMix || expandEquipmentContextSlots
                || rewriteTConstructSpawnCalls || rewritePatchouliVariableLookups;
        modified |= addLegacyBlockEntityNbtBridges(node, remapped);
        if (adaptLegacyCriterion) {
            remapped.add("removed criterion serialization contexts -> Object adapter");
        }
        if (remapProjectileDispenseBehavior) {
            remapped.add("AbstractProjectileDispenseBehavior -> LegacyProjectileDispenseBehavior");
        }
        if (remapFireworkShape) {
            remapped.add("FireworkRocketItem.Shape -> FireworkExplosion.Shape");
        }
        if (remapLegacyPotionMix) {
            remapped.add("PotionBrewing.Mix -> legacy brewing registration queue");
        }
        if (expandEquipmentContextSlots) {
            remapped.add("TConstruct EquipmentContext arrays expanded for 1.21 equipment slots");
        }
        if (rewriteTConstructSpawnCalls) {
            remapped.add("TConstruct mob equipment spawn calls adapted to 1.21.1");
        }
        if (rewritePatchouliVariableLookups) {
            remapped.add("Patchouli IVariableProvider.get adapted to registry-aware API");
        }
        if (node.interfaces.remove(LEGACY_VANISHABLE)) {
            modified = true;
            remapped.add("removed unavailable Vanishable marker interface");
        }
        boolean addLegacyStemSeedDrop = false;
        Set<String> ownRenamed = new HashSet<>();
        Set<String> unresolved = new TreeSet<>();

        if (TOOL_STACK_ITEM_PREDICATE.equals(node.name) && ITEM_PREDICATE.equals(node.superName)) {
            node.superName = "java/lang/Object";
            modified = true;
            remapped.add("ToolStackItemPredicate inheritance -> ItemPredicate subpredicate adapter");
            for (MethodNode method : node.methods) {
                if (!method.name.equals("<init>")) continue;
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction instanceof MethodInsnNode call
                            && call.getOpcode() == org.objectweb.asm.Opcodes.INVOKESPECIAL
                            && ITEM_PREDICATE.equals(call.owner) && call.name.equals("<init>")
                            && call.desc.equals("()V")) {
                        call.owner = "java/lang/Object";
                    }
                }
            }
        }

        if (isMixin) {
            for (MethodNode m : node.methods) {
                String mapped = LegacyNameMappings.looksLikeSrg(m.name) && isShadow(m.invisibleAnnotations, m.visibleAnnotations)
                        ? mappings.map(node.name, m.name, m.desc) : null;
                if (mapped != null) {
                    remapped.add(m.name + "->" + mapped);
                    ownRenamed.add("M" + m.name + m.desc + "=" + mapped);
                    m.name = mapped;
                    modified = true;
                }
            }
            for (FieldNode f : node.fields) {
                String mapped = LegacyNameMappings.looksLikeSrg(f.name) && isShadow(f.invisibleAnnotations, f.visibleAnnotations)
                        ? mappings.map(node.name, f.name, f.desc) : null;
                if (mapped != null) {
                    remapped.add(f.name + "->" + mapped);
                    ownRenamed.add("F" + f.name + "=" + mapped);
                    f.name = mapped;
                    modified = true;
                }
            }
            modified |= remapAnnotations(node.invisibleAnnotations, remapped);
            modified |= remapAnnotations(node.visibleAnnotations, remapped);
            for (MethodNode m : node.methods) {
                modified |= remapAnnotations(m.invisibleAnnotations, remapped);
                modified |= remapAnnotations(m.visibleAnnotations, remapped);
            }
            for (FieldNode f : node.fields) {
                modified |= remapAnnotations(f.invisibleAnnotations, remapped);
                modified |= remapAnnotations(f.visibleAnnotations, remapped);
            }
        }

        if (!isMixin) {
            modified |= rewriteLegacyTConstructTintedVertexBuilder(node, remapped);
            modified |= addOverrideBridges(node, remapped);
            modified |= addLegacyBlockUseBridges(node, remapped);
            modified |= addLegacyUnbakedModelBakeBridge(node, remapped);
            modified |= addLegacySimpleCraftingRecipeConstructorBridge(node, remapped);
            modified |= addLegacyCriterionCodecBridge(node, remapped);
            modified |= addLegacyGeometryBakeBridge(node, remapped);
            modified |= addLegacySimpleGeometryQuadsBridge(node, remapped);
        }

        for (MethodNode m : node.methods) {
            java.util.Map<AbstractInsnNode, String> unmapped = new java.util.LinkedHashMap<>();
            if (rewriteLegacyMantleItemStackShareTag(node, m)) {
                modified = true;
                remapped.add("Mantle ItemStackLoadable NBT share tags -> component patches");
            }
            if (rewriteLegacyCoFHTranslucentParticleRendering(node, m)) {
                modified = true;
                remapped.add("CoFH particle rendering -> ParticleRenderType.begin builder and BufferUploader draw");
            }
            if (rewriteLegacyTesselatorBufferLifecycle(m)) {
                modified = true;
                remapped.add("Legacy Tesselator buffer lifecycle -> Tesselator.begin and MeshData draw");
            }
            if (rewriteThermalClientConfigDefault(node.name, m)) {
                modified = true;
                remapped.add("ThermalClientConfig festive vanilla mobs default supplier");
            }
            if (rewriteChargedSoilProtectedTick(node, m)) {
                modified = true;
                remapped.add("ChargedSoilBlock.charge -> protected Block.tick receiver cast");
            }
            if (rewriteLegacyMobEffectInstanceConstructor(m)) {
                modified = true;
                remapped.add("MobEffectInstance(MobEffect, int, int) -> LegacyMobEffectInstance.create");
            }
            if (rewriteLegacyStairBlockConstructor(m)) {
                modified = true;
                remapped.add("StairBlock(Supplier<BlockState>, Properties) -> StairBlock(BlockState, Properties)");
            }
            if (rewriteLegacyLiquidBlockConstructor(m)) {
                modified = true;
                remapped.add("LiquidBlock(Supplier<FlowingFluid>, Properties) -> LiquidBlock(FlowingFluid, Properties)");
            }
            if (rewriteLegacyAmethystClusterConstructor(m)) {
                modified = true;
                remapped.add("AmethystClusterBlock(int, int, Properties) -> AmethystClusterBlock(float, float, Properties)");
            }
            if (rewriteLegacyWoodBlockConstructors(m)) {
                modified = true;
                remapped.add("Legacy wooden block constructor argument adaptation");
            }
            if (rewriteLegacyFungusBlockConstructor(m)) {
                modified = true;
                remapped.add("FungusBlock(Properties, ResourceKey, Block) -> FungusBlock(ResourceKey, Block, Properties)");
            }
            if (rewriteLegacyArmorMaterialFactory(node.name, m)) {
                modified = true;
                remapped.add("ModifiableArmorMaterial.create -> array size follows ArmorItem.Type values");
            }
            if (rewriteLegacyCoFHStemConstructor(node.name, m)) {
                modified = true;
                addLegacyStemSeedDrop = true;
                remapped.add("StemBlockCoFH legacy constructor -> registry-key StemBlock constructor");
            }
            if (rewriteLegacyCoFHAttachedStemConstructor(node.name, m)) {
                modified = true;
                remapped.add("AttachedStemBlockCoFH legacy constructor -> registry-key AttachedStemBlock constructor");
            }
            if (rewriteLegacyBucketItemConstructor(m)) {
                modified = true;
                remapped.add("BucketItem(Supplier<Fluid>, Properties) -> BucketItem(Fluid, Properties)");
            }
            if (rewriteLegacyBlocksLogFactory(m)) {
                modified = true;
                remapped.add("Blocks.log(MapColor, MapColor) -> LegacyBlockFactoryShim.log");
            }
            if (rewriteLegacyBlocksLeavesFactory(m)) {
                modified = true;
                remapped.add("Blocks.leaves(SoundType) -> LegacyBlockFactoryShim.leaves");
            }
            if (rewriteLegacyBlocksButtonFactory(m)) {
                modified = true;
                remapped.add("Blocks.buttonBlock(BlockSetType, FeatureFlag...) -> LegacyBlockFactoryShim.button");
            }
            if (rewriteLegacyAttributeModifierConstructor(m)) {
                modified = true;
                remapped.add("AttributeModifier(UUID, String, double, Operation) -> ResourceLocation constructor");
            }
            if (rewriteLegacyAttributeSupplierBuilder(m)) {
                modified = true;
                remapped.add("AttributeSupplier.Builder.add(Attribute, ...) -> add(Holder<Attribute>, ...)");
            }
            if (rewriteTConstructHeatingMenuColumnCount(node, m)) {
                modified = true;
                remapped.add("TConstruct smeltery menu column count -> minimum 1");
            }
            if (syncTConstructStructureBeforeMenuOpen(node, m)) {
                modified = true;
                remapped.add("TConstruct heating structure dimensions -> clients before menu data sync");
            }
            if (removeUnsupportedTConstructProtectionSlotMutation(node, m)) {
                modified = true;
                remapped.add("data-driven protection enchantment slot defaults");
                continue;
            }
            for (AbstractInsnNode insn : m.instructions.toArray()) {
                if (insn instanceof FieldInsnNode field && rewriteLegacyVertexFormatElementField(m, field)) {
                    modified = true;
                    remapped.add("DefaultVertexFormat element field -> VertexFormatElement");
                } else if (insn instanceof MethodInsnNode call && rewriteLegacyIdMapWrite(call)) {
                    modified = true;
                    remapped.add("FriendlyByteBuf.m_236818_ -> LegacyFriendlyByteBuf.writeId");
                } else if (insn instanceof MethodInsnNode call && rewriteLegacyFriendlyByteBufItemStack(call)) {
                    modified = true;
                    remapped.add("FriendlyByteBuf legacy ItemStack packet methods -> LegacyFriendlyByteBuf codecs");
                } else if (insn instanceof MethodInsnNode call && rewriteLegacyFriendlyByteBufWriteNbt(call)) {
                    modified = true;
                    remapped.add("FriendlyByteBuf.writeNbt(CompoundTag) -> writeNbt(Tag)");
                } else if (insn instanceof MethodInsnNode call && rewriteLegacyIngredientIsVanilla(call)) {
                    modified = true;
                    remapped.add("Ingredient.isVanilla -> LegacyIngredientBridge.isVanilla");
                } else if (insn instanceof MethodInsnNode call && rewriteLegacyRecipeMatches(call)) {
                    modified = true;
                    remapped.add("Recipe.matches(Container, Level) -> LegacyRecipeManager.matchesLegacy");
                } else if (insn instanceof MethodInsnNode call && isLegacyRecipeId(call)) {
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = LEGACY_RECIPE_MANAGER;
                    call.name = "getRecipeId";
                    call.desc = RECIPE_ID_DESC;
                    call.itf = false;
                    modified = true;
                    remapped.add("Recipe.m_6423_ -> LegacyRecipeManager.getRecipeId");
                } else if (insn instanceof MethodInsnNode call && rewriteLegacyParticleCall(call)) {
                    modified = true;
                    remapped.add("LevelAccessor.addParticle -> ParticleOptions parameter");
                } else if (insn instanceof MethodInsnNode call && rewriteLegacyLiquidBlockGetFluid(call)) {
                    modified = true;
                    remapped.add("LiquidBlock.getFluid -> LegacyLiquidBlock.getFluid");
                } else if (insn instanceof MethodInsnNode call && isLegacyPotionGet(call)) {
                    call.owner = LEGACY_POTION_SHIM;
                    call.name = "getPotion";
                    call.itf = false;
                    modified = true;
                    remapped.add("PotionUtils.getPotion(CompoundTag) -> LegacyPotion.getPotion");
                } else if (insn instanceof MethodInsnNode call && rewriteLegacyBlockPosNbt(call)) {
                    modified = true;
                    remapped.add("NbtUtils legacy BlockPos NBT -> LegacyBlockPosNbt");
                } else if (insn instanceof MethodInsnNode call
                        && rewriteLegacyBlockEntityNbtSuperCall(node.name, m, call)) {
                    modified = true;
                    remapped.add("BlockEntity legacy NBT super hook -> no-op");
                } else if (insn instanceof MethodInsnNode call && rewriteFluidTankSetFluid(call)) {
                    modified = true;
                    remapped.add("FluidTank.setFluid(null) -> normalize null to FluidStack.EMPTY");
                } else if (insn instanceof MethodInsnNode call && isLegacyPotionColor(call)) {
                    call.owner = LEGACY_POTION_SHIM;
                    call.name = "getColor";
                    call.itf = false;
                    modified = true;
                    remapped.add("PotionUtils.getColor(ItemStack) -> LegacyPotion.getColor");
                } else if (insn instanceof MethodInsnNode call
                        && call.owner.equals("net/minecraft/world/item/alchemy/PotionUtils")
                        && call.name.equals("m_43573_")
                        && call.desc.equals(LEGACY_POTION_EFFECTS_DESC)) {
                    call.owner = LEGACY_POTION_SHIM;
                    call.name = "getCustomEffects";
                    call.itf = false;
                    modified = true;
                    remapped.add("PotionUtils.getCustomEffects(CompoundTag) -> LegacyPotion.getCustomEffects");
                } else if (insn instanceof MethodInsnNode call
                        && call.owner.equals("net/minecraft/world/item/alchemy/PotionUtils")
                        && call.name.equals("m_43566_")
                        && call.desc.equals(LEGACY_POTION_EFFECTS_DESC)) {
                    call.owner = LEGACY_POTION_SHIM;
                    call.name = "getAllEffects";
                    call.itf = false;
                    modified = true;
                    remapped.add("PotionUtils.getAllEffects(CompoundTag) -> LegacyPotion.getAllEffects");
                } else if (insn instanceof MethodInsnNode call
                        && call.owner.equals("net/minecraft/world/item/alchemy/Potion")
                        && call.name.equals("m_43492_")
                        && call.desc.equals(LEGACY_POTION_NAME_DESC)) {
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = LEGACY_POTION_SHIM;
                    call.name = "getName";
                    call.desc = "(Lnet/minecraft/world/item/alchemy/Potion;" + LEGACY_POTION_NAME_DESC
                            .substring(1);
                    call.itf = false;
                    modified = true;
                    remapped.add("Potion.m_43492_ -> LegacyPotion.getName");
                } else if (insn instanceof MethodInsnNode call && isLegacyRecipeResult(call)
                        && !(m.name.equals("getResultItem") && m.desc.equals(RECIPE_RESULT_DESC)
                                && (m.access & Opcodes.ACC_SYNTHETIC) != 0)) {
                    call.name = "getResultItem";
                    call.desc = RECIPE_RESULT_DESC;
                    modified = true;
                    remapped.add("Recipe.getResultItem(RegistryAccess) -> getResultItem(HolderLookup.Provider)");
                } else if (insn instanceof MethodInsnNode call && isLegacyItemEdible(call)) {
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = LEGACY_ITEMSTACK_SHIM;
                    call.name = "isEdible";
                    call.desc = LEGACY_ITEM_EDIBLE_DESC;
                    call.itf = false;
                    modified = true;
                    remapped.add("Item.m_41472_ -> LegacyItemStackShim.isEdible");
                } else if (insn instanceof MethodInsnNode call && isLegacyItemDamageable(call)) {
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = LEGACY_ITEMSTACK_SHIM;
                    call.name = "isDamageable";
                    call.desc = "(L" + ITEM + ";)Z";
                    call.itf = false;
                    modified = true;
                    remapped.add("Item.m_41465_ -> LegacyItemStackShim.isDamageable");
                } else if (insn instanceof MethodInsnNode call && isLegacyRecipeManagerByType(call)) {
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = LEGACY_RECIPE_MANAGER;
                    call.name = "byType";
                    call.desc = LEGACY_RECIPE_MANAGER_BY_TYPE_DESC;
                    call.itf = false;
                    modified = true;
                    remapped.add("RecipeManager.byType -> LegacyRecipeManager.byType");
                } else if (insn instanceof MethodInsnNode call && isLegacyRecipeManagerGetAllRecipesFor(call)) {
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = LEGACY_RECIPE_MANAGER;
                    call.name = "getAllRecipesFor";
                    call.desc = LEGACY_RECIPE_MANAGER_GET_ALL_RECIPES_FOR_DESC;
                    call.itf = false;
                    modified = true;
                    remapped.add("RecipeManager.getAllRecipesFor -> LegacyRecipeManager.getAllRecipesFor");
                } else if (insn instanceof MethodInsnNode call && isLegacyRecipeManagerGetRecipesFor(call)) {
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = LEGACY_RECIPE_MANAGER;
                    call.name = "getRecipesFor";
                    call.desc = LEGACY_RECIPE_MANAGER_GET_RECIPES_FOR_DESC;
                    call.itf = false;
                    modified = true;
                    remapped.add("RecipeManager.getRecipesFor -> LegacyRecipeManager.getRecipesFor");
                } else if (insn instanceof MethodInsnNode call && isLegacyRecipeManagerGetRecipeFor(call)) {
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = LEGACY_RECIPE_MANAGER;
                    call.name = "getRecipeFor";
                    call.desc = LEGACY_RECIPE_MANAGER_GET_RECIPE_FOR_DESC;
                    call.itf = false;
                    modified = true;
                    remapped.add("RecipeManager.getRecipeFor -> LegacyRecipeManager.getRecipeFor");
                } else if (insn instanceof MethodInsnNode call && isLegacyDyeableLeatherColor(call)) {
                    call.name = "getColor";
                    modified = true;
                    remapped.add("DyeableLeatherItem.m_41121_ -> getColor");
                } else if (insn instanceof MethodInsnNode call && nodeIsInterfaceVirtualCall(node, call)) {
                    call.setOpcode(org.objectweb.asm.Opcodes.INVOKEINTERFACE);
                    call.itf = true;
                    modified = true;
                    remapped.add("interface self-call INVOKEVIRTUAL -> INVOKEINTERFACE");
                } else if (insn instanceof MethodInsnNode call && isLegacyHolderGet(call)) {
                    call.name = "value";
                    modified = true;
                    remapped.add("Holder.get -> Holder.value");
                } else if (insn instanceof MethodInsnNode call
                        && rewriteLegacyEnchantmentInstanceConstructor(m, call)) {
                    modified = true;
                    remapped.add("EnchantmentInstance(Enchantment, int) -> holder-based factory");
                } else if (insn instanceof MethodInsnNode call && rewriteLegacyGuiOverlayPost(m, call)) {
                    modified = true;
                    remapped.add("legacy RenderGuiOverlayEvent.Post -> RenderGuiLayerEvent.Post");
                } else if (insn instanceof MethodInsnNode call && rewriteLegacyGuiOverlayPartialTick(call)) {
                    modified = true;
                    remapped.add("legacy GUI overlay partial tick -> DeltaTracker value");
                } else if (insn instanceof TypeInsnNode type && rewriteLegacyDebugBufferBuilderAllocation(node, type)) {
                    modified = true;
                    remapped.add("DebugRenderer BufferBuilder allocation -> ByteBufferBuilder");
                } else if (insn instanceof FieldInsnNode field && rewriteLegacyItemStackTagField(m, field)) {
                    modified = true;
                    remapped.add("ItemStack.tag field -> LegacyItemStackShim");
                } else if (insn instanceof MethodInsnNode call
                        && rewriteLegacyCustomRecipeConstructor(m, call)) {
                    modified = true;
                    remapped.add("CustomRecipe(ResourceLocation, CraftingBookCategory) -> CraftingBookCategory");
                } else if (insn instanceof MethodInsnNode call
                       && rewriteLegacyDebugBufferBuilderConstructor(node, call)) {
                   modified = true;
                   remapped.add("DebugRenderer BufferBuilder constructor -> ByteBufferBuilder");
                } else if (insn instanceof MethodInsnNode call
                       && rewriteLegacyDebugBufferBuilderFactory(node, call)) {
                   modified = true;
                   remapped.add("DebugRenderer legacy builder factory -> ByteBufferBuilder");
                } else if (insn instanceof MethodInsnNode call
                       && rewriteLegacyDebugImmediateWithBuffers(node, call)) {
                    modified = true;
                    remapped.add("MultiBufferSource.immediateWithBuffers -> sequenced buffer-source adapter");
                } else if (insn instanceof MethodInsnNode call && markDataResultStaticCallAsInterface(call)) {
                   modified = true;
                   remapped.add("DataResult static call -> interface method reference");
                } else if (insn instanceof FieldInsnNode field && isLegacyMissingModelLocation(field)) {
                    m.instructions.set(field, new MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESTATIC,
                            LEGACY_MODEL_RESOURCE_LOCATIONS, "missingModelLocation",
                            "()Lnet/minecraft/resources/ResourceLocation;", false));
                    modified = true;
                    remapped.add("ModelBakery.MISSING_MODEL_LOCATION -> legacy ResourceLocation");
                } else if (insn instanceof FieldInsnNode field && isLegacyGuiIconsLocation(field)) {
                    m.instructions.set(field, new MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESTATIC,
                            "net/neoforged/forgestuck/compat/LegacyGuiTexture", "vanillaIcons",
                            "()Lnet/minecraft/resources/ResourceLocation;", false));
                    modified = true;
                    remapped.add("Gui.GUI_ICONS_LOCATION -> GUI texture atlas");
                } else if (insn instanceof FieldInsnNode field && isLegacyCauldronMap(field)) {
                    m.instructions.set(field, new MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESTATIC,
                            "net/neoforged/forgestuck/compat/LegacyCauldronInteractions",
                            legacyCauldronMapAccessor(field.name),
                            "()Ljava/util/Map;", false));
                    modified = true;
                    remapped.add("CauldronInteraction." + legacyCauldronMapName(field.name)
                            + " -> InteractionMap.map");
                } else if (insn instanceof FieldInsnNode field && rewriteLegacyMiningEnchantment(node, m, field)) {
                    modified = true;
                    remapped.add("legacy mining enchantment -> ResourceKey");
                } else if (insn instanceof FieldInsnNode field && isLegacyPotionMixList(field)) {
                    m.instructions.set(field, new FieldInsnNode(org.objectweb.asm.Opcodes.GETSTATIC,
                            LEGACY_BREWING_REGISTRY, "LEGACY_MIXES", "Ljava/util/List;"));
                    modified = true;
                    remapped.add("PotionBrewing.POTION_MIXES -> legacy brewing registration queue");
                } else if (insn instanceof MethodInsnNode call && isLegacyPotionAddMix(call)) {
                    call.setOpcode(org.objectweb.asm.Opcodes.INVOKESTATIC);
                    call.owner = LEGACY_BREWING_REGISTRY;
                    call.name = "addPotionMix";
                    call.desc = "(Lnet/minecraft/world/item/alchemy/Potion;Lnet/minecraft/world/item/Item;"
                            + "Lnet/minecraft/world/item/alchemy/Potion;)V";
                    call.itf = false;
                    modified = true;
                    remapped.add("PotionBrewing.addMix -> legacy brewing registration queue");
                } else if (insn instanceof MethodInsnNode call && rewriteLateLootFunctionRegistration(node, m, call)) {
                    modified = true;
                    remapped.add("late loot function registry registration -> earlier RegisterEvent entry");
                } else if (insn instanceof MethodInsnNode call && rewriteLegacyMiningEnchantmentCheck(node, m, call)) {
                    modified = true;
                    remapped.add("legacy mining enchantment check -> registry key lookup");
                } else if (insn instanceof MethodInsnNode call && isLegacyResourceLocationSplit(node, call)) {
                    call.setOpcode(org.objectweb.asm.Opcodes.INVOKESTATIC);
                    call.owner = "net/neoforged/forgestuck/compat/LegacyResourceLocations";
                    call.name = "split";
                    call.itf = false;
                    modified = true;
                    remapped.add("ResourceLocation.decompose -> legacy split helper");
                } else if (insn instanceof MethodInsnNode call && rewriteLegacyComposterAdd(call)) {
                    modified = true;
                    remapped.add("ComposterBlock.add -> legacy compostable map");
                } else if (insn instanceof MethodInsnNode call && rewriteLegacySpriteSourceRegistration(call)) {
                    modified = true;
                    remapped.add("SpriteSources.register(Codec) -> MapCodec registration");
                } else if (insn instanceof MethodInsnNode call && rewriteLegacyExtraCodecsValidate(call)) {
                    modified = true;
                    remapped.add("ExtraCodecs.validate -> Codec.validate");
                } else if (insn instanceof MethodInsnNode call && rewriteLegacyToolStackPredicateCall(call)) {
                    modified = true;
                    remapped.add("ToolStackItemPredicate -> 1.21 ItemSubPredicate");
                } else if (insn instanceof MethodInsnNode call && isLegacyCriteriaTriggerRegister(call)) {
                    call.setOpcode(org.objectweb.asm.Opcodes.INVOKESTATIC);
                    call.owner = LEGACY_CRITERIA_HELPER;
                    call.name = "register";
                    call.itf = false;
                    modified = true;
                    remapped.add("CriteriaTriggers.register -> LegacyCriteriaTriggers.register");
                } else if (insn instanceof MethodInsnNode call && isLegacyDamageSourceCreativePlayer(call)) {
                    call.setOpcode(org.objectweb.asm.Opcodes.INVOKESTATIC);
                    call.owner = "net/neoforged/forgestuck/compat/LegacyDamageSource";
                    call.name = "isCreativePlayer";
                    call.desc = "(L" + DAMAGE_SOURCE + ";)Z";
                    call.itf = false;
                    modified = true;
                    remapped.add("DamageSource.m_269014_ -> LegacyDamageSource.isCreativePlayer");
                } else if (insn instanceof MethodInsnNode call && isLegacyMobEffectHasEffect(call)) {
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = LEGACY_MOB_EFFECT;
                    call.name = "hasEffect";
                    call.desc = LEGACY_MOB_EFFECT_HAS_EFFECT_DESC;
                    call.itf = false;
                    modified = true;
                    remapped.add("LivingEntity/Player.hasEffect(MobEffect) -> LegacyMobEffect.hasEffect");
                } else if (insn instanceof MethodInsnNode call && isLegacyMobEffectLookup(call)) {
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = LEGACY_MOB_EFFECT;
                    call.name = "getEffect";
                    call.desc = LEGACY_MOB_EFFECT_GET_EFFECT_DESC;
                    call.itf = false;
                    modified = true;
                    remapped.add("LivingEntity/Player.getEffect(MobEffect) -> LegacyMobEffect.getEffect");
                } else if (insn instanceof MethodInsnNode call && isLegacyMobEffectRemove(call)) {
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = LEGACY_MOB_EFFECT;
                    call.name = "removeEffect";
                    call.desc = LEGACY_MOB_EFFECT_REMOVE_DESC;
                    call.itf = false;
                    modified = true;
                    remapped.add("LivingEntity/Player.removeEffect(MobEffect) -> LegacyMobEffect.removeEffect");
                } else if (insn instanceof MethodInsnNode call && isLegacyAttributeValueLookup(call)) {
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = LEGACY_ATTRIBUTE_LOOKUP;
                    call.name = "getValue";
                    call.desc = LEGACY_ATTRIBUTE_VALUE_DESC;
                    call.itf = false;
                    modified = true;
                    remapped.add("LivingEntity/Player.getAttributeValue(Attribute) -> LegacyAttribute.getValue");
                } else if (insn instanceof MethodInsnNode call && isLegacyEntityArmorSlots(call)) {
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = LEGACY_ENTITY_EQUIPMENT;
                    call.name = "getArmorSlots";
                    call.desc = LEGACY_ENTITY_ARMOR_SLOTS_DESC;
                    call.itf = false;
                    modified = true;
                    remapped.add("Entity.getArmorSlots -> LegacyEntityEquipment.getArmorSlots");
                } else if (insn instanceof MethodInsnNode call && isLegacyHolderContainsTag(call)) {
                    call.name = "is";
                    modified = true;
                    remapped.add("Holder.containsTag -> Holder.is");
                } else if (insn instanceof MethodInsnNode call && isLegacyMobTypeGetter(call)) {
                    call.setOpcode(org.objectweb.asm.Opcodes.INVOKESTATIC);
                    call.owner = LEGACY_MOB_TYPE;
                    call.name = "getMobType";
                    call.desc = "(Lnet/minecraft/world/entity/LivingEntity;)L" + LEGACY_MOB_TYPE + ";";
                    call.itf = false;
                    modified = true;
                    remapped.add("LivingEntity.getMobType -> LegacyMobType.getMobType");
                } else if (insn instanceof MethodInsnNode call && isLegacyArmorTypeValues(call)) {
                    call.owner = LEGACY_ARMOR_TYPE_SHIM;
                    call.name = "values";
                    call.itf = false;
                    modified = true;
                    remapped.add("ArmorItem.Type.values -> LegacyArmorTypeShim.values");
                } else if (insn instanceof MethodInsnNode call && rewriteLegacyArmorItemConstructor(m, call)) {
                    modified = true;
                    remapped.add("ArmorItem(LegacyArmorMaterial) -> Holder<ArmorMaterial>");
                } else if (insn instanceof MethodInsnNode call && rewriteLegacyItemMaxStackSize(call)) {
                    modified = true;
                    remapped.add("Item.m_41459_ -> LegacyItemStackShim.getMaxStackSize");
                } else if (insn instanceof MethodInsnNode call && rewriteLegacyBlockItemData(call)) {
                    modified = true;
                    remapped.add("BlockItem.m_186336_ -> LegacyItemStackShim.getBlockEntityData");
                } else if (insn instanceof MethodInsnNode call && rewriteLegacyDyeColorComponents(call)) {
                    modified = true;
                    remapped.add("DyeColor.m_41068_ -> LegacyDyeColorShim.getTextureDiffuseColors");
                } else if (insn instanceof MethodInsnNode call && rewriteLegacyEnchantmentList(call)) {
                    modified = true;
                    remapped.add("EnchantmentHelper.m_44882_ -> LegacyEnchantmentShim.deserializeEnchantments");
                } else if (insn instanceof MethodInsnNode call && rewriteLegacyItemStackSave(call)) {
                    modified = true;
                    remapped.add("ItemStack.save(CompoundTag) -> LegacyItemStackShim.save");
                } else if (insn instanceof MethodInsnNode call && rewriteLegacyItemStackParse(call)) {
                    modified = true;
                    remapped.add("ItemStack.of(CompoundTag) -> LegacyItemStackShim.parse");
                } else if (insn instanceof MethodInsnNode call && isLegacyItemStackGetTagElement(call)) {
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = LEGACY_ITEMSTACK_SHIM;
                    call.name = "getTagElement";
                    call.desc = "(L" + ITEMSTACK + ";Ljava/lang/String;)Lnet/minecraft/nbt/CompoundTag;";
                    call.itf = false;
                    modified = true;
                    remapped.add("ItemStack.getTagElement -> LegacyItemStackShim.getTagElement");
                } else if (insn instanceof MethodInsnNode call && isLegacyItemStackGetOrCreateTagElement(call)) {
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = LEGACY_ITEMSTACK_SHIM;
                    call.name = "getOrCreateTagElement";
                    call.desc = "(L" + ITEMSTACK + ";Ljava/lang/String;)Lnet/minecraft/nbt/CompoundTag;";
                    call.itf = false;
                    modified = true;
                    remapped.add("ItemStack.getOrCreateTagElement -> LegacyItemStackShim.getOrCreateTagElement");
                } else if (insn instanceof MethodInsnNode call && isLegacyItemStackSetHoverName(call)) {
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = LEGACY_ITEMSTACK_SHIM;
                    call.name = "setHoverName";
                    call.desc = "(L" + ITEMSTACK + ";Lnet/minecraft/network/chat/Component;)L" + ITEMSTACK + ";";
                    call.itf = false;
                    modified = true;
                    remapped.add("ItemStack.setHoverName -> LegacyItemStackShim.setHoverName");
                } else if (insn instanceof MethodInsnNode call && isLegacyItemStackHasCustomHoverName(call)) {
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = LEGACY_ITEMSTACK_SHIM;
                    call.name = "hasCustomHoverName";
                    call.desc = "(L" + ITEMSTACK + ";)Z";
                    call.itf = false;
                    modified = true;
                    remapped.add("ItemStack.hasCustomHoverName -> LegacyItemStackShim.hasCustomHoverName");
                } else if (insn instanceof MethodInsnNode call && isLegacyItemStackGetTag(call)) {
                    call.setOpcode(org.objectweb.asm.Opcodes.INVOKESTATIC);
                    call.owner = LEGACY_ITEMSTACK_SHIM;
                    call.name = "getTag";
                    call.desc = "(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/nbt/CompoundTag;";
                    call.itf = false;
                    modified = true;
                    remapped.add("ItemStack.getTag -> LegacyItemStackShim.getTag");
                } else if (insn instanceof MethodInsnNode call && isLegacyItemStackGetEnchantmentTags(call)) {
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = LEGACY_ITEMSTACK_SHIM;
                    call.name = "getEnchantmentTags";
                    call.desc = "(L" + ITEMSTACK + ";)Lnet/minecraft/nbt/ListTag;";
                    call.itf = false;
                    modified = true;
                    remapped.add("ItemStack.getEnchantmentTags -> LegacyItemStackShim.getEnchantmentTags");
                } else if (insn instanceof MethodInsnNode call && rewriteLegacyIngredientCodecMethod(call)) {
                    modified = true;
                    remapped.add("Ingredient legacy JSON/network methods -> LegacyIngredientBridge");
                } else if (insn instanceof MethodInsnNode call && isLegacyIngredientArrayMethod(call)) {
                    boolean items = call.name.equals("m_43908_");
                    call.setOpcode(org.objectweb.asm.Opcodes.INVOKESTATIC);
                    call.owner = "net/neoforged/forgestuck/compat/LegacyIngredientBridge";
                    call.name = items ? "getItems" : "getStackingIds";
                    call.desc = items
                            ? "(Lnet/minecraft/world/item/crafting/Ingredient;)[Lnet/minecraft/world/item/ItemStack;"
                            : "(Lnet/minecraft/world/item/crafting/Ingredient;)Lit/unimi/dsi/fastutil/ints/IntList;";
                    call.itf = false;
                    modified = true;
                    remapped.add("Ingredient." + (items ? "getItems" : "getStackingIds")
                            + " -> LegacyIngredientBridge");
                } else if (insn instanceof MethodInsnNode call && isLegacyBlockPropertiesCopy(call)) {
                    call.name = "ofLegacyCopy";
                    modified = true;
                    remapped.add("BlockBehaviour.Properties.copy -> ofLegacyCopy");
                } else if (insn instanceof MethodInsnNode call && isLegacyLoadableIngredientSerialize(call)) {
                    call.setOpcode(org.objectweb.asm.Opcodes.INVOKESTATIC);
                    call.owner = LEGACY_INGREDIENT_BRIDGE;
                    call.name = "serializeLegacy";
                    call.desc = "(Ljava/lang/Object;Ljava/lang/Object;)Lcom/google/gson/JsonObject;";
                    call.itf = false;
                    modified = true;
                    remapped.add("LoadableIngredientSerializer.serialize -> LegacyIngredientBridge.serializeLegacy");
                } else if (insn instanceof MethodInsnNode call && rewriteLegacyVertexConsumerCall(call)) {
                    modified = true;
                    remapped.add("legacy VertexConsumer call -> LegacyVertexConsumerShim");
                } else if (insn instanceof MethodInsnNode call && rewriteLegacyAabbBlockPosConstructor(m, call)) {
                    modified = true;
                    remapped.add("AABB(BlockPos, BlockPos) -> AABB.encapsulatingFullBlocks");
                } else if (insn instanceof MethodInsnNode call && rewriteLegacyVertexFormatConstructor(m, call)) {
                    modified = true;
                    remapped.add("VertexFormat(ImmutableMap) -> LegacyVertexFormatShim.create");
                } else if (insn instanceof MethodInsnNode call && rewriteLegacyVertexFormatElements(call)) {
                    modified = true;
                    remapped.add("VertexFormat.getElements -> LegacyVertexFormatShim.getElements");
                } else if (insn instanceof MethodInsnNode call && rewriteLegacyPostChainConstructor(call)) {
                    modified = true;
                    remapped.add("PostChain(ResourceManager) -> PostChain(ResourceProvider)");
                } else if (insn instanceof MethodInsnNode call && rewriteLegacyTextColorParse(call)) {
                    modified = true;
                    remapped.add("TextColor.parseColor -> LegacyTextColorShim.parseColor");
                } else if (insn instanceof MethodInsnNode call && rewriteLegacyLootDataCall(m.instructions, call)) {
                    modified = true;
                    remapped.add("legacy loot data lookup -> reloadable server registries");
                } else if (insn instanceof MethodInsnNode call
                        && call.owner.equals(FACE_BAKERY)
                        && call.name.equals("m_111581_")
                        && call.desc.equals(LEGACY_FACE_BAKERY_RECOMPUTE_UVS_DESC)) {
                    m.instructions.insertBefore(call, new InsnNode(Opcodes.POP));
                    call.name = "recomputeUVs";
                    call.desc = FACE_BAKERY_RECOMPUTE_UVS_DESC;
                    modified = true;
                    remapped.add("FaceBakery.recomputeUVs -> 1.21 UV-lock helper (model location dropped)");
                } else if (insn instanceof MethodInsnNode call
                        && call.owner.equals(BLOCK_MODEL)
                        && call.name.equals("m_111437_")
                        && call.desc.equals(LEGACY_BLOCK_MODEL_BAKE_FACE_DESC)) {
                    m.instructions.insertBefore(call, new InsnNode(Opcodes.POP));
                    call.name = "bakeFace";
                    call.desc = BLOCK_MODEL_BAKE_FACE_DESC;
                    modified = true;
                    remapped.add("BlockModel.bakeFace -> 1.21 bakeFace (model location dropped)");
                } else if (insn instanceof MethodInsnNode call
                        && call.owner.equals(VERTEX_FORMAT_ELEMENT)
                        && call.name.equals("getElementCount")
                        && call.desc.equals("()I")) {
                    call.name = "count";
                    modified = true;
                    remapped.add("VertexFormatElement.getElementCount -> count");
                } else if (insn instanceof MethodInsnNode call
                        && call.owner.equals("net/minecraft/client/renderer/block/model/BlockElementFace")
                        && call.name.equals("getFaceData")
                        && call.desc.equals("()Lnet/neoforged/neoforge/client/model/ExtraFaceData;")) {
                    call.name = "faceData";
                    modified = true;
                    remapped.add("BlockElementFace.getFaceData -> faceData");
                } else if (insn instanceof MethodInsnNode call && rewriteLegacyTextureAtlasSpriteUv(m, call)) {
                    modified = true;
                    remapped.add("TextureAtlasSprite." + call.name + "(double) -> normalized UV float");
                } else if (insn instanceof MethodInsnNode call && rewriteLegacyUnbakedModelBake(m, call)) {
                    modified = true;
                    remapped.add("UnbakedModel.m_7611_ -> bake without legacy model-location argument");
                } else if (insn instanceof MethodInsnNode call
                        && rewriteLegacyScreenBackgroundCall(node, m, call)) {
                    modified = true;
                    remapped.add(node.name.equals(LEGACY_MANTLE_MULTI_MODULE_SCREEN)
                            ? "Mantle MultiModuleScreen -> avoid duplicate container background rendering"
                            : "Screen.renderBackground(GuiGraphics) -> "
                                    + "renderBackground(GuiGraphics, int, int, float)");
                } else if (insn instanceof MethodInsnNode call && LegacyNameMappings.looksLikeSrg(call.name)) {
                    boolean declaredLegacySuperCall = isBlockStateDefinitionMethod(call)
                            && declaresMethod(call.owner, call.name, call.desc);
                    boolean inheritedItemMethod = isInheritedItemDefaultInstance(call)
                            || isInheritedItemIsFoil(call);
                    String mapped = declaredLegacySuperCall ? null
                            : inheritedItemMethod
                                    ? mappings.map(ITEM, call.name, call.desc)
                                    : mappings.map(call.owner, call.name, call.desc);
                    if (mapped == null && isFluidStateDefinitionMethod(call)) {
                        mapped = mappings.map(FLUID, call.name, call.desc);
                    }
                    if (mapped == null && isBlockFluidStateMethod(call)) {
                        mapped = mappings.map("net/minecraft/world/level/block/Block", call.name, call.desc);
                    }
                    if (mapped == null && isBlockStateDefinitionMethod(call)
                            && !declaresMethod(call.owner, call.name, call.desc)) {
                        mapped = mappings.map("net/minecraft/world/level/block/Block", call.name, call.desc);
                    }
                    if (mapped == null) {
                        if (!isMixin && ownerIsGame(call.owner) && !call.owner.equals(node.name)) {
                            unmapped.put(call, call.owner.replace('/', '.') + "." + call.name + call.desc);
                        }
                        continue;
                    }
                    boolean own = call.owner.equals(node.name)
                            && ownRenamed.contains("M" + call.name + call.desc + "=" + mapped);
                    boolean inherited = call.owner.equals(node.name)
                            && node.methods.stream().noneMatch(declared -> declared.name.equals(call.name)
                                    && declared.desc.equals(call.desc));
                    boolean bridgeDelegate = (m.access & org.objectweb.asm.Opcodes.ACC_SYNTHETIC) != 0
                            && m.name.equals(mapped) && m.desc.equals(call.desc)
                            && call.owner.equals(node.name);
                    if (!bridgeDelegate && (own || ownerIsGame(call.owner) || inherited
                            || isInheritedResourceLocationMethod(call)
                            || isExternalInheritedSrgCall(call)
                            || inheritedItemMethod
                            || isFluidStateDefinitionMethod(call) || isBlockStateDefinitionMethod(call)
                            || isBlockFluidStateMethod(call))) {
                        remapped.add(call.name + "->" + mapped);
                        call.name = mapped;
                        modified = true;
                    }
                } else if (insn instanceof org.objectweb.asm.tree.InvokeDynamicInsnNode indy
                        && rewriteLegacyMapCodecTypeLambda(m, indy)) {
                    modified = true;
                    remapped.add("legacy codec type lambda -> MapCodec adapter");
                } else if (insn instanceof org.objectweb.asm.tree.InvokeDynamicInsnNode indy && indy.bsmArgs != null) {
                    if (rewriteLegacySimpleCraftingRecipeFactory(indy)) {
                        modified = true;
                        remapped.add("SimpleCraftingRecipeSerializer.Factory -> category-only recipe factory");
                    }
                    if (indy.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")
                            && (indy.bsm.getName().equals("metafactory")
                                    || indy.bsm.getName().equals("altMetafactory"))
                            && LegacyNameMappings.looksLikeSrg(indy.name)
                            && indy.bsmArgs.length > 0
                            && indy.bsmArgs[0] instanceof org.objectweb.asm.Type samType
                            && org.objectweb.asm.Type.getReturnType(indy.desc).getSort()
                                    == org.objectweb.asm.Type.OBJECT) {
                        String functionalInterface = org.objectweb.asm.Type.getReturnType(indy.desc).getInternalName();
                        String mapped = mappings.map(functionalInterface, indy.name, samType.getDescriptor());
                        if (mapped != null) {
                            remapped.add(indy.name + "->" + mapped);
                            indy.name = mapped;
                            modified = true;
                        }
                    }
                    for (int i = 0; i < indy.bsmArgs.length; i++) {
                        if (indy.bsmArgs[i] instanceof org.objectweb.asm.Handle h && isLegacyRecipeResult(h)) {
                            indy.bsmArgs[i] = currentRecipeResultHandle(h);
                            modified = true;
                            remapped.add("Recipe.getResultItem method handle uses HolderLookup.Provider");
                        } else if (indy.bsmArgs[i] instanceof org.objectweb.asm.Handle h && isLegacyItemEdible(h)) {
                            indy.bsmArgs[i] = legacyItemEdibleHandle();
                            modified = true;
                            remapped.add("Item.m_41472_ method handle -> LegacyItemStackShim.isEdible");
                        } else if (indy.bsmArgs[i] instanceof org.objectweb.asm.Handle h
                                && isLegacyRecipeManagerByType(h)) {
                            indy.bsmArgs[i] = legacyRecipeManagerByTypeHandle(h);
                            modified = true;
                            remapped.add("RecipeManager.byType method handle -> LegacyRecipeManager.byType");
                        } else if (indy.bsmArgs[i] instanceof org.objectweb.asm.Handle h
                                && isLegacyRecipeManagerGetAllRecipesFor(h)) {
                            indy.bsmArgs[i] = legacyRecipeManagerGetAllRecipesForHandle();
                            modified = true;
                            remapped.add("RecipeManager.getAllRecipesFor method handle -> "
                                    + "LegacyRecipeManager.getAllRecipesFor");
                        } else if (indy.bsmArgs[i] instanceof org.objectweb.asm.Handle h
                                && isLegacyRecipeManagerGetRecipesFor(h)) {
                            indy.bsmArgs[i] = legacyRecipeManagerGetRecipesForHandle();
                            modified = true;
                            remapped.add("RecipeManager.getRecipesFor method handle -> LegacyRecipeManager.getRecipesFor");
                        } else if (indy.bsmArgs[i] instanceof org.objectweb.asm.Handle h
                                && isLegacyRecipeManagerGetRecipeFor(h)) {
                            indy.bsmArgs[i] = legacyRecipeManagerGetRecipeForHandle();
                            modified = true;
                            remapped.add("RecipeManager.getRecipeFor method handle -> LegacyRecipeManager.getRecipeFor");
                        } else if (indy.bsmArgs[i] instanceof org.objectweb.asm.Handle h
                                && isLegacyRecipeId(h)) {
                            indy.bsmArgs[i] = legacyRecipeIdHandle();
                            modified = true;
                            remapped.add("Recipe.m_6423_ method handle -> LegacyRecipeManager.getRecipeId");
                        } else if (indy.bsmArgs[i] instanceof org.objectweb.asm.Handle h
                                && isLegacyPotionGet(h)) {
                            indy.bsmArgs[i] = legacyPotionGetHandle();
                            modified = true;
                            remapped.add("PotionUtils.getPotion method handle -> LegacyPotion.getPotion");
                        } else if (indy.bsmArgs[i] instanceof org.objectweb.asm.Handle h
                                && isLegacyPotionColor(h)) {
                            indy.bsmArgs[i] = legacyPotionColorHandle();
                            modified = true;
                            remapped.add("PotionUtils.getColor method handle -> LegacyPotion.getColor");
                        } else if (indy.bsmArgs[i] instanceof org.objectweb.asm.Handle h
                                && isLegacyDyeableLeatherColor(h)) {
                            indy.bsmArgs[i] = new org.objectweb.asm.Handle(h.getTag(), h.getOwner(), "getColor",
                                    h.getDesc(), h.isInterface());
                            modified = true;
                            remapped.add("DyeableLeatherItem.m_41121_ method handle -> getColor");
                        } else if (indy.bsmArgs[i] instanceof org.objectweb.asm.Handle h && isLegacyHolderGet(h)) {
                            indy.bsmArgs[i] = new org.objectweb.asm.Handle(h.getTag(), h.getOwner(), "value",
                                    h.getDesc(), h.isInterface());
                            modified = true;
                            remapped.add("Holder.get method handle -> Holder.value");
                        } else if (indy.bsmArgs[i] instanceof org.objectweb.asm.Handle h && isLegacyItemStackGetTag(h)) {
                            indy.bsmArgs[i] = legacyItemStackGetTagHandle();
                            modified = true;
                            remapped.add("ItemStack.getTag handle -> LegacyItemStackShim.getTag");
                        } else if (indy.bsmArgs[i] instanceof org.objectweb.asm.Handle h
                                && isLegacyItemStackHasCustomHoverName(h)) {
                            indy.bsmArgs[i] = legacyItemStackHasCustomHoverNameHandle();
                            modified = true;
                            remapped.add("ItemStack.hasCustomHoverName handle -> LegacyItemStackShim");
                        } else if (indy.bsmArgs[i] instanceof org.objectweb.asm.Handle h
                                && isLegacyItemStackGetEnchantmentTags(h)) {
                            indy.bsmArgs[i] = legacyItemStackGetEnchantmentTagsHandle();
                            modified = true;
                            remapped.add("ItemStack.getEnchantmentTags handle -> LegacyItemStackShim");
                        } else if (indy.bsmArgs[i] instanceof org.objectweb.asm.Handle h
                                && legacyItemStackTagFieldHandle(h) != null) {
                            indy.bsmArgs[i] = legacyItemStackTagFieldHandle(h);
                            modified = true;
                            remapped.add("ItemStack.tag field handle -> LegacyItemStackShim");
                        } else if (indy.bsmArgs[i] instanceof org.objectweb.asm.Handle h
                                && legacyVertexConsumerShimHandle(h) != null) {
                            indy.bsmArgs[i] = legacyVertexConsumerShimHandle(h);
                            modified = true;
                            remapped.add("legacy VertexConsumer method handle -> LegacyVertexConsumerShim");
                        } else if (indy.bsmArgs[i] instanceof org.objectweb.asm.Handle h
                                && legacyVertexFormatElementHandle(h) != null) {
                            indy.bsmArgs[i] = legacyVertexFormatElementHandle(h);
                            modified = true;
                            remapped.add("DefaultVertexFormat element field handle -> VertexFormatElement");
                        } else if (indy.bsmArgs[i] instanceof org.objectweb.asm.Handle h
                                && legacyVertexFormatConstructorHandle(h) != null) {
                            indy.bsmArgs[i] = legacyVertexFormatConstructorHandle(h);
                            modified = true;
                            remapped.add("VertexFormat constructor handle -> LegacyVertexFormatShim.create");
                        } else if (indy.bsmArgs[i] instanceof org.objectweb.asm.Handle h
                                && legacyAabbBlockPosConstructorHandle(h) != null) {
                            indy.bsmArgs[i] = legacyAabbBlockPosConstructorHandle(h);
                            modified = true;
                            remapped.add("AABB constructor handle -> AABB.encapsulatingFullBlocks");
                        } else if (indy.bsmArgs[i] instanceof org.objectweb.asm.Handle h
                                && isLegacyDamageSourceCreativePlayer(h)) {
                            indy.bsmArgs[i] = legacyDamageSourceCreativePlayerHandle();
                            modified = true;
                            remapped.add("DamageSource.m_269014_ handle -> LegacyDamageSource.isCreativePlayer");
                        } else if (indy.bsmArgs[i] instanceof org.objectweb.asm.Handle h && ownerIsGame(h.getOwner())
                                && LegacyNameMappings.looksLikeSrg(h.getName())) {
                            String mapped = mappings.map(h.getOwner(), h.getName(), h.getDesc());
                            if (mapped == null) continue;
                            remapped.add(h.getName() + "->" + mapped);
                            indy.bsmArgs[i] = new org.objectweb.asm.Handle(h.getTag(), h.getOwner(), mapped, h.getDesc(),
                                    h.isInterface());
                            modified = true;
                        }
                    }
                } else if (insn instanceof org.objectweb.asm.tree.LdcInsnNode ldc
                        && ldc.cst instanceof org.objectweb.asm.Handle h && isLegacyRecipeResult(h)) {
                    ldc.cst = currentRecipeResultHandle(h);
                    modified = true;
                    remapped.add("Recipe.getResultItem method handle uses HolderLookup.Provider");
                } else if (insn instanceof org.objectweb.asm.tree.LdcInsnNode ldc
                        && ldc.cst instanceof org.objectweb.asm.Handle h && isLegacyItemEdible(h)) {
                    ldc.cst = legacyItemEdibleHandle();
                    modified = true;
                    remapped.add("Item.m_41472_ method handle -> LegacyItemStackShim.isEdible");
                } else if (insn instanceof org.objectweb.asm.tree.LdcInsnNode ldc
                        && ldc.cst instanceof org.objectweb.asm.Handle h && isLegacyRecipeManagerByType(h)) {
                    ldc.cst = legacyRecipeManagerByTypeHandle(h);
                    modified = true;
                    remapped.add("RecipeManager.byType method handle -> LegacyRecipeManager.byType");
                } else if (insn instanceof org.objectweb.asm.tree.LdcInsnNode ldc
                        && ldc.cst instanceof org.objectweb.asm.Handle h && isLegacyRecipeManagerGetAllRecipesFor(h)) {
                    ldc.cst = legacyRecipeManagerGetAllRecipesForHandle();
                    modified = true;
                    remapped.add("RecipeManager.getAllRecipesFor method handle -> "
                            + "LegacyRecipeManager.getAllRecipesFor");
                } else if (insn instanceof org.objectweb.asm.tree.LdcInsnNode ldc
                        && ldc.cst instanceof org.objectweb.asm.Handle h && isLegacyRecipeManagerGetRecipesFor(h)) {
                    ldc.cst = legacyRecipeManagerGetRecipesForHandle();
                    modified = true;
                    remapped.add("RecipeManager.getRecipesFor method handle -> LegacyRecipeManager.getRecipesFor");
                } else if (insn instanceof org.objectweb.asm.tree.LdcInsnNode ldc
                        && ldc.cst instanceof org.objectweb.asm.Handle h && isLegacyRecipeManagerGetRecipeFor(h)) {
                    ldc.cst = legacyRecipeManagerGetRecipeForHandle();
                    modified = true;
                    remapped.add("RecipeManager.getRecipeFor method handle -> LegacyRecipeManager.getRecipeFor");
                } else if (insn instanceof org.objectweb.asm.tree.LdcInsnNode ldc
                        && ldc.cst instanceof org.objectweb.asm.Handle h && isLegacyRecipeId(h)) {
                    ldc.cst = legacyRecipeIdHandle();
                    modified = true;
                    remapped.add("Recipe.m_6423_ method handle -> LegacyRecipeManager.getRecipeId");
                } else if (insn instanceof org.objectweb.asm.tree.LdcInsnNode ldc
                        && ldc.cst instanceof org.objectweb.asm.Handle h && isLegacyPotionGet(h)) {
                    ldc.cst = legacyPotionGetHandle();
                    modified = true;
                    remapped.add("PotionUtils.getPotion method handle -> LegacyPotion.getPotion");
                } else if (insn instanceof org.objectweb.asm.tree.LdcInsnNode ldc
                        && ldc.cst instanceof org.objectweb.asm.Handle h && isLegacyPotionColor(h)) {
                    ldc.cst = legacyPotionColorHandle();
                    modified = true;
                    remapped.add("PotionUtils.getColor method handle -> LegacyPotion.getColor");
                } else if (insn instanceof org.objectweb.asm.tree.LdcInsnNode ldc
                        && ldc.cst instanceof org.objectweb.asm.Handle h && isLegacyDyeableLeatherColor(h)) {
                    ldc.cst = new org.objectweb.asm.Handle(h.getTag(), h.getOwner(), "getColor",
                            h.getDesc(), h.isInterface());
                    modified = true;
                    remapped.add("DyeableLeatherItem.m_41121_ method handle -> getColor");
                } else if (insn instanceof org.objectweb.asm.tree.LdcInsnNode ldc
                        && ldc.cst instanceof org.objectweb.asm.Handle h && isLegacyHolderGet(h)) {
                    ldc.cst = new org.objectweb.asm.Handle(h.getTag(), h.getOwner(), "value",
                            h.getDesc(), h.isInterface());
                    modified = true;
                    remapped.add("Holder.get method handle -> Holder.value");
                } else if (insn instanceof org.objectweb.asm.tree.LdcInsnNode ldc
                        && ldc.cst instanceof org.objectweb.asm.Handle h && isLegacyItemStackGetTag(h)) {
                    ldc.cst = legacyItemStackGetTagHandle();
                    modified = true;
                    remapped.add("ItemStack.getTag handle -> LegacyItemStackShim.getTag");
                } else if (insn instanceof org.objectweb.asm.tree.LdcInsnNode ldc
                        && ldc.cst instanceof org.objectweb.asm.Handle h && isLegacyItemStackHasCustomHoverName(h)) {
                    ldc.cst = legacyItemStackHasCustomHoverNameHandle();
                    modified = true;
                    remapped.add("ItemStack.hasCustomHoverName handle -> LegacyItemStackShim");
                } else if (insn instanceof org.objectweb.asm.tree.LdcInsnNode ldc
                        && ldc.cst instanceof org.objectweb.asm.Handle h && isLegacyItemStackGetEnchantmentTags(h)) {
                    ldc.cst = legacyItemStackGetEnchantmentTagsHandle();
                    modified = true;
                    remapped.add("ItemStack.getEnchantmentTags handle -> LegacyItemStackShim");
                } else if (insn instanceof org.objectweb.asm.tree.LdcInsnNode ldc
                        && ldc.cst instanceof org.objectweb.asm.Handle h
                        && legacyItemStackTagFieldHandle(h) != null) {
                    ldc.cst = legacyItemStackTagFieldHandle(h);
                    modified = true;
                    remapped.add("ItemStack.tag field handle -> LegacyItemStackShim");
                } else if (insn instanceof org.objectweb.asm.tree.LdcInsnNode ldc
                        && ldc.cst instanceof org.objectweb.asm.Handle h
                        && legacyVertexConsumerShimHandle(h) != null) {
                    ldc.cst = legacyVertexConsumerShimHandle(h);
                    modified = true;
                    remapped.add("legacy VertexConsumer method handle -> LegacyVertexConsumerShim");
                } else if (insn instanceof org.objectweb.asm.tree.LdcInsnNode ldc
                        && ldc.cst instanceof org.objectweb.asm.Handle h
                        && legacyVertexFormatElementHandle(h) != null) {
                    ldc.cst = legacyVertexFormatElementHandle(h);
                    modified = true;
                    remapped.add("DefaultVertexFormat element field handle -> VertexFormatElement");
                } else if (insn instanceof org.objectweb.asm.tree.LdcInsnNode ldc
                        && ldc.cst instanceof org.objectweb.asm.Handle h
                        && legacyVertexFormatConstructorHandle(h) != null) {
                    ldc.cst = legacyVertexFormatConstructorHandle(h);
                    modified = true;
                    remapped.add("VertexFormat constructor handle -> LegacyVertexFormatShim.create");
                } else if (insn instanceof org.objectweb.asm.tree.LdcInsnNode ldc
                        && ldc.cst instanceof org.objectweb.asm.Handle h
                        && legacyAabbBlockPosConstructorHandle(h) != null) {
                    ldc.cst = legacyAabbBlockPosConstructorHandle(h);
                    modified = true;
                    remapped.add("AABB constructor handle -> AABB.encapsulatingFullBlocks");
                } else if (insn instanceof org.objectweb.asm.tree.LdcInsnNode ldc
                        && ldc.cst instanceof org.objectweb.asm.Handle h
                        && isLegacyDamageSourceCreativePlayer(h)) {
                    ldc.cst = legacyDamageSourceCreativePlayerHandle();
                    modified = true;
                    remapped.add("DamageSource.m_269014_ handle -> LegacyDamageSource.isCreativePlayer");
                } else if (insn instanceof org.objectweb.asm.tree.LdcInsnNode ldc
                        && ldc.cst instanceof org.objectweb.asm.Handle h
                        && h.getTag() == Opcodes.H_GETFIELD
                        && h.getName().equals("f_40219_")
                        && h.getDesc().equals("I")) {
                    ldc.cst = new org.objectweb.asm.Handle(Opcodes.H_INVOKEVIRTUAL,
                            "net/minecraft/world/inventory/Slot", "getSlotIndex", "()I", false);
                    modified = true;
                    remapped.add("Slot.slot field handle -> Slot.getSlotIndex");
                } else if (insn instanceof FieldInsnNode field
                        && (LegacyNameMappings.looksLikeSrg(field.name)
                                || field.owner.equals("net/minecraft/world/item/crafting/CraftingBookCategory")
                                        && field.desc.equals("Lnet/minecraft/util/StringRepresentable$EnumCodec;"))) {
                    if (field.getOpcode() == Opcodes.GETFIELD
                            && field.name.equals("f_40219_")
                            && field.desc.equals("I")) {
                        m.instructions.set(field, new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                                "net/minecraft/world/inventory/Slot", "getSlotIndex", "()I", false));
                        modified = true;
                        remapped.add("Slot.slot field -> Slot.getSlotIndex");
                        continue;
                    }
                    if (field.getOpcode() == org.objectweb.asm.Opcodes.GETSTATIC
                            && field.desc.equals("Ljava/util/UUID;")
                            && (field.name.equals("f_41374_") || field.name.equals("f_41375_"))) {
                        field.owner = "net/neoforged/forgestuck/compat/LegacyAttributeModifierShim";
                        field.name = field.name.equals("f_41374_")
                                ? "ATTACK_DAMAGE_UUID" : "ATTACK_SPEED_UUID";
                        modified = true;
                        remapped.add("Item." + field.name);
                        continue;
                    }
                    String mobTypeField = legacyMobTypeField(field);
                    if (mobTypeField != null) {
                        field.name = mobTypeField;
                        modified = true;
                        remapped.add("LegacyMobType." + field.name);
                        continue;
                    }
                    if (field.getOpcode() == org.objectweb.asm.Opcodes.GETFIELD
                            && field.name.equals("f_41380_")
                            && field.desc.equals(LEGACY_ITEM_FOOD_PROPERTIES)) {
                        m.instructions.set(field, new MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESTATIC,
                                LEGACY_ITEMSTACK_SHIM, "getFoodProperties",
                                "(Lnet/minecraft/world/item/Item;)" + LEGACY_ITEM_FOOD_PROPERTIES, false));
                        modified = true;
                        remapped.add("Item.foodProperties -> LegacyItemStackShim.getFoodProperties");
                        continue;
                    }
                    if (field.getOpcode() == org.objectweb.asm.Opcodes.GETSTATIC
                            && field.owner.equals("net/minecraft/world/item/alchemy/Potions")
                            && field.name.equals("f_268695_")
                            && field.desc.equals("Lnet/minecraft/resources/ResourceKey;")) {
                        m.instructions.set(field, new MethodInsnNode(Opcodes.INVOKESTATIC,
                                "net/neoforged/forgestuck/compat/LegacyPotion", "emptyKey",
                                "()Lnet/minecraft/resources/ResourceKey;", false));
                        modified = true;
                        remapped.add("Potions.EMPTY key -> LegacyPotion.emptyKey");
                        continue;
                    }
                    if (field.getOpcode() == org.objectweb.asm.Opcodes.GETSTATIC
                            && field.owner.equals("net/minecraft/world/item/alchemy/Potions")
                            && field.name.equals("f_43598_")
                            && field.desc.equals("Lnet/minecraft/world/item/alchemy/Potion;")) {
                        field.name = "WATER";
                        field.desc = "Lnet/minecraft/core/Holder;";
                        InsnList unwrap = new InsnList();
                        unwrap.add(new MethodInsnNode(org.objectweb.asm.Opcodes.INVOKEINTERFACE,
                                "net/minecraft/core/Holder", "value", "()Ljava/lang/Object;", true));
                        unwrap.add(new TypeInsnNode(org.objectweb.asm.Opcodes.CHECKCAST,
                                "net/minecraft/world/item/alchemy/Potion"));
                        m.instructions.insert(field, unwrap);
                        modified = true;
                        remapped.add("Potions.EMPTY -> Potions.WATER.value");
                        continue;
                    }
                    if (field.getOpcode() == org.objectweb.asm.Opcodes.GETFIELD
                            && field.owner.equals("net/minecraft/world/phys/shapes/VoxelShape")
                            && field.name.equals("f_83211_")
                            && field.desc.equals("Lnet/minecraft/world/phys/shapes/DiscreteVoxelShape;")) {
                        m.instructions.set(field, new MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESTATIC,
                                "net/neoforged/forgestuck/compat/LegacyVoxelShapeShim", "getDiscreteShape",
                                "(Lnet/minecraft/world/phys/shapes/VoxelShape;)"
                                        + "Lnet/minecraft/world/phys/shapes/DiscreteVoxelShape;",
                                false));
                        modified = true;
                        remapped.add("VoxelShape.shape -> LegacyVoxelShapeShim.getDiscreteShape");
                        continue;
                    }
                    if (field.owner.equals("net/minecraft/world/item/crafting/CraftingBookCategory")
                            && field.desc.equals("Lnet/minecraft/util/StringRepresentable$EnumCodec;")
                            && field.getOpcode() == org.objectweb.asm.Opcodes.GETSTATIC) {
                        m.instructions.set(field, new MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESTATIC,
                                "net/neoforged/forgestuck/compat/LegacyEnumCodecs",
                                "craftingBookCategoryCodec",
                                "()Lnet/minecraft/util/StringRepresentable$EnumCodec;", false));
                        modified = true;
                        remapped.add("CraftingBookCategory.CODEC -> LegacyEnumCodecs.craftingBookCategoryCodec");
                        continue;
                    }
                    if (field.getOpcode() == org.objectweb.asm.Opcodes.GETFIELD
                            && field.owner.equals("net/minecraft/server/network/ServerGamePacketListenerImpl")
                            && field.name.equals("f_9742_")
                            && field.desc.equals("Lnet/minecraft/network/Connection;")) {
                        m.instructions.set(field, new MethodInsnNode(org.objectweb.asm.Opcodes.INVOKEVIRTUAL,
                                field.owner, "getConnection", "()" + field.desc, false));
                        modified = true;
                        remapped.add("ServerGamePacketListenerImpl.connection -> getConnection()");
                        continue;
                    }
                    if (isLegacyEmptyBlockDropsAssignment(field, node.name)) {
                        AbstractInsnNode value = previousMeaningful(field);
                        AbstractInsnNode receiver = previousMeaningful(value);
                        m.instructions.remove(receiver);
                        m.instructions.remove(value);
                        m.instructions.remove(field);
                        modified = true;
                        remapped.add("Block.drops = BuiltInLootTables.EMPTY -> data-driven empty loot table");
                        continue;
                    }
                    String[] repl = isMixin ? null : REPLACEMENTS.get(field.owner + "." + field.name + field.desc);
                    if (repl != null) {
                        remapped.add(field.name + "->" + repl[0] + "." + repl[1]);
                        field.owner = repl[0];
                        field.name = repl[1];
                        modified = true;
                        continue;
                    }
                    String mapped = mappings.map(field.owner, field.name, field.desc);
                    if (mapped == null) {
                        if (!isMixin && ownerIsGame(field.owner) && !field.owner.equals(node.name)) {
                            unmapped.put(field, field.owner.replace('/', '.') + "." + field.name);
                        }
                        continue;
                    }
                    boolean own = field.owner.equals(node.name) && ownRenamed.contains("F" + field.name + "=" + mapped);
                    boolean inherited = field.owner.equals(node.name)
                            && node.fields.stream().noneMatch(declared -> declared.name.equals(field.name)
                                    && declared.desc.equals(field.desc));
                    if (own || ownerIsGame(field.owner) || inherited
                            || LegacyNameMappings.looksLikeSrg(field.name)) {
                        remapped.add(field.name + "->" + mapped);
                        field.name = mapped;
                        modified = true;
                    }
                }
            }
            for (var e : unmapped.entrySet()) {
                ForgeOwnerRemapStep.failAtCall(m, e.getKey(), e.getValue() + " (legacy SRG name without a 1.21.1 mapping)", node.name + "." + m.name);
                unresolved.add(e.getValue());
                modified = true;
            }
        }

            if (COFH_ARMOR_ITEM.equals(node.name)
                    && node.methods.stream().noneMatch(method -> method.name.equals("m_7167_")
                            && method.desc.equals("(Lnet/minecraft/world/entity/EquipmentSlot;)"
                                    + "Lcom/google/common/collect/Multimap;"))) {
                node.methods.add(legacyCoFHArmorAttributesBridge());
                remapped.add("ArmorItemCoFH legacy attribute method bridge");
                modified = true;
            }

            if (addLegacyStemSeedDrop) addLegacyCoFHStemSeedDrop(node);

        if (!modified) return bytes;
        if (!remapped.isEmpty()) {
            LOGGER.info("{} remapped {} legacy member name(s) in {}: {}", TAG, remapped.size(), className, remapped);
        }
        if (!unresolved.isEmpty()) {
            LOGGER.warn("{} {} references legacy SRG member(s) with no 1.21.1 mapping; they link but throw "
                    + "UnsupportedOperationException if executed: {}", TAG, className, unresolved);
        }
        ClassWriter writer = new ClassWriter(0);
        Remapper aliases = new Remapper() {
            @Override
            public String map(String internalName) {
                if (LEGACY_PROJECTILE_DISPENSE_BEHAVIOR.equals(internalName)) {
                    return LEGACY_PROJECTILE_DISPENSE_SHIM;
                }
                if (LEGACY_FIREWORK_SHAPE.equals(internalName)) return FIREWORK_SHAPE;
                return LEGACY_POTION_MIX.equals(internalName)
                        ? LEGACY_BREWING_REGISTRY + "$LegacyMix" : internalName;
            }
        };
        node.accept(new ClassRemapper(writer, aliases));
        return writer.toByteArray();
    }

    private static org.objectweb.asm.Handle legacyVertexConsumerShimHandle(org.objectweb.asm.Handle handle) {
        if (!VERTEX_CONSUMER.equals(handle.getOwner())) return null;
        String name = switch (handle.getName()) {
            case "m_5483_" -> "vertex";
            case "m_193479_", "m_6122_" -> "color";
            case "m_5601_" -> "normal";
            case "m_7421_" -> "uv";
            case "m_7122_" -> "overlayCoords";
            case "m_7120_" -> "uv2";
            case "m_5752_" -> "endVertex";
            default -> null;
        };
        if (name == null) return null;
        String expectedDescriptor = switch (handle.getName()) {
            case "m_5483_" -> "(DDD)L" + VERTEX_CONSUMER + ";";
            case "m_193479_" -> "(I)L" + VERTEX_CONSUMER + ";";
            case "m_6122_" -> "(IIII)L" + VERTEX_CONSUMER + ";";
            case "m_5601_" -> "(FFF)L" + VERTEX_CONSUMER + ";";
            case "m_7421_" -> "(FF)L" + VERTEX_CONSUMER + ";";
            case "m_7122_" -> "(II)L" + VERTEX_CONSUMER + ";";
            case "m_7120_" -> "(II)L" + VERTEX_CONSUMER + ";";
            case "m_5752_" -> "()V";
            default -> throw new IllegalStateException("Unhandled VertexConsumer method " + handle.getName());
        };
        if (!expectedDescriptor.equals(handle.getDesc())) return null;
        String descriptor = "(L" + VERTEX_CONSUMER + ";" + handle.getDesc().substring(1);
        return new org.objectweb.asm.Handle(org.objectweb.asm.Opcodes.H_INVOKESTATIC,
                LEGACY_VERTEX_CONSUMER_SHIM, name, descriptor, false);
    }

    private static boolean rewriteLegacyVertexConsumerCall(MethodInsnNode call) {
        org.objectweb.asm.Handle shim = legacyVertexConsumerShimHandle(new org.objectweb.asm.Handle(
                org.objectweb.asm.Opcodes.H_INVOKEINTERFACE, call.owner, call.name, call.desc, true));
        if (shim == null) return false;
        call.setOpcode(org.objectweb.asm.Opcodes.INVOKESTATIC);
        call.owner = shim.getOwner();
        call.name = shim.getName();
        call.desc = shim.getDesc();
        call.itf = false;
        return true;
    }

    private static boolean isLegacyMissingModelLocation(FieldInsnNode field) {
        return field.getOpcode() == org.objectweb.asm.Opcodes.GETSTATIC
                && field.owner.equals("net/minecraft/client/resources/model/ModelBakery")
                && (field.name.equals("MISSING_MODEL_LOCATION") || field.name.equals("f_119230_"))
                && field.desc.equals("Lnet/minecraft/client/resources/model/ModelResourceLocation;");
    }

    private static boolean isLegacyGuiIconsLocation(FieldInsnNode field) {
        return field.getOpcode() == org.objectweb.asm.Opcodes.GETSTATIC
                && field.owner.equals("net/minecraft/client/gui/Gui")
                && (field.name.equals("GUI_ICONS_LOCATION") || field.name.equals("f_279580_"))
                && field.desc.equals("Lnet/minecraft/resources/ResourceLocation;");
    }

    private static boolean isLegacyCauldronMap(FieldInsnNode field) {
        return field.getOpcode() == org.objectweb.asm.Opcodes.GETSTATIC
                && field.owner.equals("net/minecraft/core/cauldron/CauldronInteraction")
                && (field.name.equals("f_175606_") || field.name.equals("f_175607_")
                        || field.name.equals("EMPTY") || field.name.equals("WATER"))
                && field.desc.equals("Ljava/util/Map;");
    }

    private static boolean isLegacyPotionMixList(FieldInsnNode field) {
        return field.getOpcode() == org.objectweb.asm.Opcodes.GETSTATIC
                && field.owner.equals(LEGACY_POTION_BREWING)
                && (field.name.equals("f_43494_") || field.name.equals("POTION_MIXES"))
                && field.desc.equals("Ljava/util/List;");
    }

    private static boolean isLegacyPotionAddMix(MethodInsnNode call) {
        return call.getOpcode() == org.objectweb.asm.Opcodes.INVOKESTATIC
                && call.owner.equals(LEGACY_POTION_BREWING)
                && call.name.equals("m_43513_")
                && call.desc.equals("(Lnet/minecraft/world/item/alchemy/Potion;Lnet/minecraft/world/item/Item;"
                        + "Lnet/minecraft/world/item/alchemy/Potion;)V");
    }

    private static boolean rewriteLegacyMiningEnchantment(ClassNode node, MethodNode method, FieldInsnNode field) {
        if (!isLegacyMiningItem(node) || !method.name.equals("setupEnchants")
                || field.getOpcode() != org.objectweb.asm.Opcodes.GETSTATIC
                || !field.owner.equals("net/minecraft/world/item/enchantment/Enchantments")) return false;
        String key = switch (field.name) {
            case "f_44984_" -> "EFFICIENCY";
            case "f_44985_" -> "SILK_TOUCH";
            case "f_44987_" -> "FORTUNE";
            default -> null;
        };
        if (key == null || !field.desc.equals("Lnet/minecraft/world/item/enchantment/Enchantment;")) return false;
        field.name = key;
        field.desc = "Lnet/minecraft/resources/ResourceKey;";
        return true;
    }

    private static boolean rewriteLegacyMiningEnchantmentCheck(
            ClassNode node, MethodNode method, MethodInsnNode call) {
        if (!isLegacyMiningItem(node) || !method.name.equals("canApplyAtEnchantingTable")
                || call.getOpcode() != org.objectweb.asm.Opcodes.INVOKEINTERFACE
                || !call.owner.equals("java/util/Set") || !call.name.equals("contains")
                || !call.desc.equals("(Ljava/lang/Object;)Z")) return false;
        call.setOpcode(org.objectweb.asm.Opcodes.INVOKESTATIC);
        call.owner = "net/neoforged/forgestuck/compat/LegacyEnchantmentRegistry";
        call.name = "contains";
        call.desc = "(Ljava/util/Set;Ljava/lang/Object;)Z";
        call.itf = false;
        return true;
    }

    private static boolean isLegacyMiningItem(ClassNode node) {
        return LEGACY_RF_DRILL.equals(node.name) || LEGACY_RF_SAW.equals(node.name);
    }

    private static boolean rewriteLateLootFunctionRegistration(
            ClassNode node, MethodNode method, MethodInsnNode call) {
        if (!LEGACY_COFH_NBT_SYNC.equals(node.name) || !method.name.equals("setup")
                || call.getOpcode() != org.objectweb.asm.Opcodes.INVOKESTATIC
                || !call.owner.equals("net/minecraft/core/Registry")
                || !call.name.equals("m_122965_")
                || !call.desc.equals("(Lnet/minecraft/core/Registry;Lnet/minecraft/resources/ResourceLocation;"
                        + "Ljava/lang/Object;)Ljava/lang/Object;")) return false;
        call.owner = LEGACY_LOOT_FUNCTION_REGISTRATION;
        call.name = "existing";
        call.desc = "(Lnet/minecraft/core/Registry;Lnet/minecraft/resources/ResourceLocation;"
                + "Ljava/lang/Object;)Ljava/lang/Object;";
        call.itf = false;
        return true;
    }

    private static boolean removeUnsupportedTConstructProtectionSlotMutation(ClassNode node, MethodNode method) {
        if (!LEGACY_TCONSTRUCT_CONFIG.equals(node.name)
                || !(method.name.equals("lambda$new$0") || method.name.equals("lambda$new$1"))
                || !method.desc.equals("()V")) return false;
        boolean enchantmentConstant = false;
        boolean slotValues = false;
        boolean slotAssignment = false;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field
                    && field.getOpcode() == org.objectweb.asm.Opcodes.GETSTATIC
                    && field.owner.equals("net/minecraft/world/item/enchantment/Enchantments")
                    && (field.name.equals("f_44966_") || field.name.equals("f_44968_"))
                    && field.desc.equals("Lnet/minecraft/world/item/enchantment/Enchantment;")) {
                enchantmentConstant = true;
            } else if (instruction instanceof MethodInsnNode call
                    && call.getOpcode() == org.objectweb.asm.Opcodes.INVOKESTATIC
                    && call.owner.equals("net/minecraft/world/entity/EquipmentSlot")
                    && call.name.equals("values")
                    && call.desc.equals("()[Lnet/minecraft/world/entity/EquipmentSlot;")) {
                slotValues = true;
            } else if (instruction instanceof FieldInsnNode field
                    && field.getOpcode() == org.objectweb.asm.Opcodes.PUTFIELD
                    && field.owner.equals("net/minecraft/world/item/enchantment/Enchantment")
                    && field.name.equals("f_44671_")
                    && field.desc.equals("[Lnet/minecraft/world/entity/EquipmentSlot;")) {
                slotAssignment = true;
            }
        }
        if (!enchantmentConstant || !slotValues || !slotAssignment) return false;
        method.instructions.clear();
        method.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.RETURN));
        method.maxStack = 0;
        method.maxLocals = 0;
        return true;
    }

    private static String legacyCauldronMapAccessor(String fieldName) {
        return fieldName.equals("f_175606_") || fieldName.equals("EMPTY") ? "empty" : "water";
    }

    private static String legacyCauldronMapName(String fieldName) {
        return fieldName.equals("f_175606_") || fieldName.equals("EMPTY") ? "EMPTY" : "WATER";
    }

    private static boolean isLegacyResourceLocationSplit(ClassNode node, MethodInsnNode call) {
        return call.getOpcode() == org.objectweb.asm.Opcodes.INVOKESTATIC
                && call.name.equals("m_135832_")
                && call.desc.equals("(Ljava/lang/String;C)[Ljava/lang/String;")
                && (call.owner.equals("net/minecraft/resources/ResourceLocation")
                        || (call.owner.equals(node.name)
                                && node.superName.equals("net/minecraft/resources/ResourceLocation")));
    }

    private static boolean rewriteLegacyDebugBufferBuilderAllocation(ClassNode node, TypeInsnNode type) {
        if (!node.name.equals("cofh/thermal/dynamics/client/DebugRenderer")
                || type.getOpcode() != org.objectweb.asm.Opcodes.NEW
                || !type.desc.equals("com/mojang/blaze3d/vertex/BufferBuilder")) return false;
        type.desc = "com/mojang/blaze3d/vertex/ByteBufferBuilder";
        return true;
    }

    private static boolean rewriteLegacyDebugBufferBuilderConstructor(ClassNode node, MethodInsnNode call) {
        if (!node.name.equals("cofh/thermal/dynamics/client/DebugRenderer")
                || call.getOpcode() != org.objectweb.asm.Opcodes.INVOKESPECIAL
                || !call.owner.equals("com/mojang/blaze3d/vertex/BufferBuilder")
                || !call.name.equals("<init>") || !call.desc.equals("(I)V")) return false;
        call.owner = "com/mojang/blaze3d/vertex/ByteBufferBuilder";
        return true;
    }

    private static boolean rewriteLegacyDebugBufferBuilderFactory(ClassNode node, MethodInsnNode call) {
        if (!node.name.equals("cofh/thermal/dynamics/client/DebugRenderer")
                || call.getOpcode() != org.objectweb.asm.Opcodes.INVOKESTATIC
                || !call.owner.equals("net/neoforged/forgestuck/compat/LegacyBufferBuilders")
                || !call.name.equals("create")
                || !call.desc.equals("(I)Lcom/mojang/blaze3d/vertex/BufferBuilder;")) return false;
        call.owner = "net/neoforged/forgestuck/compat/LegacyMultiBufferSource";
        call.name = "createByteBufferBuilder";
        call.desc = "(I)Lcom/mojang/blaze3d/vertex/ByteBufferBuilder;";
        return true;
    }

    private static boolean rewriteLegacyDebugImmediateWithBuffers(ClassNode node, MethodInsnNode call) {
        if (!node.name.equals("cofh/thermal/dynamics/client/DebugRenderer")
                || call.getOpcode() != org.objectweb.asm.Opcodes.INVOKESTATIC
                || !call.owner.equals("net/minecraft/client/renderer/MultiBufferSource")
                || !call.name.equals("m_109900_")
                || !call.desc.equals("(Ljava/util/Map;Lcom/mojang/blaze3d/vertex/BufferBuilder;)"
                        + "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;")) return false;
        call.owner = "net/neoforged/forgestuck/compat/LegacyMultiBufferSource";
        call.name = "immediateWithBuffers";
        call.desc = "(Ljava/util/Map;Lcom/mojang/blaze3d/vertex/ByteBufferBuilder;)"
                + "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;";
        call.itf = false;
        return true;
    }

    private static boolean markDataResultStaticCallAsInterface(MethodInsnNode call) {
        if (call.getOpcode() != org.objectweb.asm.Opcodes.INVOKESTATIC
                || call.itf
                || !call.owner.equals("com/mojang/serialization/DataResult")) return false;
        call.itf = true;
        return true;
    }

    private static boolean addLegacyGeometryBakeBridge(ClassNode node, Set<String> remapped) {
        String geometry = "net/neoforged/neoforge/client/model/geometry/IUnbakedGeometry";
        if (!node.interfaces.contains(geometry)) return false;
        String context = "net/neoforged/neoforge/client/model/geometry/IGeometryBakingContext";
        String baker = "net/minecraft/client/resources/model/ModelBaker";
        String modelState = "net/minecraft/client/resources/model/ModelState";
        String overrides = "net/minecraft/client/renderer/block/model/ItemOverrides";
        String bakedModel = "net/minecraft/client/resources/model/BakedModel";
        String resourceLocation = "net/minecraft/resources/ResourceLocation";
        String targetDescriptor = "(L" + context + ";L" + baker + ";Ljava/util/function/Function;"
                + "L" + modelState + ";L" + overrides + ";)L" + bakedModel + ";";
        String legacyDescriptor = "(L" + context + ";L" + baker + ";Ljava/util/function/Function;"
                + "L" + modelState + ";L" + overrides + ";L" + resourceLocation + ";)L" + bakedModel + ";";
        boolean hasLegacyBake = node.methods.stream().anyMatch(method -> method.name.equals("bake")
                && method.desc.equals(legacyDescriptor));
        if (!hasLegacyBake || node.methods.stream().anyMatch(method -> method.name.equals("bake")
                && method.desc.equals(targetDescriptor))) return false;

        MethodNode bridge = new MethodNode(org.objectweb.asm.Opcodes.ACC_PUBLIC, "bake", targetDescriptor,
                null, null);
        bridge.instructions.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, 0));
        bridge.instructions.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, 1));
        bridge.instructions.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, 2));
        bridge.instructions.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, 3));
        bridge.instructions.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, 4));
        bridge.instructions.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, 5));
        bridge.instructions.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, 1));
        bridge.instructions.add(new MethodInsnNode(org.objectweb.asm.Opcodes.INVOKEINTERFACE, context,
                "getModelName", "()Ljava/lang/String;", true));
        bridge.instructions.add(new MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESTATIC,
                resourceLocation, "parse", "(Ljava/lang/String;)L" + resourceLocation + ";", false));
        bridge.instructions.add(new MethodInsnNode(org.objectweb.asm.Opcodes.INVOKEVIRTUAL, node.name,
                "bake", legacyDescriptor, false));
        bridge.instructions.add(new InsnNode(org.objectweb.asm.Opcodes.ARETURN));
        bridge.maxStack = 7;
        bridge.maxLocals = 6;
        node.methods.add(bridge);
        remapped.add("IUnbakedGeometry.bake -> legacy model-name bake bridge");
        return true;
    }

    private static boolean addLegacySimpleGeometryQuadsBridge(ClassNode node, Set<String> remapped) {
        String simpleGeometry = "net/neoforged/neoforge/client/model/geometry/SimpleUnbakedGeometry";
        if (!simpleGeometry.equals(node.superName)) return false;
        String context = "net/neoforged/neoforge/client/model/geometry/IGeometryBakingContext";
        String modelBuilder = "net/neoforged/neoforge/client/model/IModelBuilder";
        String baker = "net/minecraft/client/resources/model/ModelBaker";
        String modelState = "net/minecraft/client/resources/model/ModelState";
        String resourceLocation = "net/minecraft/resources/ResourceLocation";
        String targetDescriptor = "(L" + context + ";L" + modelBuilder + ";L" + baker
                + ";Ljava/util/function/Function;L" + modelState + ";)V";
        String legacyDescriptor = "(L" + context + ";L" + modelBuilder + ";L" + baker
                + ";Ljava/util/function/Function;L" + modelState + ";L" + resourceLocation + ";)V";
        boolean hasLegacyMethod = node.methods.stream().anyMatch(method -> method.name.equals("addQuads")
                && method.desc.equals(legacyDescriptor));
        if (!hasLegacyMethod || node.methods.stream().anyMatch(method -> method.name.equals("addQuads")
                && method.desc.equals(targetDescriptor))) return false;

        MethodNode bridge = new MethodNode(Opcodes.ACC_PUBLIC, "addQuads", targetDescriptor, null, null);
        bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        for (int local = 1; local <= 5; local++) {
            bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD, local));
        }
        bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        bridge.instructions.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, context,
                "getModelName", "()Ljava/lang/String;", true));
        bridge.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, resourceLocation,
                "parse", "(Ljava/lang/String;)L" + resourceLocation + ";", false));
        bridge.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, node.name, "addQuads",
                legacyDescriptor, false));
        bridge.instructions.add(new InsnNode(Opcodes.RETURN));
        bridge.maxStack = 7;
        bridge.maxLocals = 6;
        node.methods.add(bridge);
        remapped.add("SimpleUnbakedGeometry.addQuads -> legacy model-name bridge");
        return true;
    }

    private static MethodNode legacyCoFHArmorAttributesBridge() {
        String descriptor = "(Lnet/minecraft/world/entity/EquipmentSlot;)Lcom/google/common/collect/Multimap;";
        MethodNode bridge = new MethodNode(org.objectweb.asm.Opcodes.ACC_PUBLIC, "m_7167_", descriptor,
                null, null);
        bridge.instructions.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, 0));
        bridge.instructions.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, 1));
        bridge.instructions.add(new MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESTATIC,
                "net/neoforged/forgestuck/compat/LegacyArmorAttributeModifiers", "get",
                "(Lnet/minecraft/world/item/Item;Lnet/minecraft/world/entity/EquipmentSlot;)"
                        + "Lcom/google/common/collect/Multimap;", false));
        bridge.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.ARETURN));
        bridge.maxStack = 2;
        bridge.maxLocals = 2;
        return bridge;
    }

    private static boolean rewriteLegacyComposterAdd(MethodInsnNode call) {
        if (call.getOpcode() != org.objectweb.asm.Opcodes.INVOKESTATIC
                || !call.owner.equals("net/minecraft/world/level/block/ComposterBlock")
                || !(call.name.equals("m_51920_") || call.name.equals("add"))
                || !call.desc.equals("(FLnet/minecraft/world/level/ItemLike;)V")) {
            return false;
        }
        call.owner = "net/neoforged/forgestuck/compat/LegacyComposterRegistration";
        call.name = "add";
        call.itf = false;
        return true;
    }

    private static boolean rewriteLegacyGuiOverlayPost(MethodNode method, MethodInsnNode call) {
        boolean legacyPost = call.getOpcode() == org.objectweb.asm.Opcodes.INVOKEINTERFACE
                && call.owner.equals("net/minecraftforge/eventbus/api/IEventBus")
                && call.name.equals("post")
                && call.desc.equals("(Lnet/minecraftforge/eventbus/api/Event;)Z");
        boolean neoForgePost = call.getOpcode() == org.objectweb.asm.Opcodes.INVOKEINTERFACE
                && call.owner.equals("net/neoforged/bus/api/IEventBus")
                && call.name.equals("post")
                && call.desc.equals("(Lnet/neoforged/bus/api/Event;)Z");
        boolean remappedPost = call.getOpcode() == org.objectweb.asm.Opcodes.INVOKESTATIC
                && (call.owner.equals("net/neoforged/forgestuck/gamecompat/LegacyEventBusPost")
                        || call.owner.equals("net/neoforged/forgestuck/compat/LegacyEventBusPost"))
                && call.name.equals("post")
                && call.desc.equals("(Lnet/neoforged/bus/api/IEventBus;Lnet/neoforged/bus/api/Event;)Z");
        if (!legacyPost && !neoForgePost && !remappedPost) {
            return false;
        }
        MethodInsnNode constructor = null;
        for (AbstractInsnNode cursor = call.getPrevious(); cursor != null; cursor = cursor.getPrevious()) {
            if (cursor instanceof MethodInsnNode candidate
                    && candidate.getOpcode() == org.objectweb.asm.Opcodes.INVOKESPECIAL
                    && (candidate.owner.equals("net/minecraftforge/client/event/RenderGuiOverlayEvent$Post")
                            || candidate.owner.equals(
                                    "net/neoforged/neoforge/client/event/RenderGuiLayerEvent$Post"))
                    && candidate.name.equals("<init>")) {
                constructor = candidate;
                break;
            }
        }
        if (constructor == null) return false;
        AbstractInsnNode allocation = constructor.getPrevious();
        while (allocation != null && !(allocation instanceof TypeInsnNode type
                && type.getOpcode() == org.objectweb.asm.Opcodes.NEW
                && (type.desc.equals("net/minecraftforge/client/event/RenderGuiOverlayEvent$Post")
                        || type.desc.equals("net/neoforged/neoforge/client/event/RenderGuiLayerEvent$Post")))) {
            allocation = allocation.getPrevious();
        }
        if (allocation == null) return false;
        AbstractInsnNode eventBus = allocation.getPrevious();
        while (eventBus != null && !(eventBus instanceof FieldInsnNode field
                && field.getOpcode() == org.objectweb.asm.Opcodes.GETSTATIC
                && field.name.equals("EVENT_BUS")
                && (field.owner.equals("net/minecraftforge/common/MinecraftForge")
                        && field.desc.equals("Lnet/minecraftforge/eventbus/api/IEventBus;")
                        || field.owner.equals("net/neoforged/neoforge/common/NeoForge")
                                && field.desc.equals("Lnet/neoforged/bus/api/IEventBus;")))) {
            eventBus = eventBus.getPrevious();
        }
        if (eventBus == null) return false;
        int eventLocal = eventParameterLocal(method,
                "Lnet/minecraftforge/client/event/RenderGuiOverlayEvent$Pre;");
        if (eventLocal < 0) {
            eventLocal = eventParameterLocal(method,
                    "Lnet/neoforged/neoforge/client/event/RenderGuiLayerEvent$Pre;");
        }
        if (eventLocal < 0) return false;

        InsnList replacement = new InsnList();
        replacement.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, eventLocal));
        replacement.add(new MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESTATIC,
                "net/neoforged/forgestuck/compat/LegacyGuiOverlayEvent", "postAfterHealthbar",
                "(Lnet/neoforged/neoforge/client/event/RenderGuiLayerEvent$Pre;)Z", false));
        method.instructions.insertBefore(eventBus, replacement);
        for (AbstractInsnNode cursor = eventBus; cursor != null;) {
            AbstractInsnNode next = cursor.getNext();
            method.instructions.remove(cursor);
            if (cursor == call) break;
            cursor = next;
        }
        return true;
    }

    private static boolean rewriteLegacyGuiOverlayPartialTick(MethodInsnNode call) {
        if (!(call.owner.equals("net/neoforged/neoforge/client/event/RenderGuiLayerEvent$Post")
                        || call.owner.equals("net/neoforged/neoforge/client/event/RenderGuiLayerEvent$Pre"))
                || !call.name.equals("getPartialTick")
                || !call.desc.equals("()F")) return false;
        call.setOpcode(Opcodes.INVOKESTATIC);
        call.owner = "net/neoforged/forgestuck/compat/LegacyGuiOverlayEvent";
        call.desc = "(Lnet/neoforged/neoforge/client/event/RenderGuiLayerEvent;)F";
        call.itf = false;
        return true;
    }

    private static int eventParameterLocal(MethodNode method, String eventDescriptor) {
        int local = (method.access & org.objectweb.asm.Opcodes.ACC_STATIC) == 0 ? 1 : 0;
        for (org.objectweb.asm.Type argument : org.objectweb.asm.Type.getArgumentTypes(method.desc)) {
            if (argument.getDescriptor().equals(eventDescriptor)) return local;
            local += argument.getSize();
        }
        return -1;
    }

    private static boolean nodeIsInterfaceVirtualCall(ClassNode node, MethodInsnNode call) {
        return (node.access & org.objectweb.asm.Opcodes.ACC_INTERFACE) != 0
                && call.getOpcode() == org.objectweb.asm.Opcodes.INVOKEVIRTUAL
                && call.owner.equals(node.name);
    }

    private static boolean rewriteLegacyLootDataCall(InsnList instructions, MethodInsnNode call) {
        String lootDataType = "net/minecraft/world/level/storage/loot/LootDataType";
        if (call.owner.equals(lootDataType) && call.name.equals("m_278624_")
                && call.desc.equals("()Ljava/lang/String;")) {
            call.setOpcode(org.objectweb.asm.Opcodes.INVOKESTATIC);
            call.owner = LEGACY_LOOT_DATA_TYPE;
            call.name = "directory";
            call.desc = "(L" + lootDataType + ";)Ljava/lang/String;";
            call.itf = false;
            return true;
        }
        if (call.owner.equals("net/minecraft/server/MinecraftServer")
                && call.name.equals("m_278653_")
                && call.desc.equals("()Lnet/minecraft/world/level/storage/loot/LootDataManager;")) {
            call.name = "reloadableRegistries";
            call.desc = "()Lnet/minecraft/server/ReloadableServerRegistries$Holder;";
            return true;
        }
        if (call.owner.equals("net/minecraft/world/level/storage/loot/LootDataManager")
                && call.name.equals("m_278706_")
                && call.desc.equals("(L" + lootDataType + ";)Ljava/util/Collection;")) {
            instructions.insertBefore(call, new MethodInsnNode(org.objectweb.asm.Opcodes.INVOKEVIRTUAL,
                    lootDataType, "registryKey", "()Lnet/minecraft/resources/ResourceKey;", false));
            call.owner = "net/minecraft/server/ReloadableServerRegistries$Holder";
            call.name = "getKeys";
            call.desc = "(Lnet/minecraft/resources/ResourceKey;)Ljava/util/Collection;";
            return true;
        }
        return false;
    }

    private static boolean rewriteLegacySpriteSourceRegistration(MethodInsnNode call) {
        if (call.getOpcode() != org.objectweb.asm.Opcodes.INVOKESTATIC
                || !LEGACY_SPRITE_SOURCES.equals(call.owner)
                || !(call.name.equals("register") || call.name.equals("m_260887_"))
                || !call.desc.equals("(Ljava/lang/String;Lcom/mojang/serialization/Codec;)"
                        + "Lnet/minecraft/client/renderer/texture/atlas/SpriteSourceType;")) {
            return false;
        }
        call.setOpcode(org.objectweb.asm.Opcodes.INVOKESTATIC);
        call.owner = LEGACY_SPRITE_SOURCE_REGISTRATION;
        call.name = "register";
        call.desc = "(Ljava/lang/String;Lcom/mojang/serialization/Codec;)"
                + "Lnet/minecraft/client/renderer/texture/atlas/SpriteSourceType;";
        call.itf = false;
        return true;
    }

    private static boolean rewriteLegacyExtraCodecsValidate(MethodInsnNode call) {
        if (call.getOpcode() != org.objectweb.asm.Opcodes.INVOKESTATIC
                || !call.owner.equals("net/minecraft/util/ExtraCodecs")
                || !call.name.equals("m_264370_")
                || !call.desc.equals("(Lcom/mojang/serialization/Codec;Ljava/util/function/Function;)"
                        + "Lcom/mojang/serialization/Codec;")) {
            return false;
        }
        call.setOpcode(org.objectweb.asm.Opcodes.INVOKEINTERFACE);
        call.owner = "com/mojang/serialization/Codec";
        call.name = "validate";
        call.desc = "(Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;";
        call.itf = true;
        return true;
    }

    private static boolean rewriteLegacyToolStackPredicateCall(MethodInsnNode call) {
        if (call.getOpcode() != org.objectweb.asm.Opcodes.INVOKESTATIC) return false;
        if (TOOL_STACK_ITEM_PREDICATE.equals(call.owner)
                && (call.name.equals("ofTool") || call.name.equals("ofContext"))) {
            call.owner = LEGACY_TOOL_STACK_PREDICATE;
            call.desc = "(Ljava/lang/Object;)L" + ITEM_PREDICATE + ";";
            call.itf = false;
            return true;
        }
        String registrationDescriptor =
                "(Lnet/minecraft/resources/ResourceLocation;Ljava/util/function/Function;)V";
        if (!ITEM_PREDICATE.equals(call.owner)
                || !call.name.equals("register")
                || !registrationDescriptor.equals(call.desc)) return false;
        call.owner = LEGACY_TOOL_STACK_PREDICATE;
        call.itf = false;
        return true;
    }

    private static boolean rewriteThermalClientConfigDefault(String className, MethodNode method) {
        if (!THERMAL_CLIENT_CONFIG.equals(className) || !method.name.equals("apply")) return false;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof FieldInsnNode field)
                    || field.getOpcode() != org.objectweb.asm.Opcodes.GETSTATIC
                    || !field.owner.equals(THERMAL_CLIENT_CONFIG)
                    || !field.name.equals("festiveMobs")
                    || !field.desc.equals("Ljava/util/function/Supplier;")) continue;
            AbstractInsnNode previous = previousMeaningful(field);
            if (previous instanceof org.objectweb.asm.tree.LdcInsnNode label
                    && "Festive Vanilla Mobs".equals(label.cst)) {
                field.name = "festiveVanillaMobs";
                return true;
            }
        }
        return false;
    }

    private static boolean rewriteLegacyFungusBlockConstructor(MethodNode method) {
        boolean modified = false;
        String legacyDescriptor =
                "(Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;"
                        + "Lnet/minecraft/resources/ResourceKey;"
                        + "Lnet/minecraft/world/level/block/Block;)V";
        String targetDescriptor =
                "(Lnet/minecraft/resources/ResourceKey;"
                        + "Lnet/minecraft/world/level/block/Block;"
                        + "Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)V";
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode call)
                    || call.getOpcode() != org.objectweb.asm.Opcodes.INVOKESPECIAL
                    || !call.owner.equals("net/minecraft/world/level/block/FungusBlock")
                    || !call.name.equals("<init>")
                    || !call.desc.equals(legacyDescriptor)) continue;
            int blockLocal = method.maxLocals++;
            int keyLocal = method.maxLocals++;
            int propertiesLocal = method.maxLocals++;
            InsnList reordered = new InsnList();
            reordered.add(new VarInsnNode(org.objectweb.asm.Opcodes.ASTORE, blockLocal));
            reordered.add(new VarInsnNode(org.objectweb.asm.Opcodes.ASTORE, keyLocal));
            reordered.add(new VarInsnNode(org.objectweb.asm.Opcodes.ASTORE, propertiesLocal));
            reordered.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, keyLocal));
            reordered.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, blockLocal));
            reordered.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, propertiesLocal));
            method.instructions.insertBefore(call, reordered);
            call.desc = targetDescriptor;
            modified = true;
        }
        return modified;
    }

    private static boolean isLegacyItemStackGetTagElement(MethodInsnNode call) {
        return call.getOpcode() == Opcodes.INVOKEVIRTUAL
                && call.owner.equals(ITEMSTACK)
                && (call.name.equals("getTagElement") || call.name.equals("m_41737_"))
                && call.desc.equals("(Ljava/lang/String;)Lnet/minecraft/nbt/CompoundTag;");
    }

    private static boolean isLegacyItemStackGetOrCreateTagElement(MethodInsnNode call) {
        return call.getOpcode() == Opcodes.INVOKEVIRTUAL
                && call.owner.equals(ITEMSTACK)
                && call.name.equals("getOrCreateTagElement")
                && call.desc.equals("(Ljava/lang/String;)Lnet/minecraft/nbt/CompoundTag;");
    }

    private static boolean rewriteLegacyItemStackSave(MethodInsnNode call) {
        if (call.getOpcode() != Opcodes.INVOKEVIRTUAL
                || !call.owner.equals(ITEMSTACK)
                || !(call.name.equals("m_41739_") || call.name.equals("save"))
                || !call.desc.equals(LEGACY_ITEMSTACK_SAVE_DESC)) {
            return false;
        }
        call.setOpcode(Opcodes.INVOKESTATIC);
        call.owner = LEGACY_ITEMSTACK_SHIM;
        call.name = "save";
        call.desc = "(L" + ITEMSTACK + ";Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;";
        call.itf = false;
        return true;
    }

    private static boolean rewriteLegacyItemMaxStackSize(MethodInsnNode call) {
        if (call.getOpcode() != Opcodes.INVOKEVIRTUAL
                || !call.owner.equals(ITEM)
                || !(call.name.equals("m_41459_") || call.name.equals("getMaxStackSize"))
                || !call.desc.equals("()I")) {
            return false;
        }
        call.setOpcode(Opcodes.INVOKESTATIC);
        call.owner = LEGACY_ITEMSTACK_SHIM;
        call.name = "getMaxStackSize";
        call.desc = "(L" + ITEM + ";)I";
        call.itf = false;
        return true;
    }

    private static boolean rewriteLegacyBlockItemData(MethodInsnNode call) {
        if (call.getOpcode() != Opcodes.INVOKESTATIC
                || !call.owner.equals("net/minecraft/world/item/BlockItem")
                || !(call.name.equals("m_186336_") || call.name.equals("getBlockEntityData"))
                || !call.desc.equals("(L" + ITEMSTACK + ";)Lnet/minecraft/nbt/CompoundTag;")) {
            return false;
        }
        call.owner = LEGACY_ITEMSTACK_SHIM;
        call.name = "getBlockEntityData";
        call.itf = false;
        return true;
    }

    private static boolean rewriteLegacyDyeColorComponents(MethodInsnNode call) {
        String dyeColor = "net/minecraft/world/item/DyeColor";
        if (call.getOpcode() != Opcodes.INVOKEVIRTUAL
                || !call.owner.equals(dyeColor)
                || !(call.name.equals("m_41068_") || call.name.equals("getTextureDiffuseColors"))
                || !call.desc.equals("()[F")) {
            return false;
        }
        call.setOpcode(Opcodes.INVOKESTATIC);
        call.owner = LEGACY_DYE_COLOR_SHIM;
        call.name = "getTextureDiffuseColors";
        call.desc = "(L" + dyeColor + ";)[F";
        call.itf = false;
        return true;
    }

    private static boolean rewriteLegacyEnchantmentList(MethodInsnNode call) {
        if (call.getOpcode() != Opcodes.INVOKESTATIC
                || !call.owner.equals("net/minecraft/world/item/enchantment/EnchantmentHelper")
                || !call.name.equals("m_44882_")
                || !call.desc.equals("(Lnet/minecraft/nbt/ListTag;)Ljava/util/Map;")) {
            return false;
        }
        call.owner = LEGACY_ENCHANTMENT_SHIM;
        call.name = "deserializeEnchantments";
        call.itf = false;
        return true;
    }

    private static boolean rewriteLegacyItemStackParse(MethodInsnNode call) {
        if (call.getOpcode() != Opcodes.INVOKESTATIC
                || !call.owner.equals(ITEMSTACK)
                || !(call.name.equals("m_41712_") || call.name.equals("of"))
                || !call.desc.equals(LEGACY_ITEMSTACK_PARSE_DESC)) {
            return false;
        }
        call.owner = LEGACY_ITEMSTACK_SHIM;
        call.name = "parse";
        call.desc = "(Lnet/minecraft/nbt/CompoundTag;)L" + ITEMSTACK + ";";
        call.itf = false;
        return true;
    }

    private static boolean isLegacyItemStackSetHoverName(MethodInsnNode call) {
        return call.getOpcode() == Opcodes.INVOKEVIRTUAL
                && call.owner.equals(ITEMSTACK)
                && (call.name.equals("setHoverName") || call.name.equals("m_41714_"))
                && call.desc.equals("(Lnet/minecraft/network/chat/Component;)L" + ITEMSTACK + ";");
    }

    private static boolean isLegacyItemStackHasCustomHoverName(MethodInsnNode call) {
        return call.getOpcode() == Opcodes.INVOKEVIRTUAL
                && call.owner.equals(ITEMSTACK)
                && (call.name.equals("hasCustomHoverName") || call.name.equals("m_41788_"))
                && call.desc.equals("()Z");
    }

    private static boolean isLegacyItemStackHasCustomHoverName(org.objectweb.asm.Handle handle) {
        return handle.getTag() == Opcodes.H_INVOKEVIRTUAL
                && handle.getOwner().equals(ITEMSTACK)
                && (handle.getName().equals("hasCustomHoverName") || handle.getName().equals("m_41788_"))
                && handle.getDesc().equals("()Z");
    }

    private static org.objectweb.asm.Handle legacyItemStackHasCustomHoverNameHandle() {
        return new org.objectweb.asm.Handle(Opcodes.H_INVOKESTATIC, LEGACY_ITEMSTACK_SHIM,
                "hasCustomHoverName", "(L" + ITEMSTACK + ";)Z", false);
    }

    private static boolean isLegacyItemStackGetTag(MethodInsnNode call) {
        return call.getOpcode() == org.objectweb.asm.Opcodes.INVOKEVIRTUAL
                && call.owner.equals(ITEMSTACK)
                && (call.name.equals("m_41783_") || call.name.equals("getTag"))
                && call.desc.equals(ITEMSTACK_TAG);
    }

    private static boolean isLegacyItemStackGetEnchantmentTags(MethodInsnNode call) {
        return call.getOpcode() == Opcodes.INVOKEVIRTUAL && isLegacyItemStackGetEnchantmentTags(
                new org.objectweb.asm.Handle(Opcodes.H_INVOKEVIRTUAL, call.owner, call.name, call.desc, false));
    }

    private static boolean isLegacyItemStackGetEnchantmentTags(org.objectweb.asm.Handle handle) {
        return handle.getTag() == Opcodes.H_INVOKEVIRTUAL
                && handle.getOwner().equals(ITEMSTACK)
                && (handle.getName().equals("m_41785_") || handle.getName().equals("getEnchantmentTags"))
                && handle.getDesc().equals("()Lnet/minecraft/nbt/ListTag;");
    }

    private static org.objectweb.asm.Handle legacyItemStackGetEnchantmentTagsHandle() {
        return new org.objectweb.asm.Handle(Opcodes.H_INVOKESTATIC, LEGACY_ITEMSTACK_SHIM, "getEnchantmentTags",
                "(L" + ITEMSTACK + ";)Lnet/minecraft/nbt/ListTag;", false);
    }

    private static boolean rewriteLegacyItemStackTagField(MethodNode method, FieldInsnNode field) {
        if (!field.owner.equals(ITEMSTACK)
                || !(field.name.equals("f_41590_") || field.name.equals("tag"))
                || !field.desc.equals("Lnet/minecraft/nbt/CompoundTag;")) return false;
        String name;
        String descriptor;
        if (field.getOpcode() == org.objectweb.asm.Opcodes.GETFIELD) {
            name = "getTag";
            descriptor = "(L" + ITEMSTACK + ";)Lnet/minecraft/nbt/CompoundTag;";
        } else if (field.getOpcode() == org.objectweb.asm.Opcodes.PUTFIELD) {
            name = "setTag";
            descriptor = "(L" + ITEMSTACK + ";Lnet/minecraft/nbt/CompoundTag;)V";
        } else {
            return false;
        }
        method.instructions.set(field, new MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESTATIC,
                LEGACY_ITEMSTACK_SHIM, name, descriptor, false));
        return true;
    }

    private static boolean rewriteLegacyCustomRecipeConstructor(MethodNode method, MethodInsnNode call) {
        String owner = "net/minecraft/world/item/crafting/CustomRecipe";
        String legacyDescriptor = "(Lnet/minecraft/resources/ResourceLocation;"
                + "Lnet/minecraft/world/item/crafting/CraftingBookCategory;)V";
        if (call.getOpcode() != org.objectweb.asm.Opcodes.INVOKESPECIAL
                || !call.owner.equals(owner)
                || !call.name.equals("<init>")
                || !call.desc.equals(legacyDescriptor)) return false;
        InsnList argumentAdaptation = new InsnList();
        argumentAdaptation.add(new InsnNode(org.objectweb.asm.Opcodes.SWAP));
        argumentAdaptation.add(new InsnNode(org.objectweb.asm.Opcodes.POP));
        method.instructions.insertBefore(call, argumentAdaptation);
        call.desc = "(Lnet/minecraft/world/item/crafting/CraftingBookCategory;)V";
        return true;
    }

    private static org.objectweb.asm.Handle legacyItemStackTagFieldHandle(org.objectweb.asm.Handle handle) {
        if (!handle.getOwner().equals(ITEMSTACK)
                || !(handle.getName().equals("f_41590_") || handle.getName().equals("tag"))
                || !handle.getDesc().equals("Lnet/minecraft/nbt/CompoundTag;")) return null;
        if (handle.getTag() == org.objectweb.asm.Opcodes.H_GETFIELD) {
            return new org.objectweb.asm.Handle(org.objectweb.asm.Opcodes.H_INVOKESTATIC,
                    LEGACY_ITEMSTACK_SHIM, "getTag",
                    "(L" + ITEMSTACK + ";)Lnet/minecraft/nbt/CompoundTag;", false);
        }
        if (handle.getTag() == org.objectweb.asm.Opcodes.H_PUTFIELD) {
            return new org.objectweb.asm.Handle(org.objectweb.asm.Opcodes.H_INVOKESTATIC,
                    LEGACY_ITEMSTACK_SHIM, "setTag",
                    "(L" + ITEMSTACK + ";Lnet/minecraft/nbt/CompoundTag;)V", false);
        }
        return null;
    }

    private static boolean isLegacyItemStackGetTag(org.objectweb.asm.Handle handle) {
        return handle.getTag() == org.objectweb.asm.Opcodes.H_INVOKEVIRTUAL
                && handle.getOwner().equals(ITEMSTACK)
                && (handle.getName().equals("m_41783_") || handle.getName().equals("getTag"))
                && handle.getDesc().equals(ITEMSTACK_TAG);
    }

    private static org.objectweb.asm.Handle legacyItemStackGetTagHandle() {
        return new org.objectweb.asm.Handle(org.objectweb.asm.Opcodes.H_INVOKESTATIC, LEGACY_ITEMSTACK_SHIM, "getTag",
                "(L" + ITEMSTACK + ";)Lnet/minecraft/nbt/CompoundTag;", false);
    }

    private static boolean isLegacyCriteriaTriggerRegister(MethodInsnNode call) {
        return call.getOpcode() == org.objectweb.asm.Opcodes.INVOKESTATIC
                && call.owner.equals("net/minecraft/advancements/CriteriaTriggers")
                && call.name.equals("m_10595_")
                && call.desc.equals("(Lnet/minecraft/advancements/CriterionTrigger;)Lnet/minecraft/advancements/CriterionTrigger;");
    }

    private static boolean isLegacyDamageSourceCreativePlayer(MethodInsnNode call) {
        return call.getOpcode() == org.objectweb.asm.Opcodes.INVOKEVIRTUAL
                && call.owner.equals(DAMAGE_SOURCE)
                && call.name.equals("m_269014_")
                && call.desc.equals("()Z");
    }

    private static boolean isLegacyMobEffectHasEffect(MethodInsnNode call) {
        return call.getOpcode() == Opcodes.INVOKEVIRTUAL
                && (call.owner.equals("net/minecraft/world/entity/LivingEntity")
                        || call.owner.equals("net/minecraft/world/entity/player/Player"))
                && (call.name.equals("m_21023_") || call.name.equals("hasEffect"))
                && call.desc.equals("(Lnet/minecraft/world/effect/MobEffect;)Z");
    }

    private static boolean isLegacyMobEffectLookup(MethodInsnNode call) {
        return call.getOpcode() == Opcodes.INVOKEVIRTUAL
                && (call.owner.equals("net/minecraft/world/entity/LivingEntity")
                        || call.owner.equals("net/minecraft/world/entity/player/Player"))
                && call.desc.equals("(Lnet/minecraft/world/effect/MobEffect;)"
                        + "Lnet/minecraft/world/effect/MobEffectInstance;");
    }

    private static boolean isLegacyMobEffectRemove(MethodInsnNode call) {
        return call.getOpcode() == Opcodes.INVOKEVIRTUAL
                && (call.owner.equals("net/minecraft/world/entity/LivingEntity")
                        || call.owner.equals("net/minecraft/world/entity/player/Player"))
                && (call.name.equals("removeEffect") || call.name.equals("m_21195_"))
                && call.desc.equals("(Lnet/minecraft/world/effect/MobEffect;)Z");
    }

    private static boolean isLegacyAttributeValueLookup(MethodInsnNode call) {
        return call.getOpcode() == Opcodes.INVOKEVIRTUAL
                && (call.owner.equals("net/minecraft/world/entity/LivingEntity")
                        || call.owner.equals("net/minecraft/world/entity/player/Player"))
                && call.desc.equals("(Lnet/minecraft/world/entity/ai/attributes/Attribute;)D");
    }

    private static boolean isLegacyEntityArmorSlots(MethodInsnNode call) {
        return call.getOpcode() == Opcodes.INVOKEVIRTUAL
                && call.owner.equals("net/minecraft/world/entity/Entity")
                && (call.name.equals("getArmorSlots") || call.name.equals("m_6168_"))
                && call.desc.equals("()Ljava/lang/Iterable;");
    }

    private static boolean isLegacyHolderContainsTag(MethodInsnNode call) {
        return call.name.equals("containsTag")
                && call.desc.equals("(Lnet/minecraft/tags/TagKey;)Z")
                && ((call.getOpcode() == Opcodes.INVOKEVIRTUAL
                                && call.owner.equals("net/minecraft/core/Holder$Reference"))
                        || (call.getOpcode() == Opcodes.INVOKEINTERFACE
                                && call.owner.equals("net/minecraft/core/Holder")));
    }

    private static boolean rewriteLegacyTConstructEquipmentContextArrays(ClassNode node) {
        if (!LEGACY_TCONSTRUCT_EQUIPMENT_CONTEXT.equals(node.name)) return false;
        int adapted = 0;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("<init>")) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof IntInsnNode size) || size.getOpcode() != Opcodes.BIPUSH
                        || size.operand != 6) continue;
                AbstractInsnNode allocation = instruction.getNext();
                while (allocation != null && allocation.getOpcode() < 0) allocation = allocation.getNext();
                boolean booleanArray = allocation instanceof IntInsnNode array
                        && array.getOpcode() == Opcodes.NEWARRAY && array.operand == Opcodes.T_BOOLEAN;
                boolean toolArray = allocation instanceof TypeInsnNode type
                        && type.getOpcode() == Opcodes.ANEWARRAY
                        && type.desc.equals("slimeknights/tconstruct/library/tools/nbt/IToolStackView");
                if (booleanArray || toolArray) {
                    method.instructions.set(instruction, new IntInsnNode(Opcodes.BIPUSH, 7));
                    adapted++;
                }
            }
        }
        return adapted > 0;
    }

    private static boolean rewriteLegacyTConstructSpawnCalls(ClassNode node) {
        if (!LEGACY_TCONSTRUCT_MOB_EQUIPMENT.equals(node.name)) return false;
        String mob = "net/minecraft/world/entity/Mob";
        String entity = "net/minecraft/world/entity/Entity";
        String randomSource = "net/minecraft/util/RandomSource";
        String finalizeSpawnEvent = "net/neoforged/neoforge/event/entity/living/FinalizeSpawnEvent";
        String compoundTag = "net/minecraft/nbt/CompoundTag";
        String serverLevelAccessor = "net/minecraft/world/level/ServerLevelAccessor";
        String difficulty = "net/minecraft/world/DifficultyInstance";
        String spawnType = "net/minecraft/world/entity/MobSpawnType";
        String spawnData = "net/minecraft/world/entity/SpawnGroupData";
        String legacyFinalizeSpawnDesc = "(L" + serverLevelAccessor + ";L" + difficulty + ";L" + spawnType
                + ";L" + spawnData + ";L" + compoundTag + ";)L" + spawnData + ";";
        String finalizeSpawnDesc = "(L" + serverLevelAccessor + ";L" + difficulty + ";L" + spawnType
                + ";L" + spawnData + ";)L" + spawnData + ";";
        boolean modified = false;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode call)) continue;
                if (call.getOpcode() == Opcodes.INVOKEVIRTUAL && call.owner.equals(mob)
                        && (call.name.equals("m_217043_") || call.name.equals("getRandom"))
                        && call.desc.equals("()L" + randomSource + ";")) {
                    call.owner = entity;
                    call.name = "getRandom";
                    call.itf = false;
                    modified = true;
                } else if (call.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && (call.owner.equals(finalizeSpawnEvent)
                                || call.owner.equals("net/minecraftforge/event/entity/living/MobSpawnEvent$FinalizeSpawn"))
                        && (call.name.equals("getSpawnTag") || call.name.equals("m_"))
                        && call.desc.equals("()L" + compoundTag + ";")) {
                    InsnList replacement = new InsnList();
                    replacement.add(new InsnNode(Opcodes.POP));
                    replacement.add(new InsnNode(Opcodes.ACONST_NULL));
                    method.instructions.insertBefore(call, replacement);
                    method.instructions.remove(call);
                    modified = true;
                } else if (call.getOpcode() == Opcodes.INVOKEVIRTUAL && call.owner.equals(mob)
                        && (call.name.equals("m_6518_") || call.name.equals("finalizeSpawn"))
                        && call.desc.equals(legacyFinalizeSpawnDesc)) {
                    method.instructions.insertBefore(call, new InsnNode(Opcodes.POP));
                    call.name = "finalizeSpawn";
                    call.desc = finalizeSpawnDesc;
                    call.itf = false;
                    modified = true;
                }
            }
        }
        return modified;
    }

    private static boolean rewriteLegacyTConstructTintedVertexBuilder(ClassNode node, Set<String> remapped) {
        if (!LEGACY_TCONSTRUCT_TINTED_VERTEX_BUILDER.equals(node.name)) return false;
        boolean modified = false;
        String vertexConsumer = "L" + VERTEX_CONSUMER + ";";
        modified |= addLegacyVertexConsumerBridge(node, "addVertex", "(FFF)" + vertexConsumer,
                "m_5483_", "(DDD)" + vertexConsumer,
                new int[] {1, 2, 3}, Opcodes.FLOAD, true);
        modified |= addLegacyVertexConsumerBridge(node, "setColor", "(IIII)" + vertexConsumer,
                "m_6122_", "(IIII)" + vertexConsumer,
                new int[] {1, 2, 3, 4}, Opcodes.ILOAD, false);
        modified |= addLegacyVertexConsumerBridge(node, "setUv", "(FF)" + vertexConsumer,
                "m_7421_", "(FF)" + vertexConsumer,
                new int[] {1, 2}, Opcodes.FLOAD, false);
        modified |= addLegacyVertexConsumerBridge(node, "setUv1", "(II)" + vertexConsumer,
                "m_7122_", "(II)" + vertexConsumer,
                new int[] {1, 2}, Opcodes.ILOAD, false);
        modified |= addLegacyVertexConsumerBridge(node, "setUv2", "(II)" + vertexConsumer,
                "m_7120_", "(II)" + vertexConsumer,
                new int[] {1, 2}, Opcodes.ILOAD, false);
        modified |= addLegacyVertexConsumerBridge(node, "setNormal", "(FFF)" + vertexConsumer,
                "m_5601_", "(FFF)" + vertexConsumer,
                new int[] {1, 2, 3}, Opcodes.FLOAD, false);
        modified |= removeLegacyTintedVertexDefaultColor(node, "m_7404_", "(IIII)V", 5);
        modified |= removeLegacyTintedVertexDefaultColor(node, "m_141991_", "()V", 1);
        if (modified) remapped.add("TConstruct TintedVertexBuilder -> 1.21 VertexConsumer methods");
        return modified;
    }

    private static boolean addLegacyVertexConsumerBridge(
            ClassNode node, String modernName, String modernDesc, String legacyName, String legacyDesc,
            int[] argumentLocals, int argumentLoadOpcode, boolean widenFloatsToDoubles) {
        if (node.methods.stream().anyMatch(method -> method.name.equals(modernName)
                && method.desc.equals(modernDesc))) return false;
        if (node.methods.stream().noneMatch(method -> method.name.equals(legacyName)
                && method.desc.equals(legacyDesc))) return false;
        MethodNode bridge = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                modernName, modernDesc, null, null);
        bridge.visitCode();
        bridge.visitVarInsn(Opcodes.ALOAD, 0);
        for (int local : argumentLocals) {
            bridge.visitVarInsn(argumentLoadOpcode, local);
            if (widenFloatsToDoubles) bridge.visitInsn(Opcodes.F2D);
        }
        bridge.visitMethodInsn(Opcodes.INVOKEVIRTUAL, node.name, legacyName, legacyDesc, false);
        bridge.visitInsn(Opcodes.POP);
        bridge.visitVarInsn(Opcodes.ALOAD, 0);
        bridge.visitInsn(Opcodes.ARETURN);
        int maxStack = widenFloatsToDoubles ? 7 : argumentLocals.length + 1;
        int maxLocals = argumentLocals[argumentLocals.length - 1] + 1;
        bridge.visitMaxs(maxStack, maxLocals);
        bridge.visitEnd();
        node.methods.add(bridge);
        return true;
    }

    private static boolean removeLegacyTintedVertexDefaultColor(
            ClassNode node, String methodName, String descriptor, int maxLocals) {
        for (MethodNode method : node.methods) {
            if (!method.name.equals(methodName) || !method.desc.equals(descriptor)) continue;
            method.instructions.clear();
            method.instructions.add(new InsnNode(Opcodes.RETURN));
            method.tryCatchBlocks.clear();
            method.localVariables = null;
            method.maxStack = 0;
            method.maxLocals = maxLocals;
            return true;
        }
        return false;
    }

    private static boolean rewriteLegacyPatchouliVariableLookups(ClassNode node) {
        String level = "net/minecraft/world/level/Level";
        String registryAccess = "net/minecraft/core/RegistryAccess";
        String variable = "vazkii/patchouli/api/IVariable";
        String oldDescriptor = "(Ljava/lang/String;)L" + variable + ";";
        String newDescriptor = "(Ljava/lang/String;Lnet/minecraft/core/HolderLookup$Provider;)L"
                + variable + ";";
        boolean modified = false;
        for (MethodNode method : node.methods) {
            if ((method.access & Opcodes.ACC_STATIC) != 0
                    || !method.desc.startsWith("(L" + level + ";L" + PATCHOULI_VARIABLE_PROVIDER + ";")) {
                continue;
            }
            boolean methodModified = false;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode call)
                        || call.getOpcode() != Opcodes.INVOKEINTERFACE
                        || !call.owner.equals(PATCHOULI_VARIABLE_PROVIDER)
                        || !call.name.equals("get")
                        || !call.desc.equals(oldDescriptor)) continue;
                InsnList provider = new InsnList();
                provider.add(new VarInsnNode(Opcodes.ALOAD, 1));
                provider.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, level, "registryAccess",
                        "()L" + registryAccess + ";", false));
                method.instructions.insertBefore(call, provider);
                call.desc = newDescriptor;
                modified = true;
                methodModified = true;
            }
            if (methodModified) method.maxStack++;
        }
        return modified;
    }

    private static boolean isLegacyMobTypeGetter(MethodInsnNode call) {
        return call.getOpcode() == org.objectweb.asm.Opcodes.INVOKEVIRTUAL
                && call.owner.equals("net/minecraft/world/entity/LivingEntity")
                && call.name.equals("m_6336_")
                && call.desc.equals("()L" + LEGACY_MOB_TYPE + ";");
    }

    private static boolean isLegacyDamageSourceCreativePlayer(org.objectweb.asm.Handle handle) {
        return handle.getTag() == org.objectweb.asm.Opcodes.H_INVOKEVIRTUAL
                && handle.getOwner().equals(DAMAGE_SOURCE)
                && handle.getName().equals("m_269014_")
                && handle.getDesc().equals("()Z");
    }

    private static org.objectweb.asm.Handle legacyDamageSourceCreativePlayerHandle() {
        return new org.objectweb.asm.Handle(org.objectweb.asm.Opcodes.H_INVOKESTATIC,
                "net/neoforged/forgestuck/compat/LegacyDamageSource", "isCreativePlayer",
                "(L" + DAMAGE_SOURCE + ";)Z", false);
    }

    private static boolean isFluidStateDefinitionMethod(MethodInsnNode call) {
        return call.getOpcode() == org.objectweb.asm.Opcodes.INVOKESPECIAL
                && call.name.equals("m_7180_") && call.desc.equals(FLUID_STATE_DEFINITION_DESC);
    }

    private static boolean isBlockStateDefinitionMethod(MethodInsnNode call) {
        return call.getOpcode() == org.objectweb.asm.Opcodes.INVOKESPECIAL
                && call.name.equals("m_7926_") && call.desc.equals(BLOCK_STATE_DEFINITION_DESC);
    }

    private static boolean isBlockFluidStateMethod(MethodInsnNode call) {
        return call.getOpcode() == org.objectweb.asm.Opcodes.INVOKESPECIAL
                && isBlockFluidStateMethod(call.name, call.desc);
    }

    private static boolean isBlockFluidStateMethod(String name, String descriptor) {
        return name.equals("m_5888_")
                && descriptor.equals("(Lnet/minecraft/world/level/block/state/BlockState;)"
                        + "Lnet/minecraft/world/level/material/FluidState;");
    }

    private static boolean isLegacyEmptyBlockDropsAssignment(FieldInsnNode field, String className) {
        if (field.getOpcode() != org.objectweb.asm.Opcodes.PUTFIELD
                || !(field.owner.equals("net/minecraft/world/level/block/Block") || field.owner.equals(className))
                || !(field.name.equals("f_60440_") || field.name.equals("drops"))
                || !field.desc.equals("Lnet/minecraft/resources/ResourceLocation;")) return false;
        AbstractInsnNode value = previousMeaningful(field);
        if (!(value instanceof FieldInsnNode source && source.getOpcode() == org.objectweb.asm.Opcodes.GETSTATIC
                && source.owner.equals("net/minecraft/world/level/storage/loot/BuiltInLootTables")
                && (source.name.equals("f_78712_") || source.name.equals("EMPTY"))
                && source.desc.equals("Lnet/minecraft/resources/ResourceLocation;"))) return false;
        AbstractInsnNode receiver = previousMeaningful(value);
        return receiver instanceof org.objectweb.asm.tree.VarInsnNode load
                && load.getOpcode() == org.objectweb.asm.Opcodes.ALOAD && load.var == 0;
    }

    private static boolean rewriteLegacyMobEffectInstanceConstructor(MethodNode method) {
        String owner = "net/minecraft/world/effect/MobEffectInstance";
        String compatOwner = "net/neoforged/forgestuck/compat/LegacyMobEffectInstance";
        String descriptor = "(Lnet/minecraft/world/effect/MobEffect;II)V";
        boolean rewritten = false;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode call) || call.getOpcode() != org.objectweb.asm.Opcodes.INVOKESPECIAL
                    || !call.owner.equals(owner) || !call.name.equals("<init>") || !call.desc.equals(descriptor)) continue;
            TypeInsnNode allocation = null;
            for (AbstractInsnNode previous = call.getPrevious(); previous != null; previous = previous.getPrevious()) {
                if (previous instanceof TypeInsnNode type && type.getOpcode() == org.objectweb.asm.Opcodes.NEW
                        && type.desc.equals(owner)) {
                    allocation = type;
                    break;
                }
            }
            if (allocation == null) continue;
            AbstractInsnNode duplicate = allocation.getNext();
            while (duplicate != null && duplicate.getOpcode() < 0) duplicate = duplicate.getNext();
            if (duplicate == null || duplicate.getOpcode() != org.objectweb.asm.Opcodes.DUP) continue;
            allocation.desc = compatOwner;
            call.owner = compatOwner;
            rewritten = true;
        }
        return rewritten;
    }

    private static boolean rewriteLegacyStairBlockConstructor(MethodNode method) {
        String owner = "net/minecraft/world/level/block/StairBlock";
        String oldDescriptor = "(Ljava/util/function/Supplier;"
                + "Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)V";
        String newDescriptor = "(Lnet/minecraft/world/level/block/state/BlockState;"
                + "Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)V";
        boolean rewritten = false;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode call)
                    || call.getOpcode() != org.objectweb.asm.Opcodes.INVOKESPECIAL
                    || !call.owner.equals(owner) || !call.name.equals("<init>")
                    || !call.desc.equals(oldDescriptor)) continue;

            method.instructions.insertBefore(call, new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.SWAP));
            method.instructions.insertBefore(call, new MethodInsnNode(org.objectweb.asm.Opcodes.INVOKEINTERFACE,
                    "java/util/function/Supplier", "get", "()Ljava/lang/Object;", true));
            method.instructions.insertBefore(call, new TypeInsnNode(org.objectweb.asm.Opcodes.CHECKCAST,
                    "net/minecraft/world/level/block/state/BlockState"));
            method.instructions.insertBefore(call, new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.SWAP));
            call.desc = newDescriptor;
            rewritten = true;
        }
        return rewritten;
    }

    private static boolean rewriteLegacyLiquidBlockConstructor(MethodNode method) {
        String owner = "net/minecraft/world/level/block/LiquidBlock";
        String oldDescriptor = "(Ljava/util/function/Supplier;"
                + "Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)V";
        String newDescriptor = "(Lnet/minecraft/world/level/material/FlowingFluid;"
                + "Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)V";
        boolean rewritten = false;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode call)
                    || call.getOpcode() != org.objectweb.asm.Opcodes.INVOKESPECIAL
                    || !call.owner.equals(owner) || !call.name.equals("<init>")
                    || !call.desc.equals(oldDescriptor)) continue;
            method.instructions.insertBefore(call, new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.SWAP));
            method.instructions.insertBefore(call, new MethodInsnNode(org.objectweb.asm.Opcodes.INVOKEINTERFACE,
                    "java/util/function/Supplier", "get", "()Ljava/lang/Object;", true));
            method.instructions.insertBefore(call, new TypeInsnNode(org.objectweb.asm.Opcodes.CHECKCAST,
                    "net/minecraft/world/level/material/FlowingFluid"));
            method.instructions.insertBefore(call, new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.SWAP));
            call.desc = newDescriptor;
            rewritten = true;
        }
        return rewritten;
    }

    private static boolean rewriteLegacyArmorMaterialFactory(String className, MethodNode method) {
        String factoryDescriptor = "(Lnet/minecraft/resources/ResourceLocation;"
                + "Lnet/minecraft/sounds/SoundEvent;"
                + "[Lnet/minecraft/world/item/ArmorItem$Type;)"
                + "Lslimeknights/tconstruct/library/tools/definition/ModifiableArmorMaterial;";
        if (!className.equals(MODIFIABLE_ARMOR_MATERIAL)
                || !method.name.equals("create") || !method.desc.equals(factoryDescriptor)) return false;
        InsnList filter = new InsnList();
        filter.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, 2));
        filter.add(new MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESTATIC, LEGACY_ARMOR_TYPE_SHIM, "removeBody",
                    "([Lnet/minecraft/world/item/ArmorItem$Type;)[Lnet/minecraft/world/item/ArmorItem$Type;", false));
        filter.add(new VarInsnNode(org.objectweb.asm.Opcodes.ASTORE, 2));
        method.instructions.insert(filter);
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof org.objectweb.asm.tree.InsnNode constant)
                    || constant.getOpcode() != org.objectweb.asm.Opcodes.ICONST_4) continue;
            AbstractInsnNode array = nextMeaningful(instruction);
            if (!(array instanceof TypeInsnNode allocation)
                    || allocation.getOpcode() != org.objectweb.asm.Opcodes.ANEWARRAY
                    || !allocation.desc.equals("slimeknights/tconstruct/library/tools/definition/ToolDefinition")) {
                continue;
            }
            InsnList length = new InsnList();
            length.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, 2));
            length.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.ARRAYLENGTH));
            method.instructions.insertBefore(instruction, length);
            method.instructions.remove(instruction);
            return true;
        }
        return false;
    }

    private static boolean rewriteLegacyCoFHAttachedStemConstructor(String className, MethodNode method) {
        if (!className.equals(COFH_ATTACHED_STEM_BLOCK) || !method.name.equals("<init>")) return false;
        String legacyDescriptor = "(Lnet/neoforged/forgestuck/compat/LegacyStemGrownBlock;"
                + "Ljava/util/function/Supplier;"
                + "Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)V";
        String targetDescriptor = "(Lnet/minecraft/resources/ResourceKey;"
                + "Lnet/minecraft/resources/ResourceKey;"
                + "Lnet/minecraft/resources/ResourceKey;"
                + "Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)V";
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode call)
                    || call.getOpcode() != org.objectweb.asm.Opcodes.INVOKESPECIAL
                    || !call.owner.equals("net/minecraft/world/level/block/AttachedStemBlock")
                    || !call.name.equals("<init>")
                    || !call.desc.equals(legacyDescriptor)) continue;
            int fruitLocal = method.maxLocals++;
            int seedLocal = method.maxLocals++;
            int propertiesLocal = method.maxLocals++;
            InsnList arguments = new InsnList();
            arguments.add(new VarInsnNode(org.objectweb.asm.Opcodes.ASTORE, propertiesLocal));
            arguments.add(new VarInsnNode(org.objectweb.asm.Opcodes.ASTORE, seedLocal));
            arguments.add(new VarInsnNode(org.objectweb.asm.Opcodes.ASTORE, fruitLocal));
            arguments.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, fruitLocal));
            arguments.add(new MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESTATIC, LEGACY_STEM_BLOCK_SHIM,
                    "stemKey", "(Lnet/minecraft/world/level/block/Block;)Lnet/minecraft/resources/ResourceKey;", false));
            arguments.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, fruitLocal));
            arguments.add(new MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESTATIC, LEGACY_STEM_BLOCK_SHIM,
                    "fruitKey", "(Lnet/minecraft/world/level/block/Block;)Lnet/minecraft/resources/ResourceKey;", false));
            arguments.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, fruitLocal));
            arguments.add(new MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESTATIC, LEGACY_STEM_BLOCK_SHIM,
                    "seedKey", "(Lnet/minecraft/world/level/block/Block;)Lnet/minecraft/resources/ResourceKey;", false));
            arguments.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, propertiesLocal));
            method.instructions.insertBefore(call, arguments);
            method.maxStack = Math.max(method.maxStack, 5);
            for (AbstractInsnNode previous : method.instructions.toArray()) {
                if (previous == call) break;
                if (previous instanceof TypeInsnNode cast
                        && cast.getOpcode() == org.objectweb.asm.Opcodes.CHECKCAST
                        && cast.desc.equals("net/neoforged/forgestuck/compat/LegacyStemGrownBlock")) {
                    cast.desc = "net/minecraft/world/level/block/Block";
                }
            }
            call.desc = targetDescriptor;
            return true;
        }
        return false;
    }

    private static boolean rewriteLegacyBucketItemConstructor(MethodNode method) {
        String owner = "net/minecraft/world/item/BucketItem";
        String properties = "Lnet/minecraft/world/item/Item$Properties;";
        String oldDescriptor = "(Ljava/util/function/Supplier;" + properties + ")V";
        String newDescriptor = "(Lnet/minecraft/world/level/material/Fluid;" + properties + ")V";
        int propertiesLocal = method.maxLocals;
        boolean rewritten = false;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode call)
                    || call.getOpcode() != org.objectweb.asm.Opcodes.INVOKESPECIAL
                    || !call.owner.equals(owner)
                    || !call.name.equals("<init>")
                    || !call.desc.equals(oldDescriptor)) continue;
            InsnList adaptation = new InsnList();
            adaptation.add(new VarInsnNode(org.objectweb.asm.Opcodes.ASTORE, propertiesLocal));
            adaptation.add(new MethodInsnNode(org.objectweb.asm.Opcodes.INVOKEINTERFACE,
                    "java/util/function/Supplier", "get", "()Ljava/lang/Object;", true));
            adaptation.add(new TypeInsnNode(org.objectweb.asm.Opcodes.CHECKCAST,
                    "net/minecraft/world/level/material/Fluid"));
            adaptation.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, propertiesLocal));
            method.instructions.insertBefore(call, adaptation);
            call.desc = newDescriptor;
            rewritten = true;
        }
        if (rewritten) {
            method.maxLocals = propertiesLocal + 1;
            method.maxStack = Math.max(method.maxStack, 4);
        }
        return rewritten;
    }

    private static boolean rewriteLegacyBlocksLogFactory(MethodNode method) {
        String descriptor = "(Lnet/minecraft/world/level/material/MapColor;"
                + "Lnet/minecraft/world/level/material/MapColor;)"
                + "Lnet/minecraft/world/level/block/RotatedPillarBlock;";
        boolean rewritten = false;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode call)
                    || call.getOpcode() != org.objectweb.asm.Opcodes.INVOKESTATIC
                    || !call.owner.equals("net/minecraft/world/level/block/Blocks")
                    || !(call.name.equals("log") || call.name.equals("m_284204_"))
                    || !call.desc.equals(descriptor)) continue;
            call.owner = "net/neoforged/forgestuck/compat/LegacyBlockFactoryShim";
            call.name = "log";
            call.itf = false;
            rewritten = true;
        }
        return rewritten;
    }

    private static boolean rewriteLegacyBlocksLeavesFactory(MethodNode method) {
        String descriptor = "(Lnet/minecraft/world/level/block/SoundType;)"
                + "Lnet/minecraft/world/level/block/LeavesBlock;";
        boolean rewritten = false;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode call)
                    || call.getOpcode() != org.objectweb.asm.Opcodes.INVOKESTATIC
                    || !call.owner.equals("net/minecraft/world/level/block/Blocks")
                    || !(call.name.equals("leaves") || call.name.equals("m_152614_"))
                    || !call.desc.equals(descriptor)) continue;
            call.owner = "net/neoforged/forgestuck/compat/LegacyBlockFactoryShim";
            call.name = "leaves";
            call.itf = false;
            rewritten = true;
        }
        return rewritten;
    }

    private static boolean rewriteLegacyBlocksButtonFactory(MethodNode method) {
        String descriptor = "(Lnet/minecraft/world/level/block/state/properties/BlockSetType;"
                + "[Lnet/minecraft/world/flag/FeatureFlag;)Lnet/minecraft/world/level/block/ButtonBlock;";
        boolean rewritten = false;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode call)
                    || call.getOpcode() != org.objectweb.asm.Opcodes.INVOKESTATIC
                    || !call.owner.equals("net/minecraft/world/level/block/Blocks")
                    || !(call.name.equals("m_278156_") || call.name.equals("buttonBlock"))
                    || !call.desc.equals(descriptor)) continue;
            call.owner = "net/neoforged/forgestuck/compat/LegacyBlockFactoryShim";
            call.name = "button";
            call.itf = false;
            rewritten = true;
        }
        return rewritten;
    }

    private static boolean rewriteLegacyAttributeModifierConstructor(MethodNode method) {
        String owner = "net/minecraft/world/entity/ai/attributes/AttributeModifier";
        String oldDescriptor = "(Ljava/util/UUID;Ljava/lang/String;D"
                + "Lnet/minecraft/world/entity/ai/attributes/AttributeModifier$Operation;)V";
        String newDescriptor = "(Lnet/minecraft/resources/ResourceLocation;D"
                + "Lnet/minecraft/world/entity/ai/attributes/AttributeModifier$Operation;)V";
        int locals = method.maxLocals;
        boolean rewritten = false;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode call)
                    || call.getOpcode() != org.objectweb.asm.Opcodes.INVOKESPECIAL
                    || !call.owner.equals(owner)
                    || !call.name.equals("<init>")
                    || !call.desc.equals(oldDescriptor)) continue;
            int operationLocal = locals++;
            int amountLocal = locals;
            locals += 2;
            int nameLocal = locals++;
            int idLocal = locals++;
            InsnList adaptation = new InsnList();
            adaptation.add(new VarInsnNode(org.objectweb.asm.Opcodes.ASTORE, operationLocal));
            adaptation.add(new VarInsnNode(org.objectweb.asm.Opcodes.DSTORE, amountLocal));
            adaptation.add(new VarInsnNode(org.objectweb.asm.Opcodes.ASTORE, nameLocal));
            adaptation.add(new VarInsnNode(org.objectweb.asm.Opcodes.ASTORE, idLocal));
            adaptation.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, idLocal));
            adaptation.add(new MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESTATIC,
                    "net/neoforged/forgestuck/compat/LegacyAttributeModifierShim", "toId",
                    "(Ljava/util/UUID;)Lnet/minecraft/resources/ResourceLocation;", false));
            adaptation.add(new VarInsnNode(org.objectweb.asm.Opcodes.DLOAD, amountLocal));
            adaptation.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, operationLocal));
            method.instructions.insertBefore(call, adaptation);
            call.desc = newDescriptor;
            rewritten = true;
        }
        if (rewritten) {
            method.maxLocals = locals;
            method.maxStack = Math.max(method.maxStack, 6);
        }
        return rewritten;
    }

    private static boolean rewriteLegacyAttributeSupplierBuilder(MethodNode method) {
        String owner = "net/minecraft/world/entity/ai/attributes/AttributeSupplier$Builder";
        String attribute = "Lnet/minecraft/world/entity/ai/attributes/Attribute;";
        String holder = "Lnet/minecraft/core/Holder;";
        boolean rewritten = false;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode call)
                    || call.getOpcode() != org.objectweb.asm.Opcodes.INVOKEVIRTUAL
                    || !call.owner.equals(owner)
                    || !(call.name.equals("add") || call.name.equals("m_22268_"))) continue;
            boolean withValue = call.desc.equals("(" + attribute + "D)L" + owner + ";");
            boolean withoutValue = call.desc.equals("(" + attribute + ")L" + owner + ";");
            if (!withValue && !withoutValue) continue;
            int attributeLocal = method.maxLocals++;
            InsnList adaptation = new InsnList();
            if (withValue) {
                int valueLocal = method.maxLocals;
                method.maxLocals += 2;
                adaptation.add(new VarInsnNode(org.objectweb.asm.Opcodes.DSTORE, valueLocal));
                adaptation.add(new VarInsnNode(org.objectweb.asm.Opcodes.ASTORE, attributeLocal));
                adaptation.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, attributeLocal));
                adaptation.add(new MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESTATIC,
                        "net/minecraft/core/Holder", "direct", "(Ljava/lang/Object;)Lnet/minecraft/core/Holder;",
                        true));
                adaptation.add(new VarInsnNode(org.objectweb.asm.Opcodes.DLOAD, valueLocal));
                call.desc = "(" + holder + "D)L" + owner + ";";
            } else {
                adaptation.add(new VarInsnNode(org.objectweb.asm.Opcodes.ASTORE, attributeLocal));
                adaptation.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, attributeLocal));
                adaptation.add(new MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESTATIC,
                        "net/minecraft/core/Holder", "direct", "(Ljava/lang/Object;)Lnet/minecraft/core/Holder;",
                        true));
                call.desc = "(" + holder + ")L" + owner + ";";
            }
            method.instructions.insertBefore(call, adaptation);
            method.maxStack = Math.max(method.maxStack, 4);
            rewritten = true;
        }
        return rewritten;
    }

    private static boolean rewriteChargedSoilProtectedTick(ClassNode node, MethodNode method) {
        if (!node.name.equals("cofh/thermal/core/common/block/ChargedSoilBlock")
                || !method.name.equals("charge")
                || !method.desc.equals("(Lnet/minecraft/world/level/block/state/BlockState;"
                        + "Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V")) return false;
        boolean invokesTick = java.util.Arrays.stream(method.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .anyMatch(call -> call.owner.equals("net/minecraft/world/level/block/Block")
                        && (call.name.equals("m_213897_") || call.name.equals("tick")));
        if (!invokesTick) return false;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode call
                    && call.owner.equals("net/minecraft/world/level/block/state/BlockState")
                    && (call.name.equals("m_60734_") || call.name.equals("getBlock"))
                    && call.desc.equals("()Lnet/minecraft/world/level/block/Block;")) {
                method.instructions.insert(call, new TypeInsnNode(org.objectweb.asm.Opcodes.CHECKCAST, node.name));
                return true;
            }
        }
        return false;
    }

    private static void addLegacyCoFHStemSeedDrop(ClassNode node) {
        String descriptor = "(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;"
                + "Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/item/ItemStack;";
        if (node.methods.stream().anyMatch(method -> method.name.equals("getCloneItemStack")
                && method.desc.equals(descriptor))) return;
        MethodNode method = new MethodNode(org.objectweb.asm.Opcodes.ACC_PUBLIC, "getCloneItemStack", descriptor,
                null, null);
        method.instructions.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(org.objectweb.asm.Opcodes.GETFIELD, node.name, "seed",
                "Ljava/util/function/Supplier;"));
        method.instructions.add(new MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESTATIC, LEGACY_STEM_BLOCK_SHIM,
                "seedStack", "(Ljava/util/function/Supplier;)Lnet/minecraft/world/item/ItemStack;", false));
        method.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.ARETURN));
        method.maxStack = 1;
        method.maxLocals = 4;
        node.methods.add(method);
    }

    private static AbstractInsnNode nextMeaningful(AbstractInsnNode instruction) {
        AbstractInsnNode next = instruction.getNext();
        while (next != null && next.getOpcode() < 0) next = next.getNext();
        return next;
    }

    private static boolean rewriteLegacyIdMapWrite(MethodInsnNode call) {
        if (call.getOpcode() != Opcodes.INVOKEVIRTUAL
                || !call.owner.equals("net/minecraft/network/FriendlyByteBuf")
                || !call.name.equals("m_236818_")
                || !call.desc.equals("(Lnet/minecraft/core/IdMap;Ljava/lang/Object;)V")) return false;
        call.setOpcode(Opcodes.INVOKESTATIC);
        call.owner = "net/neoforged/forgestuck/compat/LegacyFriendlyByteBuf";
        call.name = "writeId";
        call.desc = "(Lnet/minecraft/network/FriendlyByteBuf;Lnet/minecraft/core/IdMap;Ljava/lang/Object;)V";
        call.itf = false;
        return true;
    }

    private static boolean rewriteLegacyFriendlyByteBufItemStack(MethodInsnNode call) {
        if (call.getOpcode() != Opcodes.INVOKEVIRTUAL
                || !call.owner.equals("net/minecraft/network/FriendlyByteBuf")) return false;
        if (call.name.equals("m_130055_")
                && call.desc.equals("(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/network/FriendlyByteBuf;")) {
            call.name = "writeItem";
            call.desc = "(Lnet/minecraft/network/FriendlyByteBuf;Lnet/minecraft/world/item/ItemStack;)"
                    + "Lnet/minecraft/network/FriendlyByteBuf;";
        } else if (call.name.equals("m_130267_")
                && call.desc.equals("()Lnet/minecraft/world/item/ItemStack;")) {
            call.name = "readItem";
            call.desc = "(Lnet/minecraft/network/FriendlyByteBuf;)Lnet/minecraft/world/item/ItemStack;";
        } else {
            return false;
        }
        call.setOpcode(Opcodes.INVOKESTATIC);
        call.owner = "net/neoforged/forgestuck/compat/LegacyFriendlyByteBuf";
        call.itf = false;
        return true;
    }

    private static boolean rewriteLegacyFriendlyByteBufWriteNbt(MethodInsnNode call) {
        if (call.getOpcode() != Opcodes.INVOKEVIRTUAL
                || !call.owner.equals("net/minecraft/network/FriendlyByteBuf")
                || !(call.name.equals("writeNbt") || call.name.equals("m_130079_"))
                || !call.desc.equals("(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/network/FriendlyByteBuf;")) {
            return false;
        }
        call.name = "writeNbt";
        call.desc = "(Lnet/minecraft/nbt/Tag;)Lnet/minecraft/network/FriendlyByteBuf;";
        return true;
    }

    private static boolean rewriteLegacyIngredientIsVanilla(MethodInsnNode call) {
        if (call.getOpcode() != Opcodes.INVOKEVIRTUAL
                || !call.owner.equals("net/minecraft/world/item/crafting/Ingredient")
                || !call.name.equals("isVanilla")
                || !call.desc.equals("()Z")) return false;
        call.setOpcode(Opcodes.INVOKESTATIC);
        call.owner = "net/neoforged/forgestuck/compat/LegacyIngredientBridge";
        call.desc = "(Lnet/minecraft/world/item/crafting/Ingredient;)Z";
        call.itf = false;
        return true;
    }

    private static boolean rewriteLegacyMantleItemStackShareTag(ClassNode node, MethodNode method) {
        if (!node.name.equals("slimeknights/mantle/data/loadable/common/ItemStackLoadable$NBTStack")) return false;
        String friendlyByteBuf = "net/minecraft/network/FriendlyByteBuf";
        String itemStack = "net/minecraft/world/item/ItemStack";
        String compoundTag = "net/minecraft/nbt/CompoundTag";
        String helper = "net/neoforged/forgestuck/compat/LegacyItemStackShim";
        boolean rewritten = false;
        if (method.name.equals("encode")
                && method.desc.equals("(L" + friendlyByteBuf + ";L" + itemStack + ";)V")) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode call)
                        || call.getOpcode() != Opcodes.INVOKEVIRTUAL
                        || !call.owner.equals(itemStack) || !call.name.equals("getShareTag")
                        || !call.desc.equals("()L" + compoundTag + ";")) continue;
                InsnList arguments = new InsnList();
                arguments.add(new VarInsnNode(Opcodes.ALOAD, 1));
                arguments.add(new org.objectweb.asm.tree.InsnNode(Opcodes.SWAP));
                method.instructions.insertBefore(call, arguments);
                call.setOpcode(Opcodes.INVOKESTATIC);
                call.owner = helper;
                call.name = "getShareTag";
                call.desc = "(L" + friendlyByteBuf + ";L" + itemStack + ";)L" + compoundTag + ";";
                call.itf = false;
                rewritten = true;
            }
        } else if (method.name.equals("decode")
                && method.desc.startsWith("(L" + friendlyByteBuf + ";")) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode call)
                        || call.getOpcode() != Opcodes.INVOKEVIRTUAL
                        || !call.owner.equals(itemStack) || !call.name.equals("readShareTag")
                        || !call.desc.equals("(L" + compoundTag + ";)V")) continue;
                InsnList arguments = new InsnList();
                arguments.add(new VarInsnNode(Opcodes.ALOAD, 1));
                arguments.add(new org.objectweb.asm.tree.InsnNode(Opcodes.DUP_X2));
                arguments.add(new org.objectweb.asm.tree.InsnNode(Opcodes.POP));
                method.instructions.insertBefore(call, arguments);
                call.setOpcode(Opcodes.INVOKESTATIC);
                call.owner = helper;
                call.name = "readShareTag";
                call.desc = "(L" + friendlyByteBuf + ";L" + itemStack + ";L" + compoundTag + ";)V";
                call.itf = false;
                rewritten = true;
            }
        }
        if (rewritten) method.maxStack++;
        return rewritten;
    }

    private static boolean rewriteLegacyCoFHTranslucentParticleRendering(ClassNode node, MethodNode method) {
        if (!node.name.equals("cofh/core/client/event/CoreClientEvents")
                || !method.name.equals("renderTranslucent")) return false;
        String tesselator = "com/mojang/blaze3d/vertex/Tesselator";
        String bufferBuilder = "com/mojang/blaze3d/vertex/BufferBuilder";
        String particleRenderType = "net/minecraft/client/particle/ParticleRenderType";
        String textureManager = "net/minecraft/client/renderer/texture/TextureManager";
        MethodInsnNode getBuilder = null;
        MethodInsnNode begin = null;
        MethodInsnNode end = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode call)) continue;
            if (call.owner.equals(tesselator) && call.name.equals("m_85915_")
                    && call.desc.equals("()L" + bufferBuilder + ";")) getBuilder = call;
            else if (call.owner.equals(particleRenderType) && call.name.equals("m_6505_")
                    && call.desc.equals("(L" + bufferBuilder + ";L" + textureManager + ";)V")) begin = call;
            else if (call.owner.equals(particleRenderType) && call.name.equals("m_6294_")
                    && call.desc.equals("(L" + tesselator + ";)V")) end = call;
        }
        if (getBuilder == null || begin == null || end == null) return false;

        AbstractInsnNode builderReceiver = previousMeaningful(getBuilder);
        AbstractInsnNode builderStore = nextMeaningful(getBuilder);
        AbstractInsnNode textureArgument = previousMeaningful(begin);
        AbstractInsnNode builderArgument = previousMeaningful(textureArgument);
        AbstractInsnNode endTesselator = previousMeaningful(end);
        AbstractInsnNode endRenderType = previousMeaningful(endTesselator);
        if (!(builderReceiver instanceof VarInsnNode receiverLoad)
                || receiverLoad.getOpcode() != Opcodes.ALOAD
                || !(builderStore instanceof VarInsnNode bufferStore)
                || bufferStore.getOpcode() != Opcodes.ASTORE
                || !(textureArgument instanceof VarInsnNode textureLoad)
                || textureLoad.getOpcode() != Opcodes.ALOAD
                || !(builderArgument instanceof VarInsnNode bufferLoad)
                || bufferLoad.getOpcode() != Opcodes.ALOAD
                || !(endTesselator instanceof VarInsnNode tesselatorLoad)
                || tesselatorLoad.getOpcode() != Opcodes.ALOAD
                || !(endRenderType instanceof VarInsnNode renderTypeLoad)
                || renderTypeLoad.getOpcode() != Opcodes.ALOAD
                || receiverLoad.var != tesselatorLoad.var) return false;

        int tesselatorLocal = receiverLoad.var;
        int bufferLocal = bufferStore.var;
        if (bufferLoad.var != bufferLocal) return false;
        method.instructions.set(builderReceiver, new InsnNode(Opcodes.ACONST_NULL));
        method.instructions.remove(getBuilder);

        method.instructions.set(builderArgument, new VarInsnNode(Opcodes.ALOAD, tesselatorLocal));
        begin.name = "begin";
        begin.desc = "(L" + tesselator + ";L" + textureManager + ";)L" + bufferBuilder + ";";
        begin.setOpcode(Opcodes.INVOKEINTERFACE);
        begin.itf = true;
        method.instructions.insert(begin, new VarInsnNode(Opcodes.ASTORE, bufferLocal));

        method.instructions.remove(endRenderType);
        method.instructions.remove(endTesselator);
        InsnList draw = new InsnList();
        draw.add(new VarInsnNode(Opcodes.ALOAD, bufferLocal));
        method.instructions.insertBefore(end, draw);
        end.setOpcode(Opcodes.INVOKEVIRTUAL);
        end.owner = bufferBuilder;
        end.name = "build";
        end.desc = "()Lcom/mojang/blaze3d/vertex/MeshData;";
        end.itf = false;
        method.instructions.insert(end, new MethodInsnNode(Opcodes.INVOKESTATIC,
                "com/mojang/blaze3d/vertex/BufferUploader", "drawWithShader",
                "(Lcom/mojang/blaze3d/vertex/MeshData;)V", false));
        return true;
    }

    private static boolean rewriteLegacyTesselatorBufferLifecycle(MethodNode method) {
        String tesselator = "com/mojang/blaze3d/vertex/Tesselator";
        String bufferBuilder = "com/mojang/blaze3d/vertex/BufferBuilder";
        String mode = "Lcom/mojang/blaze3d/vertex/VertexFormat$Mode;";
        String vertexFormat = "Lcom/mojang/blaze3d/vertex/VertexFormat;";
        String renderedBuffer = "Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;";
        String meshData = "Lcom/mojang/blaze3d/vertex/MeshData;";
        boolean rewritten = false;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode getBuilder)
                    || getBuilder.getOpcode() != Opcodes.INVOKEVIRTUAL
                    || !getBuilder.owner.equals(tesselator)
                    || !getBuilder.name.equals("m_85915_")
                    || !getBuilder.desc.equals("()L" + bufferBuilder + ";")) continue;

            AbstractInsnNode builderStore = nextMeaningful(getBuilder);
            if (!(builderStore instanceof VarInsnNode bufferLocal)
                    || bufferLocal.getOpcode() != Opcodes.ASTORE) continue;
            MethodInsnNode begin = null;
            for (AbstractInsnNode following = builderStore.getNext(); following != null;
                    following = following.getNext()) {
                if (following instanceof MethodInsnNode call
                        && call.owner.equals(bufferBuilder)
                        && call.name.equals("m_166779_")
                        && call.desc.equals("(" + mode + vertexFormat + ")V")) {
                    begin = call;
                    break;
                }
            }
            if (begin == null) continue;

            AbstractInsnNode formatArgument = previousMeaningful(begin);
            AbstractInsnNode modeArgument = previousMeaningful(formatArgument);
            AbstractInsnNode builderArgument = previousMeaningful(modeArgument);
            AbstractInsnNode tesselatorReceiver = previousMeaningful(getBuilder);
            boolean receiverIsLocal = tesselatorReceiver instanceof VarInsnNode receiverLoad
                    && receiverLoad.getOpcode() == Opcodes.ALOAD;
            boolean receiverIsAccessor = tesselatorReceiver instanceof MethodInsnNode receiverCall
                    && receiverCall.desc.endsWith(")L" + tesselator + ";");
            if (!(formatArgument instanceof FieldInsnNode)
                    || !(modeArgument instanceof FieldInsnNode)
                    || !(builderArgument instanceof VarInsnNode builderLoad)
                    || builderLoad.getOpcode() != Opcodes.ALOAD
                    || builderLoad.var != bufferLocal.var
                    || !receiverIsLocal && !receiverIsAccessor) continue;

            MethodInsnNode end = null;
            MethodInsnNode tesselatorEnd = null;
            for (AbstractInsnNode following = begin.getNext(); following != null; following = following.getNext()) {
                if (following instanceof MethodInsnNode call
                        && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && call.owner.equals(bufferBuilder)
                        && call.name.equals("m_231175_")
                        && call.desc.equals("()" + renderedBuffer)) {
                    end = call;
                    break;
                }
                if (following instanceof MethodInsnNode call
                        && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && call.owner.equals(tesselator)
                        && (call.name.equals("m_85914_") || call.name.equals("end"))
                        && call.desc.equals("()V")) {
                    tesselatorEnd = call;
                    break;
                }
            }
            if (end == null && tesselatorEnd == null) continue;
            MethodInsnNode draw = null;
            if (end != null) {
                AbstractInsnNode uploader = nextMeaningful(end);
                if (!(uploader instanceof MethodInsnNode uploaderCall)
                        || uploaderCall.getOpcode() != Opcodes.INVOKESTATIC
                        || !uploaderCall.owner.equals("com/mojang/blaze3d/vertex/BufferUploader")
                        || !uploaderCall.name.equals("m_231202_")
                        || !uploaderCall.desc.equals("(" + renderedBuffer + ")V")) continue;
                draw = uploaderCall;
            }

            InsnNode discardTesselator = new InsnNode(Opcodes.POP);
            method.instructions.set(getBuilder, discardTesselator);
            method.instructions.insert(discardTesselator, new InsnNode(Opcodes.ACONST_NULL));
            if (receiverIsLocal) {
                VarInsnNode receiverLoad = (VarInsnNode) tesselatorReceiver;
                method.instructions.set(builderLoad, new VarInsnNode(Opcodes.ALOAD, receiverLoad.var));
            } else {
                method.instructions.set(builderLoad, new MethodInsnNode(Opcodes.INVOKESTATIC,
                        tesselator, "getInstance", "()" + "L" + tesselator + ";", false));
            }
            begin.owner = tesselator;
            begin.name = "begin";
            begin.desc = "(" + mode + vertexFormat + ")L" + bufferBuilder + ";";
            begin.setOpcode(Opcodes.INVOKEVIRTUAL);
            begin.itf = false;
            method.instructions.insert(begin, new VarInsnNode(Opcodes.ASTORE, bufferLocal.var));
            if (end != null) {
                end.name = "build";
                end.desc = "()" + meshData;
                draw.name = "drawWithShader";
                draw.desc = "(" + meshData + ")V";
            } else {
                InsnNode discardEndTesselator = new InsnNode(Opcodes.POP);
                method.instructions.set(tesselatorEnd, discardEndTesselator);
                InsnList drawSequence = new InsnList();
                drawSequence.add(new VarInsnNode(Opcodes.ALOAD, bufferLocal.var));
                drawSequence.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, bufferBuilder,
                        "build", "()" + meshData, false));
                drawSequence.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                        "com/mojang/blaze3d/vertex/BufferUploader", "drawWithShader",
                        "(" + meshData + ")V", false));
                method.instructions.insert(discardEndTesselator, drawSequence);
            }
            rewritten = true;
        }
        return rewritten;
    }

    private static boolean rewriteLegacyCoFHStemConstructor(String className, MethodNode method) {
        if (!className.equals(COFH_STEM_BLOCK) || !method.name.equals("<init>")) return false;
        String legacyDescriptor = "(Lnet/neoforged/forgestuck/compat/LegacyStemGrownBlock;"
                + "Ljava/util/function/Supplier;"
                + "Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)V";
        String targetDescriptor = "(Lnet/minecraft/resources/ResourceKey;"
                + "Lnet/minecraft/resources/ResourceKey;"
                + "Lnet/minecraft/resources/ResourceKey;"
                + "Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)V";
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode call)
                    || call.getOpcode() != org.objectweb.asm.Opcodes.INVOKESPECIAL
                    || !call.owner.equals("net/minecraft/world/level/block/StemBlock")
                    || !call.name.equals("<init>")
                    || !call.desc.equals(legacyDescriptor)) continue;
            int fruitLocal = method.maxLocals++;
            int seedLocal = method.maxLocals++;
            int propertiesLocal = method.maxLocals++;
            InsnList arguments = new InsnList();
            arguments.add(new VarInsnNode(org.objectweb.asm.Opcodes.ASTORE, propertiesLocal));
            arguments.add(new VarInsnNode(org.objectweb.asm.Opcodes.ASTORE, seedLocal));
            arguments.add(new VarInsnNode(org.objectweb.asm.Opcodes.ASTORE, fruitLocal));
            arguments.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, fruitLocal));
            arguments.add(new MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESTATIC, LEGACY_STEM_BLOCK_SHIM,
                    "fruitKey", "(Lnet/minecraft/world/level/block/Block;)Lnet/minecraft/resources/ResourceKey;", false));
            arguments.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, fruitLocal));
            arguments.add(new MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESTATIC, LEGACY_STEM_BLOCK_SHIM,
                    "attachedStemKey",
                    "(Lnet/minecraft/world/level/block/Block;)Lnet/minecraft/resources/ResourceKey;", false));
            arguments.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, fruitLocal));
            arguments.add(new MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESTATIC, LEGACY_STEM_BLOCK_SHIM,
                    "seedKey",
                    "(Lnet/minecraft/world/level/block/Block;)Lnet/minecraft/resources/ResourceKey;", false));
            arguments.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, propertiesLocal));
            method.instructions.insertBefore(call, arguments);
            method.maxStack = Math.max(method.maxStack, 5);
            for (AbstractInsnNode previous : method.instructions.toArray()) {
                if (previous == call) break;
                if (previous instanceof TypeInsnNode cast
                        && cast.getOpcode() == org.objectweb.asm.Opcodes.CHECKCAST
                        && cast.desc.equals("net/neoforged/forgestuck/compat/LegacyStemGrownBlock")) {
                    cast.desc = "net/minecraft/world/level/block/Block";
                }
            }
            call.desc = targetDescriptor;
            return true;
        }
        return false;
    }

    private static boolean rewriteLegacyDoorBlockConstructor(MethodNode method) {
        String owner = "net/minecraft/world/level/block/DoorBlock";
        String properties = "Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;";
        String blockSetType = "Lnet/minecraft/world/level/block/state/properties/BlockSetType;";
        String oldDescriptor = "(" + properties + ")V";
        String newDescriptor = "(Lnet/minecraft/world/level/block/state/properties/BlockSetType;"
                + properties + ")V";
        boolean rewritten = false;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode call)
                    || call.getOpcode() != org.objectweb.asm.Opcodes.INVOKESPECIAL
                    || !owner.equals(call.owner)
                    || !"<init>".equals(call.name)) continue;
            if (!oldDescriptor.equals(call.desc)) continue;
            method.instructions.insertBefore(call, new org.objectweb.asm.tree.FieldInsnNode(
                    org.objectweb.asm.Opcodes.GETSTATIC,
                    "net/minecraft/world/level/block/state/properties/BlockSetType", "OAK", blockSetType));
            method.instructions.insertBefore(call,
                    new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.SWAP));
            call.desc = newDescriptor;
            method.maxStack++;
            rewritten = true;
        }
        return rewritten;
    }

    private static boolean rewriteLegacyWoodBlockConstructors(MethodNode method) {
        boolean rewritten = rewriteLegacyDoorBlockConstructor(method);
        String properties = "Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;";
        String blockSetType = "Lnet/minecraft/world/level/block/state/properties/BlockSetType;";
        String woodType = "Lnet/minecraft/world/level/block/state/properties/WoodType;";
        String[][] constructors = {
            {"net/minecraft/world/level/block/DoorBlock", blockSetType},
            {"net/minecraft/world/level/block/TrapDoorBlock", blockSetType},
            {"net/minecraft/world/level/block/FenceGateBlock", woodType},
            {"net/minecraft/world/level/block/StandingSignBlock", woodType},
            {"net/minecraft/world/level/block/WallSignBlock", woodType},
            {"net/minecraft/world/level/block/CeilingHangingSignBlock", woodType},
            {"net/minecraft/world/level/block/WallHangingSignBlock", woodType}
        };
        for (String[] constructor : constructors) {
            rewritten |= rewriteLegacyWoodConstructorArgumentOrder(method, constructor[0], properties, constructor[1]);
        }
        rewritten |= rewriteLegacyWoodButtonConstructor(method, properties, blockSetType);
        rewritten |= rewriteLegacyWoodPressurePlateConstructor(method, properties, blockSetType);
        return rewritten;
    }

    private static boolean rewriteLegacyWoodConstructorArgumentOrder(
            MethodNode method, String owner, String properties, String type) {
        String oldDescriptor = "(" + properties + type + ")V";
        String newDescriptor = "(" + type + properties + ")V";
        boolean rewritten = false;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode call)
                    || call.getOpcode() != org.objectweb.asm.Opcodes.INVOKESPECIAL
                    || !owner.equals(call.owner)
                    || !"<init>".equals(call.name)
                    || !oldDescriptor.equals(call.desc)) continue;
            method.instructions.insertBefore(call, new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.SWAP));
            call.desc = newDescriptor;
            rewritten = true;
        }
        return rewritten;
    }

    private static boolean rewriteLegacyWoodButtonConstructor(
            MethodNode method, String properties, String blockSetType) {
        String owner = "net/minecraft/world/level/block/ButtonBlock";
        String oldDescriptor = "(" + properties + blockSetType + "IZ)V";
        String newDescriptor = "(" + blockSetType + "I" + properties + ")V";
        boolean rewritten = false;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode call)
                    || call.getOpcode() != org.objectweb.asm.Opcodes.INVOKESPECIAL
                    || !owner.equals(call.owner)
                    || !"<init>".equals(call.name)
                    || !oldDescriptor.equals(call.desc)) continue;
            int locals = method.maxLocals;
            method.maxLocals += 4;
            var adaptation = new org.objectweb.asm.tree.InsnList();
            adaptation.add(new org.objectweb.asm.tree.VarInsnNode(org.objectweb.asm.Opcodes.ISTORE, locals + 2));
            adaptation.add(new org.objectweb.asm.tree.VarInsnNode(org.objectweb.asm.Opcodes.ISTORE, locals + 1));
            adaptation.add(new org.objectweb.asm.tree.VarInsnNode(org.objectweb.asm.Opcodes.ASTORE, locals));
            adaptation.add(new org.objectweb.asm.tree.VarInsnNode(org.objectweb.asm.Opcodes.ASTORE, locals + 3));
            adaptation.add(new org.objectweb.asm.tree.VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, locals));
            adaptation.add(new org.objectweb.asm.tree.VarInsnNode(org.objectweb.asm.Opcodes.ILOAD, locals + 1));
            adaptation.add(new org.objectweb.asm.tree.VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, locals + 3));
            method.instructions.insertBefore(call, adaptation);
            call.desc = newDescriptor;
            rewritten = true;
        }
        return rewritten;
    }

    private static boolean rewriteLegacyWoodPressurePlateConstructor(
            MethodNode method, String properties, String blockSetType) {
        String owner = "net/minecraft/world/level/block/PressurePlateBlock";
        String sensitivity = "Lnet/minecraft/world/level/block/PressurePlateBlock$Sensitivity;";
        String oldDescriptor = "(" + sensitivity + properties + blockSetType + ")V";
        String newDescriptor = "(" + blockSetType + properties + ")V";
        boolean rewritten = false;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode call)
                    || call.getOpcode() != org.objectweb.asm.Opcodes.INVOKESPECIAL
                    || !owner.equals(call.owner)
                    || !"<init>".equals(call.name)
                    || !oldDescriptor.equals(call.desc)) continue;
            int locals = method.maxLocals;
            method.maxLocals += 2;
            var adaptation = new org.objectweb.asm.tree.InsnList();
            adaptation.add(new org.objectweb.asm.tree.VarInsnNode(org.objectweb.asm.Opcodes.ASTORE, locals));
            adaptation.add(new org.objectweb.asm.tree.VarInsnNode(org.objectweb.asm.Opcodes.ASTORE, locals + 1));
            adaptation.add(new org.objectweb.asm.tree.VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, locals));
            adaptation.add(new org.objectweb.asm.tree.VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, locals + 1));
            method.instructions.insertBefore(call, adaptation);
            for (AbstractInsnNode previous : method.instructions.toArray()) {
                if (previous instanceof FieldInsnNode field
                        && "net/minecraft/world/level/block/PressurePlateBlock$Sensitivity".equals(field.owner)) {
                    method.instructions.remove(previous);
                }
            }
            call.desc = newDescriptor;
            rewritten = true;
        }
        return rewritten;
    }

    private static boolean rewriteLegacyAmethystClusterConstructor(MethodNode method) {
        String owner = "net/minecraft/world/level/block/AmethystClusterBlock";
        String oldDescriptor = "(IILnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)V";
        String newDescriptor = "(FFLnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)V";
        int temporaryLocals = method.maxLocals;
        boolean rewritten = false;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode call)
                    || call.getOpcode() != org.objectweb.asm.Opcodes.INVOKESPECIAL
                    || !call.owner.equals(owner) || !call.name.equals("<init>")
                    || !call.desc.equals(oldDescriptor)) continue;

            var conversion = new org.objectweb.asm.tree.InsnList();
            conversion.add(new org.objectweb.asm.tree.VarInsnNode(org.objectweb.asm.Opcodes.ASTORE,
                    temporaryLocals + 2));
            conversion.add(new org.objectweb.asm.tree.VarInsnNode(org.objectweb.asm.Opcodes.ISTORE,
                    temporaryLocals + 1));
            conversion.add(new org.objectweb.asm.tree.VarInsnNode(org.objectweb.asm.Opcodes.ISTORE,
                    temporaryLocals));
            conversion.add(new org.objectweb.asm.tree.VarInsnNode(org.objectweb.asm.Opcodes.ILOAD,
                    temporaryLocals));
            conversion.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.I2F));
            conversion.add(new org.objectweb.asm.tree.VarInsnNode(org.objectweb.asm.Opcodes.ILOAD,
                    temporaryLocals + 1));
            conversion.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.I2F));
            conversion.add(new org.objectweb.asm.tree.VarInsnNode(org.objectweb.asm.Opcodes.ALOAD,
                    temporaryLocals + 2));
            method.instructions.insertBefore(call, conversion);
            call.desc = newDescriptor;
            rewritten = true;
        }
        if (rewritten) method.maxLocals = temporaryLocals + 3;
        return rewritten;
    }

    private static AbstractInsnNode previousMeaningful(AbstractInsnNode instruction) {
        AbstractInsnNode previous = instruction.getPrevious();
        while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
        return previous;
    }

    private static boolean isLegacyIngredientArrayMethod(MethodInsnNode call) {
        return call.getOpcode() == org.objectweb.asm.Opcodes.INVOKEVIRTUAL
                && call.owner.equals("net/minecraft/world/item/crafting/Ingredient")
                && ((call.name.equals("m_43908_") || call.name.equals("getItems"))
                        && call.desc.equals("()[Lnet/minecraft/world/item/ItemStack;")
                    || (call.name.equals("m_43931_") || call.name.equals("getStackingIds"))
                        && call.desc.equals("()Lit/unimi/dsi/fastutil/ints/IntList;"));
    }

    private static boolean rewriteLegacyIngredientCodecMethod(MethodInsnNode call) {
        if (!call.owner.equals("net/minecraft/world/item/crafting/Ingredient")) return false;
        String bridge = LEGACY_INGREDIENT_BRIDGE;
        if ((call.name.equals("m_43917_") || call.name.equals("fromJson"))
                && call.getOpcode() == org.objectweb.asm.Opcodes.INVOKESTATIC
                && call.desc.equals("(Lcom/google/gson/JsonElement;)Lnet/minecraft/world/item/crafting/Ingredient;")) {
            call.name = "fromJson";
            call.desc = "(Lcom/google/gson/JsonElement;)Lnet/minecraft/world/item/crafting/Ingredient;";
        } else if ((call.name.equals("m_288218_") || call.name.equals("fromJson"))
                && call.getOpcode() == org.objectweb.asm.Opcodes.INVOKESTATIC
                && call.desc.equals(
                        "(Lcom/google/gson/JsonElement;Z)Lnet/minecraft/world/item/crafting/Ingredient;")) {
            call.name = "fromJson";
        } else if ((call.name.equals("m_43923_") || call.name.equals("toNetwork"))
                && call.getOpcode() == org.objectweb.asm.Opcodes.INVOKEVIRTUAL
                && call.desc.equals("(Lnet/minecraft/network/FriendlyByteBuf;)V")) {
            call.name = "toNetwork";
            call.desc = "(Lnet/minecraft/world/item/crafting/Ingredient;Lnet/minecraft/network/FriendlyByteBuf;)V";
        } else if ((call.name.equals("m_43940_") || call.name.equals("fromNetwork"))
                && call.getOpcode() == org.objectweb.asm.Opcodes.INVOKESTATIC
                && call.desc.equals("(Lnet/minecraft/network/FriendlyByteBuf;)Lnet/minecraft/world/item/crafting/Ingredient;")) {
            call.name = "fromNetwork";
        } else if ((call.name.equals("m_43942_") || call.name.equals("toJson"))
                && call.getOpcode() == org.objectweb.asm.Opcodes.INVOKEVIRTUAL
                && call.desc.equals("()Lcom/google/gson/JsonElement;")) {
            call.name = "toJson";
            call.desc = "(Lnet/minecraft/world/item/crafting/Ingredient;)Lcom/google/gson/JsonElement;";
        } else {
            return false;
        }
        call.setOpcode(org.objectweb.asm.Opcodes.INVOKESTATIC);
        call.owner = bridge;
        call.itf = false;
        return true;
    }

    private static boolean isLegacyLoadableIngredientSerialize(MethodInsnNode call) {
        return call.getOpcode() == org.objectweb.asm.Opcodes.INVOKEVIRTUAL
                && call.owner.equals(LOADABLE_INGREDIENT_SERIALIZER)
                && call.name.equals("serialize")
                && call.desc.equals("(Lnet/minecraft/world/item/crafting/Ingredient;)Lcom/google/gson/JsonObject;");
    }

    private boolean declaresMethod(String owner, String name, String descriptor) {
        Set<String> methods = declaredMethods.get(owner);
        return methods != null && methods.contains(name + descriptor);
    }

    private boolean isExternalInheritedSrgCall(MethodInsnNode call) {
        if (!declaredSupertypes.containsKey(call.owner) || declaresMethod(call.owner, call.name, call.desc)) {
            return false;
        }
        Set<String> visited = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>(declaredSupertypes.get(call.owner));
        while (!pending.isEmpty()) {
            String supertype = pending.removeFirst();
            if (!visited.add(supertype)) continue;
            if (declaresMethod(supertype, call.name, call.desc)) return false;
            pending.addAll(declaredSupertypes.getOrDefault(supertype, Set.of()));
        }
        return true;
    }

    private static boolean isLegacyBlockPropertiesCopy(MethodInsnNode call) {
        return call.getOpcode() == org.objectweb.asm.Opcodes.INVOKESTATIC
                && "net/minecraft/world/level/block/state/BlockBehaviour$Properties".equals(call.owner)
                && "m_60926_".equals(call.name)
                && "(Lnet/minecraft/world/level/block/state/BlockBehaviour;)Lnet/minecraft/world/level/block/state/"
                        .concat("BlockBehaviour$Properties;").equals(call.desc);
    }

    private static boolean rewriteLegacyUnbakedModelBake(MethodNode method, MethodInsnNode call) {
        String owner = "net/minecraft/client/resources/model/UnbakedModel";
        String legacyDescriptor = "(Lnet/minecraft/client/resources/model/ModelBaker;"
                + "Ljava/util/function/Function;Lnet/minecraft/client/resources/model/ModelState;"
                + "Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/resources/model/BakedModel;";
        if (call.getOpcode() != org.objectweb.asm.Opcodes.INVOKEINTERFACE
                || !call.owner.equals(owner)
                || !call.name.equals("m_7611_")
                || !call.desc.equals(legacyDescriptor)) return false;
        method.instructions.insertBefore(call, new InsnNode(org.objectweb.asm.Opcodes.POP));
        call.name = "bake";
        call.desc = "(Lnet/minecraft/client/resources/model/ModelBaker;"
                + "Ljava/util/function/Function;Lnet/minecraft/client/resources/model/ModelState;)"
                + "Lnet/minecraft/client/resources/model/BakedModel;";
        return true;
    }

    private static boolean isInheritedResourceLocationMethod(MethodInsnNode call) {
        return call.getOpcode() == org.objectweb.asm.Opcodes.INVOKEVIRTUAL
                && (call.name + call.desc).equals(INHERITED_RESOURCE_LOCATION_WITH_SUFFIX);
    }

    private static boolean isInheritedItemDefaultInstance(MethodInsnNode call) {
        return call.getOpcode() == org.objectweb.asm.Opcodes.INVOKEVIRTUAL
                && call.name.equals("m_7968_")
                && call.desc.equals("()L" + ITEMSTACK + ";")
                && !call.owner.equals(ITEM);
    }

    private static boolean isInheritedItemIsFoil(MethodInsnNode call) {
        return call.getOpcode() == org.objectweb.asm.Opcodes.INVOKEVIRTUAL
                && call.name.equals("m_41113_")
                && call.desc.equals("(L" + ITEMSTACK + ";)Z")
                && !call.owner.equals(ITEM);
    }

    /**
     * A legacy class implementing a game interface/superclass method under its SRG name never satisfies the 1.21.1
     * (named) method; add a named bridge that delegates to the SRG-named implementation.
     */
    private boolean addOverrideBridges(ClassNode node, Set<String> remapped) {
        List<String> supers = new java.util.ArrayList<>(node.interfaces);
        if (node.superName != null) supers.add(node.superName);
        List<MethodNode> bridges = new java.util.ArrayList<>();
        for (MethodNode m : node.methods) {
            if (!LegacyNameMappings.looksLikeSrg(m.name) || m.name.equals("<init>") || m.name.equals("<clinit>")) continue;
            if ((m.access & (org.objectweb.asm.Opcodes.ACC_STATIC | org.objectweb.asm.Opcodes.ACC_ABSTRACT
                    | org.objectweb.asm.Opcodes.ACC_PRIVATE)) != 0) continue;
            List<String> candidates = new java.util.ArrayList<>(supers);
            addTransitiveSupertypes(node.name, candidates);
            if (m.name.equals("m_7912_") && m.desc.equals("()Ljava/lang/String;")) {
                candidates.add(STRING_REPRESENTABLE);
            }
            if (m.name.equals("m_7208_")
                    && m.desc.equals("(ILnet/minecraft/world/entity/player/Inventory;"
                            + "Lnet/minecraft/world/entity/player/Player;)"
                            + "Lnet/minecraft/world/inventory/AbstractContainerMenu;")) {
                candidates.add("net/minecraft/world/MenuProvider");
            }
            if (m.name.equals("m_7180_") && m.desc.equals(FLUID_STATE_DEFINITION_DESC)) candidates.add(FLUID);
            if ((m.name.equals("m_7444_") && m.desc.equals(
                            "(Lnet/minecraft/world/level/material/FluidState;)Z"))
                    || (m.name.equals("m_7430_") && m.desc.equals(
                            "(Lnet/minecraft/world/level/material/FluidState;)I"))) {
                candidates.add(FLUID);
            }
            if (isBlockFluidStateMethod(m.name, m.desc)) {
                candidates.add("net/minecraft/world/level/block/Block");
            }
            if (m.name.equals("m_7926_") && m.desc.equals(BLOCK_STATE_DEFINITION_DESC)) {
                candidates.add("net/minecraft/world/level/block/Block");
            }
            if (hasSupertype(node.name, "net/minecraft/world/item/crafting/Recipe")
                    || hasLegacyForestryRecipe(node.name)) {
                candidates.add("net/minecraft/world/item/crafting/Recipe");
            }
            if (hasSupertype(node.name, "net/minecraft/world/Container")) {
                candidates.add("net/minecraft/world/Container");
            }
            for (String sup : candidates) {
                if (!ownerIsGame(sup) && !sup.equals(STRING_REPRESENTABLE)) continue;
                String mapped = mappings.map(sup, m.name, m.desc);
                if (mapped == null || mapped.equals(m.name)) continue;
                boolean exists = node.methods.stream().anyMatch(o -> o.name.equals(mapped) && o.desc.equals(m.desc))
                        || bridges.stream().anyMatch(o -> o.name.equals(mapped) && o.desc.equals(m.desc));
                if (exists) break;
                MethodNode b = new MethodNode(org.objectweb.asm.Opcodes.ACC_PUBLIC | org.objectweb.asm.Opcodes.ACC_SYNTHETIC,
                        mapped, m.desc, null, null);
                org.objectweb.asm.Type[] args = org.objectweb.asm.Type.getArgumentTypes(m.desc);
                org.objectweb.asm.Type ret = org.objectweb.asm.Type.getReturnType(m.desc);
                b.visitCode();
                b.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 0);
                int slot = 1;
                for (org.objectweb.asm.Type a : args) {
                    b.visitVarInsn(a.getOpcode(org.objectweb.asm.Opcodes.ILOAD), slot);
                    slot += a.getSize();
                }
                b.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKEVIRTUAL, node.name, m.name, m.desc, false);
                b.visitInsn(ret.getOpcode(org.objectweb.asm.Opcodes.IRETURN));
                b.visitMaxs(Math.max(slot, ret.getSize()), slot);
                b.visitEnd();
                bridges.add(b);
                remapped.add(m.name + "->" + mapped + " (bridge)");
                break;
            }
        }
        node.methods.addAll(bridges);
        return addLegacyForestryRecipeResultBridge(node, remapped) || !bridges.isEmpty();
    }

    private void addTransitiveSupertypes(String className, List<String> candidates) {
        Set<String> visited = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>(declaredSupertypes.getOrDefault(className, Set.of()));
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!visited.add(current)) continue;
            candidates.add(current);
            pending.addAll(declaredSupertypes.getOrDefault(current, Set.of()));
        }
    }

    private boolean addLegacyUnbakedModelBakeBridge(ClassNode node, Set<String> remapped) {
        String unbakedModel = "net/minecraft/client/resources/model/UnbakedModel";
        if (!hasSupertype(node.name, unbakedModel)) return false;
        String baker = "net/minecraft/client/resources/model/ModelBaker";
        String modelState = "net/minecraft/client/resources/model/ModelState";
        String bakedModel = "net/minecraft/client/resources/model/BakedModel";
        String legacyDescriptor = "(L" + baker + ";Ljava/util/function/Function;L" + modelState
                + ";Lnet/minecraft/resources/ResourceLocation;)L" + bakedModel + ";";
        String targetDescriptor = "(L" + baker + ";Ljava/util/function/Function;L" + modelState
                + ";)L" + bakedModel + ";";
        MethodNode legacyBake = node.methods.stream().filter(method ->
                method.desc.equals(legacyDescriptor)
                        && (method.name.equals("bake") || method.name.equals("m_7611_")))
                .findFirst().orElse(null);
        boolean hasTargetBake = node.methods.stream().anyMatch(method ->
                method.name.equals("bake") && method.desc.equals(targetDescriptor));
        if (legacyBake == null || hasTargetBake) return false;

        MethodNode bridge = new MethodNode(org.objectweb.asm.Opcodes.ACC_PUBLIC
                | org.objectweb.asm.Opcodes.ACC_SYNTHETIC, "bake", targetDescriptor, null, null);
        bridge.visitCode();
        bridge.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 0);
        bridge.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 1);
        bridge.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 2);
        bridge.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 3);
        bridge.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESTATIC,
                "net/neoforged/forgestuck/compat/LegacyModelResourceLocations", "missingModelLocation",
                "()Lnet/minecraft/resources/ResourceLocation;", false);
        bridge.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKEVIRTUAL, node.name, legacyBake.name,
                legacyDescriptor, false);
        bridge.visitInsn(org.objectweb.asm.Opcodes.ARETURN);
        bridge.visitMaxs(5, 4);
        bridge.visitEnd();
        node.methods.add(bridge);
        remapped.add("UnbakedModel.bake -> legacy model-location bake bridge");
        return true;
    }

    private boolean addLegacySimpleCraftingRecipeConstructorBridge(ClassNode node, Set<String> remapped) {
        String customRecipe = "net/minecraft/world/item/crafting/CustomRecipe";
        if (!customRecipe.equals(node.superName) && !hasSupertype(node.name, customRecipe)) return false;
        String resourceLocation = "net/minecraft/resources/ResourceLocation";
        String category = "net/minecraft/world/item/crafting/CraftingBookCategory";
        String legacyDescriptor = "(L" + resourceLocation + ";L" + category + ";)V";
        String targetDescriptor = "(L" + category + ";)V";
        boolean hasLegacyConstructor = node.methods.stream().anyMatch(method ->
                method.name.equals("<init>") && method.desc.equals(legacyDescriptor));
        boolean hasTargetConstructor = node.methods.stream().anyMatch(method ->
                method.name.equals("<init>") && method.desc.equals(targetDescriptor));
        if (!hasLegacyConstructor || hasTargetConstructor) return false;

        MethodNode bridge = new MethodNode(org.objectweb.asm.Opcodes.ACC_PUBLIC
                | org.objectweb.asm.Opcodes.ACC_SYNTHETIC, "<init>", targetDescriptor, null, null);
        bridge.visitCode();
        bridge.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 0);
        bridge.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESTATIC,
                "net/neoforged/forgestuck/compat/LegacyRecipeSerializerCodec", "legacyRecipeId",
                "()L" + resourceLocation + ";", false);
        bridge.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 1);
        bridge.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESPECIAL, node.name, "<init>",
                legacyDescriptor, false);
        bridge.visitInsn(org.objectweb.asm.Opcodes.RETURN);
        bridge.visitMaxs(3, 2);
        bridge.visitEnd();
        node.methods.add(bridge);
        remapped.add("CustomRecipe legacy constructor -> category-only constructor bridge");
        return true;
    }

    private static boolean rewriteLegacySimpleCraftingRecipeFactory(
            org.objectweb.asm.tree.InvokeDynamicInsnNode indy) {
        String factory = "net/minecraft/world/item/crafting/SimpleCraftingRecipeSerializer$Factory";
        String resourceLocation = "Lnet/minecraft/resources/ResourceLocation;";
        String category = "Lnet/minecraft/world/item/crafting/CraftingBookCategory;";
        String recipe = "Lnet/minecraft/world/item/crafting/CraftingRecipe;";
        String legacySam = "(" + resourceLocation + category + ")" + recipe;
        if (!(indy.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")
                        && (indy.bsm.getName().equals("metafactory") || indy.bsm.getName().equals("altMetafactory")))
                || !indy.desc.equals("()L" + factory + ";")
                || indy.bsmArgs.length < 3
                || !(indy.bsmArgs[0] instanceof org.objectweb.asm.Type samType)
                || !samType.getDescriptor().equals(legacySam)
                || !(indy.bsmArgs[1] instanceof org.objectweb.asm.Handle constructor)
                || constructor.getTag() != org.objectweb.asm.Opcodes.H_NEWINVOKESPECIAL
                || !constructor.getName().equals("<init>")
                || !constructor.getDesc().equals("(" + resourceLocation + category + ")V")
                || !(indy.bsmArgs[2] instanceof org.objectweb.asm.Type instantiatedType)
                || !instantiatedType.getDescriptor().equals("(" + resourceLocation + category + ")L"
                        + constructor.getOwner() + ";")) return false;

        String currentSam = "(" + category + ")" + recipe;
        String currentConstructor = "(" + category + ")V";
        indy.name = "create";
        indy.bsmArgs[0] = org.objectweb.asm.Type.getMethodType(currentSam);
        indy.bsmArgs[1] = new org.objectweb.asm.Handle(org.objectweb.asm.Opcodes.H_NEWINVOKESPECIAL,
                constructor.getOwner(), "<init>", currentConstructor, constructor.isInterface());
        indy.bsmArgs[2] = org.objectweb.asm.Type.getMethodType("(" + category + ")L"
                + constructor.getOwner() + ";");
        return true;
    }

    private boolean isLegacyCriterionContextClass(ClassNode node) {
        String simpleTrigger = "net/minecraft/advancements/critereon/SimpleCriterionTrigger";
        String legacyInstance = "net/minecraft/advancements/critereon/AbstractCriterionTriggerInstance";
        return simpleTrigger.equals(node.superName)
                || legacyInstance.equals(node.superName)
                || "net/neoforged/forgestuck/compat/LegacyCriterionInstance".equals(node.superName)
                || hasSupertype(node.name, simpleTrigger)
                || hasSupertype(node.name, legacyInstance)
                || hasSupertype(node.name, "net/neoforged/forgestuck/compat/LegacyCriterionInstance");
    }

    private static void rewriteLegacyCriterionContextTypes(ClassNode node) {
        for (FieldNode field : node.fields) {
            field.desc = rewriteLegacyCriterionContextDescriptor(field.desc);
        }
        for (MethodNode method : node.methods) {
            method.desc = rewriteLegacyCriterionContextDescriptor(method.desc);
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode call) {
                    call.desc = rewriteLegacyCriterionContextDescriptor(call.desc);
                } else if (instruction instanceof FieldInsnNode field) {
                    field.desc = rewriteLegacyCriterionContextDescriptor(field.desc);
                } else if (instruction instanceof org.objectweb.asm.tree.InvokeDynamicInsnNode indy) {
                    indy.desc = rewriteLegacyCriterionContextDescriptor(indy.desc);
                    for (int i = 0; i < indy.bsmArgs.length; i++) {
                        if (indy.bsmArgs[i] instanceof org.objectweb.asm.Type type
                                && type.getSort() == org.objectweb.asm.Type.METHOD) {
                            indy.bsmArgs[i] = org.objectweb.asm.Type.getMethodType(
                                    rewriteLegacyCriterionContextDescriptor(type.getDescriptor()));
                        } else if (indy.bsmArgs[i] instanceof org.objectweb.asm.Handle handle) {
                            String descriptor = rewriteLegacyCriterionContextDescriptor(handle.getDesc());
                            if (!descriptor.equals(handle.getDesc())) {
                                indy.bsmArgs[i] = new org.objectweb.asm.Handle(handle.getTag(), handle.getOwner(),
                                        handle.getName(), descriptor, handle.isInterface());
                            }
                        }
                    }
                } else if (instruction instanceof org.objectweb.asm.tree.LdcInsnNode ldc
                        && ldc.cst instanceof org.objectweb.asm.Handle handle) {
                    String descriptor = rewriteLegacyCriterionContextDescriptor(handle.getDesc());
                    if (!descriptor.equals(handle.getDesc())) {
                        ldc.cst = new org.objectweb.asm.Handle(handle.getTag(), handle.getOwner(),
                                handle.getName(), descriptor, handle.isInterface());
                    }
                }
            }
        }
    }

    private static String rewriteLegacyCriterionContextDescriptor(String descriptor) {
        return descriptor
                .replace("Lnet/minecraft/advancements/critereon/DeserializationContext;", "Ljava/lang/Object;")
                .replace("Lnet/minecraft/advancements/critereon/SerializationContext;", "Ljava/lang/Object;");
    }

    private boolean addLegacyCriterionCodecBridge(ClassNode node, Set<String> remapped) {
        String simpleTrigger = "net/minecraft/advancements/critereon/SimpleCriterionTrigger";
        if (!simpleTrigger.equals(node.superName) && !hasSupertype(node.name, simpleTrigger)) return false;
        String descriptor = "()Lcom/mojang/serialization/Codec;";
        if (node.methods.stream().anyMatch(method -> method.name.equals("codec") && method.desc.equals(descriptor))) {
            return false;
        }
        MethodNode bridge = new MethodNode(org.objectweb.asm.Opcodes.ACC_PUBLIC
                | org.objectweb.asm.Opcodes.ACC_SYNTHETIC, "codec", descriptor, null, null);
        bridge.visitCode();
        bridge.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 0);
        bridge.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESTATIC,
                "net/neoforged/forgestuck/compat/LegacyCriteriaTriggers", "codec",
                "(L" + simpleTrigger + ";)Lcom/mojang/serialization/Codec;", false);
        bridge.visitInsn(org.objectweb.asm.Opcodes.ARETURN);
        bridge.visitMaxs(1, 1);
        bridge.visitEnd();
        node.methods.add(bridge);
        remapped.add("CriterionTrigger.codec -> legacy JSON adapter");
        return true;
    }

    private boolean hasLegacyForestryRecipe(String className) {
        return hasSupertype(className, "forestry/api/recipes/IForestryRecipe")
                || hasSupertype(className, "forestry/api/core/machines/IForestryRecipe");
    }

    private static boolean addLegacyForestryRecipeResultBridge(ClassNode node, Set<String> remapped) {
        String legacyDescriptor =
                "(Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;";
        String bridgeDescriptor =
                "(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;";
        if (!node.methods.stream().anyMatch(method -> method.name.equals("m_8043_")
                && method.desc.equals(legacyDescriptor))
                || node.methods.stream().anyMatch(method -> method.name.equals("getResultItem")
                        && method.desc.equals(bridgeDescriptor))) {
            return false;
        }
        MethodNode bridge = new MethodNode(org.objectweb.asm.Opcodes.ACC_PUBLIC
                | org.objectweb.asm.Opcodes.ACC_SYNTHETIC, "getResultItem", bridgeDescriptor, null, null);
        bridge.instructions.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, 0));
        bridge.instructions.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, 1));
        bridge.instructions.add(new TypeInsnNode(org.objectweb.asm.Opcodes.CHECKCAST, "net/minecraft/core/RegistryAccess"));
        bridge.instructions.add(new MethodInsnNode(org.objectweb.asm.Opcodes.INVOKEVIRTUAL, node.name,
                "m_8043_", legacyDescriptor, false));
        bridge.instructions.add(new InsnNode(org.objectweb.asm.Opcodes.ARETURN));
        bridge.maxStack = 2;
        bridge.maxLocals = 2;
        node.methods.add(bridge);
        remapped.add("m_8043_ -> Recipe.getResultItem(HolderLookup.Provider) (bridge)");
        return true;
    }

    private boolean hasSupertype(String className, String target) {
        Set<String> visited = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>(declaredSupertypes.getOrDefault(className, Set.of()));
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (current.equals(target)) return true;
            if (!visited.add(current)) continue;
            pending.addAll(declaredSupertypes.getOrDefault(current, Set.of()));
        }
        return false;
    }

    private boolean remapAnnotations(List<AnnotationNode> annotations, Set<String> remapped) {
        if (annotations == null) return false;
        boolean changed = false;
        for (AnnotationNode a : annotations) {
            if (MixinClassSupport.isMixinAnnotation(a.desc)) {
                changed |= MixinClassSupport.remapAnnotation(a, mappings, remapped);
            }
        }
        return changed;
    }

    private static boolean hasAnnotation(List<AnnotationNode> list, String desc) {
        if (list == null) return false;
        for (AnnotationNode a : list) if (desc.equals(a.desc)) return true;
        return false;
    }

    private static boolean isShadow(List<AnnotationNode> a, List<AnnotationNode> b) {
        return hasAnnotation(a, MixinClassSupport.SHADOW) || hasAnnotation(b, MixinClassSupport.SHADOW);
    }
}
