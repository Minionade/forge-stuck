package net.neoforged.forgestuck.transformer.jar;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
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
    private static final String FLUID_STATE_DEFINITION_DESC =
            "(Lnet/minecraft/world/level/block/state/StateDefinition$Builder;)V";
    private static final String BLOCK_STATE_DEFINITION_DESC =
            "(Lnet/minecraft/world/level/block/state/StateDefinition$Builder;)V";
    private static final String ITEMSTACK = "net/minecraft/world/item/ItemStack";
    private static final String ITEMSTACK_TAG = "()Lnet/minecraft/nbt/CompoundTag;";
    private static final String LEGACY_ITEMSTACK_SHIM =
            "net/neoforged/forgestuck/compat/LegacyItemStackShim";
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
    private static final String LEGACY_LOOT_DATA_TYPE =
            "net/neoforged/forgestuck/compat/LegacyLootDataType";
    private static final String LEGACY_MODEL_RESOURCE_LOCATIONS =
            "net/neoforged/forgestuck/compat/LegacyModelResourceLocations";

    private final LegacyNameMappings mappings;
    private final Map<String, Set<String>> declaredMethods = new ConcurrentHashMap<>();

    public SrgMemberRemapStep() {
        this(LegacyNameMappings.defaults());
    }

    private static boolean isLegacyArmorTypeValues(MethodInsnNode call) {
        return call.getOpcode() == org.objectweb.asm.Opcodes.INVOKESTATIC
                && call.owner.equals("net/minecraft/world/item/ArmorItem$Type")
                && call.name.equals("values")
                && call.desc.equals("()[Lnet/minecraft/world/item/ArmorItem$Type;");
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
                && !raw.contains(LEGACY_SPRITE_SOURCES)) return bytes;

        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        boolean isMixin = hasAnnotation(node.invisibleAnnotations, MixinClassSupport.MIXIN)
                || hasAnnotation(node.visibleAnnotations, MixinClassSupport.MIXIN);

        Set<String> remapped = new TreeSet<>();
        boolean remapProjectileDispenseBehavior = raw.contains(LEGACY_PROJECTILE_DISPENSE_BEHAVIOR);
        boolean remapFireworkShape = raw.contains(LEGACY_FIREWORK_SHAPE);
        boolean remapLegacyPotionMix = raw.contains(LEGACY_POTION_MIX);
        boolean modified = remapProjectileDispenseBehavior || remapFireworkShape || remapLegacyPotionMix;
        if (remapProjectileDispenseBehavior) {
            remapped.add("AbstractProjectileDispenseBehavior -> LegacyProjectileDispenseBehavior");
        }
        if (remapFireworkShape) {
            remapped.add("FireworkRocketItem.Shape -> FireworkExplosion.Shape");
        }
        if (remapLegacyPotionMix) {
            remapped.add("PotionBrewing.Mix -> legacy brewing registration queue");
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
            modified |= addOverrideBridges(node, remapped);
            modified |= addLegacyGeometryBakeBridge(node, remapped);
        }

        for (MethodNode m : node.methods) {
            java.util.Map<AbstractInsnNode, String> unmapped = new java.util.LinkedHashMap<>();
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
            if (removeUnsupportedTConstructProtectionSlotMutation(node, m)) {
                modified = true;
                remapped.add("data-driven protection enchantment slot defaults");
                continue;
            }
            for (AbstractInsnNode insn : m.instructions.toArray()) {
                if (insn instanceof MethodInsnNode call && nodeIsInterfaceVirtualCall(node, call)) {
                    call.setOpcode(org.objectweb.asm.Opcodes.INVOKEINTERFACE);
                    call.itf = true;
                    modified = true;
                    remapped.add("interface self-call INVOKEVIRTUAL -> INVOKEINTERFACE");
                } else if (insn instanceof MethodInsnNode call && rewriteLegacyGuiOverlayPost(m, call)) {
                    modified = true;
                    remapped.add("legacy RenderGuiOverlayEvent.Post -> RenderGuiLayerEvent.Post");
                } else if (insn instanceof TypeInsnNode type && rewriteLegacyDebugBufferBuilderAllocation(node, type)) {
                    modified = true;
                    remapped.add("DebugRenderer BufferBuilder allocation -> ByteBufferBuilder");
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
                } else if (insn instanceof MethodInsnNode call && isLegacyItemStackGetTag(call)) {
                    call.setOpcode(org.objectweb.asm.Opcodes.INVOKESTATIC);
                    call.owner = LEGACY_ITEMSTACK_SHIM;
                    call.name = "getTag";
                    call.desc = "(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/nbt/CompoundTag;";
                    call.itf = false;
                    modified = true;
                    remapped.add("ItemStack.getTag -> LegacyItemStackShim.getTag");
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
                } else if (insn instanceof MethodInsnNode call && rewriteLegacyLootDataCall(m.instructions, call)) {
                    modified = true;
                    remapped.add("legacy loot data lookup -> reloadable server registries");
                } else if (insn instanceof MethodInsnNode call && LegacyNameMappings.looksLikeSrg(call.name)) {
                    boolean declaredLegacySuperCall = isBlockStateDefinitionMethod(call)
                            && declaresMethod(call.owner, call.name, call.desc);
                    String mapped = declaredLegacySuperCall ? null : mappings.map(call.owner, call.name, call.desc);
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
                    if (own || ownerIsGame(call.owner) || inherited || isInheritedResourceLocationMethod(call)
                            || isFluidStateDefinitionMethod(call) || isBlockStateDefinitionMethod(call)
                            || isBlockFluidStateMethod(call)) {
                        remapped.add(call.name + "->" + mapped);
                        call.name = mapped;
                        modified = true;
                    }
                } else if (insn instanceof org.objectweb.asm.tree.InvokeDynamicInsnNode indy && indy.bsmArgs != null) {
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
                        if (indy.bsmArgs[i] instanceof org.objectweb.asm.Handle h && isLegacyItemStackGetTag(h)) {
                            indy.bsmArgs[i] = legacyItemStackGetTagHandle();
                            modified = true;
                            remapped.add("ItemStack.getTag handle -> LegacyItemStackShim.getTag");
                        } else if (indy.bsmArgs[i] instanceof org.objectweb.asm.Handle h
                                && legacyVertexConsumerShimHandle(h) != null) {
                            indy.bsmArgs[i] = legacyVertexConsumerShimHandle(h);
                            modified = true;
                            remapped.add("legacy VertexConsumer method handle -> LegacyVertexConsumerShim");
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
                        && ldc.cst instanceof org.objectweb.asm.Handle h && isLegacyItemStackGetTag(h)) {
                    ldc.cst = legacyItemStackGetTagHandle();
                    modified = true;
                    remapped.add("ItemStack.getTag handle -> LegacyItemStackShim.getTag");
                } else if (insn instanceof org.objectweb.asm.tree.LdcInsnNode ldc
                        && ldc.cst instanceof org.objectweb.asm.Handle h
                        && legacyVertexConsumerShimHandle(h) != null) {
                    ldc.cst = legacyVertexConsumerShimHandle(h);
                    modified = true;
                    remapped.add("legacy VertexConsumer method handle -> LegacyVertexConsumerShim");
                } else if (insn instanceof org.objectweb.asm.tree.LdcInsnNode ldc
                        && ldc.cst instanceof org.objectweb.asm.Handle h
                        && isLegacyDamageSourceCreativePlayer(h)) {
                    ldc.cst = legacyDamageSourceCreativePlayerHandle();
                    modified = true;
                    remapped.add("DamageSource.m_269014_ handle -> LegacyDamageSource.isCreativePlayer");
                } else if (insn instanceof FieldInsnNode field && LegacyNameMappings.looksLikeSrg(field.name)) {
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
                    if (own || ownerIsGame(field.owner) || inherited) {
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
        boolean remappedPost = call.getOpcode() == org.objectweb.asm.Opcodes.INVOKESTATIC
                && (call.owner.equals("net/neoforged/forgestuck/gamecompat/LegacyEventBusPost")
                        || call.owner.equals("net/neoforged/forgestuck/compat/LegacyEventBusPost"))
                && call.name.equals("post")
                && call.desc.equals("(Lnet/neoforged/bus/api/IEventBus;Lnet/neoforged/bus/api/Event;)Z");
        if (!legacyPost && !remappedPost) {
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

    private static boolean isLegacyItemStackGetTag(MethodInsnNode call) {
        return call.getOpcode() == org.objectweb.asm.Opcodes.INVOKEVIRTUAL
                && call.owner.equals(ITEMSTACK)
                && (call.name.equals("m_41783_") || call.name.equals("getTag"))
                && call.desc.equals(ITEMSTACK_TAG);
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

    private static boolean isLegacyBlockPropertiesCopy(MethodInsnNode call) {
        return call.getOpcode() == org.objectweb.asm.Opcodes.INVOKESTATIC
                && "net/minecraft/world/level/block/state/BlockBehaviour$Properties".equals(call.owner)
                && "m_60926_".equals(call.name)
                && "(Lnet/minecraft/world/level/block/state/BlockBehaviour;)Lnet/minecraft/world/level/block/state/"
                        .concat("BlockBehaviour$Properties;").equals(call.desc);
    }

    private static boolean isInheritedResourceLocationMethod(MethodInsnNode call) {
        return call.getOpcode() == org.objectweb.asm.Opcodes.INVOKEVIRTUAL
                && (call.name + call.desc).equals(INHERITED_RESOURCE_LOCATION_WITH_SUFFIX);
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
            if (m.name.equals("m_7912_") && m.desc.equals("()Ljava/lang/String;")) {
                candidates.add(STRING_REPRESENTABLE);
            }
            if (m.name.equals("m_7180_") && m.desc.equals(FLUID_STATE_DEFINITION_DESC)) candidates.add(FLUID);
            if (isBlockFluidStateMethod(m.name, m.desc)) {
                candidates.add("net/minecraft/world/level/block/Block");
            }
            if (m.name.equals("m_7926_") && m.desc.equals(BLOCK_STATE_DEFINITION_DESC)) {
                candidates.add("net/minecraft/world/level/block/Block");
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
        return !bridges.isEmpty();
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
