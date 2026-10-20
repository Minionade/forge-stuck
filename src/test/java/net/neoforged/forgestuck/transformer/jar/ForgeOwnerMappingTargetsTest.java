package net.neoforged.forgestuck.transformer.jar;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;
import net.neoforged.forgestuck.compat.LegacyCriticalHitEvent;
import net.neoforged.forgestuck.compat.LegacyEventResult;
import net.neoforged.forgestuck.compat.LegacyLivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/** Guards that every default owner mapping points at a real class and every whitelisted member really exists. */
class ForgeOwnerMappingTargetsTest {
    private static final List<String> OWNERS = List.of(
            "net/minecraftforge/eventbus/api/IGenericEvent",
            "net/minecraftforge/eventbus/api/GenericEvent",
            "net/minecraftforge/event/entity/living/LivingDamageEvent",
            "net/minecraftforge/common/SoundAction", "net/minecraftforge/common/SoundActions",
            "net/minecraftforge/common/Tags$Items", "net/minecraftforge/common/Tags$Blocks",
            "net/minecraftforge/common/TierSortingRegistry",
            "net/minecraftforge/client/ForgeRenderTypes",
            "net/minecraftforge/common/ForgeI18n",
            "net/minecraft/core/particles/ParticleOptions",
            "net/minecraft/core/particles/ParticleOptions$Deserializer",
            "net/minecraftforge/common/loot/LootModifier",
            "net/minecraft/world/level/storage/loot/entries/LootPoolEntryContainer$Serializer",
            "net/minecraft/world/level/storage/loot/entries/LootPoolSingletonContainer$Serializer",
            "net/minecraft/world/level/storage/loot/functions/LootItemConditionalFunction$Serializer",
            "net/minecraft/commands/CommandRuntimeException",
            "net/minecraft/world/item/ItemStack$TooltipPart",
            "net/minecraftforge/event/AttachCapabilitiesEvent",
            "net/minecraftforge/common/capabilities/ICapabilityProvider",
            "net/minecraftforge/common/capabilities/ICapabilitySerializable",
            "net/minecraftforge/common/capabilities/Capability",
            "net/minecraftforge/common/capabilities/CapabilityManager",
            "net/minecraftforge/common/capabilities/CapabilityToken",
            "net/minecraftforge/common/capabilities/ForgeCapabilities",
            "net/minecraft/world/level/pathfinder/BlockPathTypes",
            "net/minecraftforge/common/extensions/IForgeBlockEntity",
            "net/minecraftforge/common/IPlantable",
            "net/minecraftforge/common/crafting/IShapedRecipe",
            "net/minecraftforge/common/crafting/CraftingHelper",
            "net/minecraftforge/common/crafting/VanillaIngredientSerializer",
            "net/minecraft/network/FriendlyByteBuf$Writer", "net/minecraft/network/FriendlyByteBuf$Reader",
            "net/minecraftforge/registries/RegistryObject", "net/minecraftforge/registries/DeferredRegister",
            "net/minecraftforge/registries/IForgeRegistry", "net/minecraftforge/registries/ForgeRegistries",
            "net/minecraftforge/registries/ForgeRegistries$Keys", "net/minecraftforge/registries/RegisterEvent",
            "net/minecraftforge/registries/RegisterEvent$RegisterHelper", "net/minecraftforge/fml/common/Mod",
            "net/minecraftforge/fml/common/Mod$EventBusSubscriber",
            "net/minecraftforge/fml/common/Mod$EventBusSubscriber$Bus",
            "net/minecraftforge/eventbus/api/IEventBus", "net/minecraftforge/eventbus/api/SubscribeEvent",
            "net/minecraftforge/eventbus/api/EventPriority",
            "net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext",
            "net/minecraftforge/fml/config/IConfigSpec", "net/minecraftforge/api/distmarker/Dist",
            "net/minecraftforge/api/distmarker/OnlyIn",
            "net/minecraftforge/fml/event/lifecycle/FMLCommonSetupEvent",
            "net/minecraftforge/fml/event/lifecycle/FMLClientSetupEvent",
            "net/minecraftforge/fml/event/lifecycle/FMLDedicatedServerSetupEvent",
            "net/minecraftforge/common/util/LazyOptional", "net/minecraftforge/common/util/NonNullConsumer",
            "net/minecraftforge/common/util/NonNullFunction", "net/minecraftforge/common/util/NonNullSupplier",
            "net/minecraftforge/common/util/FakePlayer",
            "net/minecraftforge/common/util/NonNullPredicate", "net/minecraftforge/common/util/TablePrinter",
            "net/minecraftforge/common/extensions/IForgeItem", "net/minecraft/world/item/DyeableLeatherItem",
            "net/minecraft/world/item/ArmorMaterial",
            "net/minecraftforge/client/model/data/ModelData",
            "net/minecraftforge/client/model/data/ModelData$Builder",
            "net/minecraftforge/client/model/data/ModelProperty",
            "net/minecraftforge/common/extensions/IForgeBlock",
            "net/minecraftforge/items/IItemHandler", "net/minecraftforge/items/IItemHandlerModifiable",
            "net/minecraftforge/items/SlotItemHandler",
            "net/minecraftforge/registries/RegistryBuilder", "net/minecraft/world/level/block/StemGrownBlock",
            "net/minecraft/world/level/block/SandBlock", "net/minecraftforge/event/entity/living/LootingLevelEvent",
            "net/minecraftforge/client/event/EntityRenderersEvent$AddLayers",
            "net/minecraftforge/common/util/INBTSerializable",
            "net/minecraftforge/common/ForgeConfigSpec", "net/minecraftforge/common/ForgeConfigSpec$Builder",
            "net/minecraftforge/common/ForgeConfigSpec$ConfigValue", "net/minecraftforge/common/ForgeConfigSpec$BooleanValue",
            "net/minecraftforge/common/ForgeConfigSpec$IntValue", "net/minecraftforge/common/ForgeConfigSpec$LongValue",
            "net/minecraftforge/common/ForgeConfigSpec$DoubleValue", "net/minecraftforge/common/ForgeConfigSpec$EnumValue",
            "net/minecraftforge/fml/config/ModConfig", "net/minecraftforge/fml/config/ModConfig$Type",
            "net/minecraftforge/fml/event/config/ModConfigEvent", "net/minecraftforge/fml/event/config/ModConfigEvent$Loading",
            "net/minecraftforge/fml/event/config/ModConfigEvent$Reloading", "net/minecraftforge/common/MinecraftForge",
            "net/minecraftforge/fml/ModLoadingContext", "net/minecraftforge/fml/ModContainer",
            "net/minecraftforge/fml/ModLoader",
            "net/minecraftforge/forgespi/language/IModInfo",
            "net/minecraftforge/fml/ModList", "net/minecraftforge/fml/loading/FMLEnvironment",
            "net/minecraftforge/fml/loading/FMLPaths", "net/minecraftforge/fml/LogicalSide",
            "net/minecraftforge/fml/DistExecutor", "net/minecraftforge/fml/DistExecutor$SafeReferent",
            "net/minecraftforge/fml/DistExecutor$SafeCallable", "net/minecraftforge/fml/DistExecutor$SafeSupplier",
            "net/minecraftforge/fml/DistExecutor$SafeRunnable");

    private static List<String> owners() {
        List<String> all = new ArrayList<>(OWNERS);
        for (ForgeOwnerMappings.Entry e : GeneratedOwnerTable.entries()) all.add(e.oldOwner());
        for (ForgeOwnerMappings.Entry e : TickMappings.entries()) all.add(e.oldOwner());
        for (ForgeOwnerMappings.Entry e : ConditionMappings.entries()) all.add(e.oldOwner());
        for (ForgeOwnerMappings.Entry e : CommonApiMappings.entries()) all.add(e.oldOwner());
        for (ForgeOwnerMappings.Entry e : DataMappings.entries()) all.add(e.oldOwner());
        for (ForgeOwnerMappings.Entry e : ModelGeneratorMappings.entries()) all.add(e.oldOwner());
        for (ForgeOwnerMappings.Entry e : ClientModelMappings.entries()) all.add(e.oldOwner());
        for (ForgeOwnerMappings.Entry e : CommonHookMappings.entries()) all.add(e.oldOwner());
        for (ForgeOwnerMappings.Entry e : FluidUtilityMappings.entries()) all.add(e.oldOwner());
        for (ForgeOwnerMappings.Entry e : WorldGenerationMappings.entries()) all.add(e.oldOwner());
        for (ForgeOwnerMappings.Entry e : ItemHandlerMappings.entries()) all.add(e.oldOwner());
        for (ForgeOwnerMappings.Entry e : NetworkMappings.entries()) all.add(e.oldOwner());
        for (ForgeOwnerMappings.Entry e : ItemAbilityAndFluidMappings.entries()) all.add(e.oldOwner());
        for (ForgeOwnerMappings.Entry e : KeyConflictMappings.entries()) all.add(e.oldOwner());
        return all;
    }

    private static Class<?> load(String internal) throws ClassNotFoundException {
        String name = internal.replace('/', '.');
        try {
            return Class.forName(name, false, ForgeOwnerMappingTargetsTest.class.getClassLoader());
        } catch (LinkageError e) {
            // Other tests' app-parented loaders may already have defined the same NeoForge classes.
            return Class.forName(name, false, ClassLoader.getSystemClassLoader());
        }
    }

    @Test
    void everyMappedOwnerIsPresentAndTargetsExist() throws Exception {
        ForgeOwnerMappings mappings = ForgeOwnerMappings.defaults();
        for (String owner : owners()) {
            assertNotNull(mappings.get(owner), "no mapping for " + owner);
            assertDoesNotThrow(() -> load(mappings.get(owner).newOwner()), "missing target for " + owner);
        }
    }

    @Test
    void criticalHitResultPreservesExplicitAllowAndDeny() {
        CriticalHitEvent vanillaCritical = new CriticalHitEvent(null, null, 1.5F, true);
        LegacyCriticalHitEvent.setResult(vanillaCritical, LegacyEventResult.ALLOW);
        assertEquals(LegacyEventResult.ALLOW, LegacyCriticalHitEvent.getResult(vanillaCritical));

        CriticalHitEvent nonCritical = new CriticalHitEvent(null, null, 1.0F, false);
        LegacyCriticalHitEvent.setResult(nonCritical, LegacyEventResult.DENY);
        assertEquals(LegacyEventResult.DENY, LegacyCriticalHitEvent.getResult(nonCritical));
    }

    @Test
    void legacyLivingDamageEventPreservesCancellation() {
        LegacyLivingDamageEvent event = new LegacyLivingDamageEvent(null, null, 4.0F);
        assertInstanceOf(net.neoforged.bus.api.ICancellableEvent.class, event);
        assertTrue(event.isCancelable());
    }

    @Test
    void optionalDeferredRegisterRetainsUnavailableRegistryKey() {
        net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<String>> key =
                net.minecraft.resources.ResourceKey.createRegistryKey(
                        net.minecraft.resources.ResourceLocation.parse("forgestuck_test:optional_registry"));
        var register = net.neoforged.forgestuck.compat.LegacyDeferredRegister.createOptional(key, "forgestuck_test");
        assertEquals(key, register.getRegistryKey());
        assertFalse(register.register("entry", () -> "value").isPresent());
    }

    @Test
    void mobEffectApplicablePreservesForgeEventResults() {
        assertEquals(List.of("APPLY", "DEFAULT", "DO_NOT_APPLY"),
                java.util.Arrays.stream(MobEffectEvent.Applicable.Result.values())
                .map(Enum::name).toList());
        MobEffectEvent.Applicable event = new MobEffectEvent.Applicable(null, null, null);
        for (LegacyEventResult result : LegacyEventResult.values()) {
            net.neoforged.forgestuck.compat.LegacyMobEffectApplicable.setResult(event, result);
            assertEquals(result, net.neoforged.forgestuck.compat.LegacyMobEffectApplicable.getResult(event));
        }
        net.neoforged.forgestuck.compat.LegacyMobEffectApplicable.setResult(event, LegacyEventResult.ALLOW);
        net.neoforged.forgestuck.compat.LegacyMobEffectApplicable.setCanceled(event, true);
        assertTrue(net.neoforged.forgestuck.compat.LegacyMobEffectApplicable.isCanceled(event));
        assertEquals(LegacyEventResult.DENY,
                net.neoforged.forgestuck.compat.LegacyMobEffectApplicable.getResult(event));
        net.neoforged.forgestuck.compat.LegacyMobEffectApplicable.setCanceled(event, false);
        assertFalse(net.neoforged.forgestuck.compat.LegacyMobEffectApplicable.isCanceled(event));
        assertEquals(LegacyEventResult.ALLOW,
                net.neoforged.forgestuck.compat.LegacyMobEffectApplicable.getResult(event));
    }

    @Test
    void legacyLootTableConditionBuilderBuildsNeoForgeCondition() {
        var builder = new net.neoforged.neoforge.common.loot.LootTableIdCondition.Builder(
                net.minecraft.resources.ResourceLocation.parse("test:loot"));
        assertInstanceOf(net.neoforged.neoforge.common.loot.LootTableIdCondition.class,
                net.neoforged.forgestuck.compat.LegacyLootTableIdCondition.m_6409_(builder));
    }

    @Test
    void legacyCanToolPerformActionBuildsNeoForgeAbilityCondition() {
        var builder = net.neoforged.forgestuck.compat.LegacyCanToolPerformAction.canToolPerformAction(
                net.neoforged.neoforge.common.ItemAbility.get("forgestuck_test"));
        assertInstanceOf(net.neoforged.neoforge.common.loot.CanItemPerformAbility.class, builder.build());
    }

    @Test
    void legacyTooltipPartsPreserveHideFlagMasks() {
        assertEquals(1, net.neoforged.forgestuck.compat.LegacyItemStackTooltipPart.ENCHANTMENTS.getMask());
        assertEquals(2, net.neoforged.forgestuck.compat.LegacyItemStackTooltipPart.MODIFIERS.getMask());
        assertEquals(3, net.neoforged.forgestuck.compat.LegacyItemStackTooltipPart.ENCHANTMENTS.getMask()
                | net.neoforged.forgestuck.compat.LegacyItemStackTooltipPart.MODIFIERS.getMask());
    }

    @Test
    void legacyTierDropCheckUsesTheTierIncorrectBlocksTag() {
        var incorrect = net.minecraft.world.item.Tiers.WOOD.getIncorrectBlocksForDrops();
        var stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        assertEquals(!stone.is(incorrect),
                net.neoforged.forgestuck.compat.LegacyTierSortingRegistry.isCorrectTierForDrops(
                        net.minecraft.world.item.Tiers.WOOD, stone));
    }

    @Test
    void legacyTierSortingResolvesVanillaTierNamesAndOrder() {
        var wood = net.minecraft.world.item.Tiers.WOOD;
        var woodId = net.minecraft.resources.ResourceLocation.withDefaultNamespace("wood");
        assertSame(wood, net.neoforged.forgestuck.compat.LegacyTierSortingRegistry.byName(woodId));
        assertEquals(woodId, net.neoforged.forgestuck.compat.LegacyTierSortingRegistry.getName(wood));
        assertNull(net.neoforged.forgestuck.compat.LegacyTierSortingRegistry.byName(
                net.minecraft.resources.ResourceLocation.parse("example:unknown")));
        assertEquals(List.of(
                net.minecraft.world.item.Tiers.WOOD,
                net.minecraft.world.item.Tiers.GOLD,
                net.minecraft.world.item.Tiers.STONE,
                net.minecraft.world.item.Tiers.IRON,
                net.minecraft.world.item.Tiers.DIAMOND,
                net.minecraft.world.item.Tiers.NETHERITE),
                net.neoforged.forgestuck.compat.LegacyTierSortingRegistry.getSortedTiers());
    }

    @Test
    void legacyCraftingHelperIngredientHonorsAllowEmpty() {
        var empty = new com.google.gson.JsonArray();
        assertSame(net.minecraft.world.item.crafting.Ingredient.EMPTY,
                net.neoforged.forgestuck.compat.LegacyConditions.getIngredient(empty, true));
        assertThrows(com.google.gson.JsonSyntaxException.class,
                () -> net.neoforged.forgestuck.compat.LegacyConditions.getIngredient(empty, false));
    }

    @Test
    void legacyCraftingHelperItemStackParsesItemCountAndNbt() {
        net.neoforged.forgestuck.testsupport.TestBootstrap.init();
        var json = new com.google.gson.JsonObject();
        json.addProperty("item", "minecraft:diamond");
        json.addProperty("count", 3);
        var nbt = new com.google.gson.JsonObject();
        nbt.addProperty("marker", 7);
        json.add("nbt", nbt);

        var stack = net.neoforged.forgestuck.compat.LegacyConditions.getItemStack(json, true);

        assertEquals(net.minecraft.world.item.Items.DIAMOND, stack.getItem());
        assertEquals(3, stack.getCount());
        assertEquals(7, stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA)
                .copyTag().getInt("marker"));
    }

    @Test
    void legacyCraftingHelperItemStackParsesForgeSnbtString() {
        net.neoforged.forgestuck.testsupport.TestBootstrap.init();
        var json = new com.google.gson.JsonObject();
        json.addProperty("item", "minecraft:diamond");
        json.addProperty("nbt", "{marker:7}");

        var stack = net.neoforged.forgestuck.compat.LegacyConditions.getItemStack(json, true);

        assertEquals(7, stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA)
                .copyTag().getInt("marker"));
    }

    @Test
    void legacyCraftingHelperItemStackHonorsReadNbtAndDisallowsAirFlags() {
        net.neoforged.forgestuck.testsupport.TestBootstrap.init();
        var json = new com.google.gson.JsonObject();
        json.addProperty("item", "minecraft:diamond");
        json.addProperty("count", 2);
        var nbt = new com.google.gson.JsonObject();
        nbt.addProperty("marker", 5);
        json.add("nbt", nbt);

        var withoutNbt = net.neoforged.forgestuck.compat.LegacyConditions.getItemStack(json, false, false);
        assertEquals(2, withoutNbt.getCount());
        assertNull(withoutNbt.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA));

        var withNbt = net.neoforged.forgestuck.compat.LegacyConditions.getItemStack(json, true, false);
        assertEquals(5, withNbt.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA)
                .copyTag().getInt("marker"));

        var air = new com.google.gson.JsonObject();
        air.addProperty("item", "minecraft:air");
        assertTrue(net.neoforged.forgestuck.compat.LegacyConditions.getItemStack(air, false, false).isEmpty());
        assertThrows(com.google.gson.JsonSyntaxException.class,
                () -> net.neoforged.forgestuck.compat.LegacyConditions.getItemStack(air, false, true));
    }

    @Test
    void legacyCraftingHelperGetNbtParsesObjectAndSnbt() {
        var object = new com.google.gson.JsonObject();
        object.addProperty("marker", 9);
        assertEquals(9, net.neoforged.forgestuck.compat.LegacyConditions.getNBT(object).getInt("marker"));
        assertEquals(11, net.neoforged.forgestuck.compat.LegacyConditions.getNBT(
                new com.google.gson.JsonPrimitive("{marker:11}")).getInt("marker"));
        assertThrows(com.google.gson.JsonSyntaxException.class,
                () -> net.neoforged.forgestuck.compat.LegacyConditions.getNBT(
                        new com.google.gson.JsonPrimitive(3)));
    }

    @Test
    void legacyGuiOverlayTokensPreserveIdentityForVanillaLayers() {
        assertSame(net.neoforged.forgestuck.compat.LegacyVanillaGuiOverlay.CROSSHAIR.type(),
                net.neoforged.forgestuck.compat.LegacyNamedGuiOverlay.of(
                        net.neoforged.neoforge.client.gui.VanillaGuiLayers.CROSSHAIR));
        assertSame(net.neoforged.forgestuck.compat.LegacyVanillaGuiOverlay.HOTBAR.type(),
                net.neoforged.forgestuck.compat.LegacyNamedGuiOverlay.of(
                        net.neoforged.neoforge.client.gui.VanillaGuiLayers.HOTBAR));
        assertSame(net.neoforged.forgestuck.compat.LegacyVanillaGuiOverlay.PLAYER_HEALTH.type(),
                net.neoforged.forgestuck.compat.LegacyNamedGuiOverlay.of(
                        net.neoforged.neoforge.client.gui.VanillaGuiLayers.PLAYER_HEALTH));
    }

    @Test
    void legacyItemTagAliasesUseTheExpectedTargetKeys() {
        assertEquals(net.minecraft.tags.ItemTags.FOOT_ARMOR,
                net.neoforged.forgestuck.compat.LegacyItemTags.ARMORS_BOOTS);
        assertEquals(net.minecraft.tags.ItemTags.CHEST_ARMOR,
                net.neoforged.forgestuck.compat.LegacyItemTags.ARMORS_CHESTPLATES);
        assertEquals(net.minecraft.tags.ItemTags.HEAD_ARMOR,
                net.neoforged.forgestuck.compat.LegacyItemTags.ARMORS_HELMETS);
        assertEquals(net.minecraft.tags.ItemTags.LEG_ARMOR,
                net.neoforged.forgestuck.compat.LegacyItemTags.ARMORS_LEGGINGS);
        assertEquals(net.minecraft.tags.ItemTags.SKULLS,
                net.neoforged.forgestuck.compat.LegacyItemTags.HEADS);
        assertEquals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "storage_blocks/quartz"),
                net.neoforged.forgestuck.compat.LegacyItemTags.STORAGE_BLOCKS_QUARTZ.location());
        assertEquals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "dusts/prismarine"),
                net.neoforged.forgestuck.compat.LegacyItemTags.DUSTS_PRISMARINE.location());
        assertEquals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("forge", "stained_glass"),
                net.neoforged.forgestuck.compat.LegacyItemTags.STAINED_GLASS.location());
        assertEquals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("forge", "stained_glass_panes"),
                net.neoforged.forgestuck.compat.LegacyItemTags.STAINED_GLASS_PANES.location());
        assertEquals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("forge", "stained_glass"),
                net.neoforged.forgestuck.compat.LegacyBlockTags.STAINED_GLASS.location());
        assertEquals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("forge", "stained_glass_panes"),
                net.neoforged.forgestuck.compat.LegacyBlockTags.STAINED_GLASS_PANES.location());
    }

    @Test
    void legacyNoneBiomeModifierHolderKeepsTheTargetRegistryKey() {
        net.neoforged.forgestuck.testsupport.TestBootstrap.init();
        assertEquals(net.neoforged.neoforge.common.NeoForgeMod.NONE_BIOME_MODIFIER_TYPE.getKey(),
                net.neoforged.forgestuck.compat.LegacyForgeMod.NONE_BIOME_MODIFIER_TYPE.getKey());
    }

    @Test
    void legacyPlantableContractPreservesItsMethods() throws Exception {
        Class<?> target = net.neoforged.forgestuck.compat.LegacyIPlantable.class;
        assertTrue(target.isInterface());
        assertTrue(hasMethod(target, "getPlant",
                "(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)"
                        + "Lnet/minecraft/world/level/block/state/BlockState;"));
        assertTrue(hasMethod(target, "getPlantType",
                "(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)"
                        + "Lnet/neoforged/forgestuck/compat/PlantType;"));
    }

    @Test
    void legacyPlantableAdapterPreservesVanillaBlockDefaults() {
        net.neoforged.forgestuck.testsupport.TestBootstrap.init();
        var block = net.minecraft.world.level.block.Blocks.DEAD_BUSH;
        assertEquals(block.defaultBlockState(), net.neoforged.forgestuck.compat.LegacyPlantable.getPlant(
                (Object) block, null, null));
        assertEquals(net.neoforged.forgestuck.compat.PlantType.DESERT,
                net.neoforged.forgestuck.compat.LegacyPlantable.getPlantType((Object) block, null, null));
    }

    @Test
    void legacyShapedRecipeAdapterReadsVanillaDimensions() {
        net.neoforged.forgestuck.testsupport.TestBootstrap.init();
        var recipe = new net.minecraft.world.item.crafting.ShapedRecipe(
                "", net.minecraft.world.item.crafting.CraftingBookCategory.MISC,
                net.minecraft.world.item.crafting.ShapedRecipePattern.of(
                        java.util.Map.of('X',
                                net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.STONE)),
                        "XX", " X"),
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE));
        assertEquals(2, net.neoforged.forgestuck.compat.LegacyShapedRecipe.getRecipeWidth(recipe));
        assertEquals(2, net.neoforged.forgestuck.compat.LegacyShapedRecipe.getRecipeHeight(recipe));
        assertEquals("net/neoforged/forgestuck/compat/LegacyIShapedRecipe",
                ForgeOwnerMappings.defaults().get("net/minecraftforge/common/crafting/IShapedRecipe").newOwner());
    }

    @Test
    void legacyVanillaIngredientSerializerParsesVanillaJson() throws Exception {
        net.neoforged.forgestuck.testsupport.TestBootstrap.init();
        var serializer = net.neoforged.forgestuck.compat.LegacyVanillaIngredientSerializer.INSTANCE;
        var ingredient = serializer.parse(com.google.gson.JsonParser.parseString(
                "{\"item\":\"minecraft:stone\"}").getAsJsonObject());
        assertTrue(ingredient.test(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE)));

        var entry = ForgeOwnerMappings.defaults().get(
                "net/minecraftforge/common/crafting/VanillaIngredientSerializer");
        assertTrue(hasField(load(entry.newOwner()), "INSTANCE",
                "Lnet/neoforged/forgestuck/compat/LegacyVanillaIngredientSerializer;"));
    }

    @Test
    void conditionalAdvancementBuilderMapsOnlyItsSupportedSingleVariantCalls() throws Exception {
        ForgeOwnerMappings mappings = ForgeOwnerMappings.defaults();
        String oldOwner = "net/minecraftforge/common/crafting/ConditionalAdvancement$Builder";
        String newOwner = "net/neoforged/forgestuck/compat/LegacyConditionalAdvancement$Builder";
        var entry = mappings.get(oldOwner);
        assertNotNull(entry);
        assertEquals(newOwner, entry.newOwner());

        Class<?> target = load(newOwner);
        assertTrue(hasConstructor(target, "()V"));
        assertTrue(hasMethod(target, "addAdvancement",
                "(Lnet/minecraft/advancements/Advancement$Builder;)L" + newOwner + ";"));
        assertTrue(hasMethod(target, "addCondition",
                "(Lnet/neoforged/neoforge/common/conditions/ICondition;)L" + newOwner + ";"));
        assertTrue(hasMethod(target, "write", "()Lcom/google/gson/JsonObject;"));
        assertNull(mappings.get("net/minecraftforge/common/crafting/ConditionalRecipe$Builder"));
    }

    @Test
    void legacyArmorTextureHookPreservesFallback() {
        net.neoforged.forgestuck.testsupport.TestBootstrap.init();
        assertEquals("default:texture",
                net.neoforged.forgestuck.compat.LegacyArmorTextureHooks.getArmorTexture(null,
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE), "default:texture",
                        net.minecraft.world.entity.EquipmentSlot.CHEST, "overlay"));
    }

    @Test
    void legacyBonemealHookPreservesNullPlayerNoOp() {
        assertEquals(0, net.neoforged.forgestuck.compat.LegacyBonemealHook.onApplyBonemeal(
                null, null, null, null, null));
    }

    @Test
    void legacyGeometryHelperPreservesEmptyBakeResult() {
        assertTrue(net.neoforged.forgestuck.compat.LegacyUnbakedGeometryHelper.bakeElements(
                List.of(), null, null, null).isEmpty());
    }

    @Test
    void mappedForgeTagFieldsExistOnNeoForge() throws Exception {
        ForgeOwnerMappings mappings = ForgeOwnerMappings.defaults();
        List<String> missing = new ArrayList<>();
        for (String owner : List.of("net/minecraftforge/common/Tags$Items",
                "net/minecraftforge/common/Tags$Blocks", "net/minecraftforge/common/Tags$Fluids",
                "net/minecraftforge/common/Tags$Biomes")) {
            ForgeOwnerMappings.Entry entry = mappings.get(owner);
            Class<?> target = load(entry.newOwner());
            for (String member : entry.supportedMembers()) {
                var matcher = FIELD.matcher(member);
                assertTrue(matcher.matches(), "invalid field mapping " + member);
                String targetName = mappings.mappedFieldName(owner, matcher.group(1), matcher.group(2));
                if (!hasField(target, targetName, matcher.group(2))) {
                    missing.add(entry.newOwner() + "." + member);
                }
            }
        }
        assertEquals(List.of(), missing);
    }

    @Test
    void legacyCommonTagFieldAliasesPointAtExistingNeoForgeFields() throws Exception {
        ForgeOwnerMappings mappings = ForgeOwnerMappings.defaults();
        for (String[] alias : List.of(
                new String[] {"net/minecraftforge/common/Tags$Items", "GLASS", "GLASS_BLOCKS"},
                new String[] {"net/minecraftforge/common/Tags$Items", "GUNPOWDER", "GUNPOWDERS"},
                new String[] {"net/minecraftforge/common/Tags$Items", "STRING", "STRINGS"},
                new String[] {"net/minecraftforge/common/Tags$Items", "TOOLS_BOWS", "TOOLS_BOW"},
                new String[] {"net/minecraftforge/common/Tags$Items", "GLASS_SILICA", "GLASS_BLOCKS_CHEAP"},
                new String[] {"net/minecraftforge/common/Tags$Items", "TOOLS_TRIDENTS", "TOOLS_SPEAR"},
                new String[] {"net/minecraftforge/common/Tags$Blocks", "GLASS", "GLASS_BLOCKS"},
                new String[] {"net/minecraftforge/common/Tags$Blocks", "GLASS_SILICA", "GLASS_BLOCKS_CHEAP"})) {
            ForgeOwnerMappings.Entry entry = mappings.get(alias[0]);
            Class<?> target = load(entry.newOwner());
            String descriptor = "Lnet/minecraft/tags/TagKey;";
            assertEquals(alias[2], mappings.mappedFieldName(alias[0], alias[1], descriptor));
            assertTrue(hasField(target, alias[2], descriptor));
            assertTrue(mappings.supportsMember(alias[0], alias[1], descriptor));
        }
    }

    @Test
    void mappedForgeRenderTypeMembersExistOnNeoForge() throws Exception {
        ForgeOwnerMappings mappings = ForgeOwnerMappings.defaults();
        ForgeOwnerMappings.Entry entry = mappings.get("net/minecraftforge/client/ForgeRenderTypes");
        Class<?> target = load(entry.newOwner());
        for (String member : entry.supportedMembers()) {
            if (member.contains("(")) {
                int paren = member.indexOf('(');
                String name = member.substring(0, paren);
                String descriptor = mappings.remapDescriptorTokens(member.substring(paren), new HashSet<>(),
                        new HashMap<>());
                assertTrue(hasMethod(target, name, descriptor), "missing method " + member);
            } else {
                var matcher = FIELD.matcher(member);
                assertTrue(matcher.matches(), "invalid field mapping " + member);
                String descriptor = mappings.remapDescriptorTokens(matcher.group(2), new HashSet<>(),
                        new HashMap<>());
                assertTrue(hasField(target, matcher.group(1), descriptor), "missing field " + member);
            }
        }
    }

    @Test
    void mappedItemFluidHandlerMethodsExistOnNeoForge() throws Exception {
        ForgeOwnerMappings mappings = ForgeOwnerMappings.defaults();
        ForgeOwnerMappings.Entry entry = mappings.get("net/minecraftforge/fluids/capability/IFluidHandlerItem");
        Class<?> target = load(entry.newOwner());
        for (String member : entry.supportedMembers()) {
            int paren = member.indexOf('(');
            String name = member.substring(0, paren);
            String descriptor = mappings.remapDescriptorTokens(member.substring(paren), new HashSet<>(),
                    new HashMap<>());
            boolean exists = name.equals("<init>") ? hasConstructor(target, descriptor)
                    : hasMethod(target, name, descriptor);
            assertTrue(exists, "missing target method " + name + descriptor);
        }
    }

    @Test
    void mappedClientFluidExtensionMethodsExistOnNeoForge() throws Exception {
        ForgeOwnerMappings mappings = ForgeOwnerMappings.defaults();
        ForgeOwnerMappings.Entry entry = GeneratedOwnerTable.entries().stream()
                .filter(candidate -> candidate.oldOwner().equals(
                        "net/minecraftforge/client/extensions/common/IClientFluidTypeExtensions"))
                .findFirst().orElseThrow();
        Class<?> target = load(entry.newOwner());
        for (String member : entry.supportedMembers()) {
            int paren = member.indexOf('(');
            String name = member.substring(0, paren);
            String descriptor = mappings.remapDescriptorTokens(member.substring(paren), new HashSet<>(),
                    new HashMap<>());
            boolean exists = name.equals("<init>") ? hasConstructor(target, descriptor)
                    : hasMethod(target, name, descriptor);
            assertTrue(exists, "missing target method " + name + descriptor);
        }
    }

    @Test
    void mappedClientExtensionsAndRenderEventsExistOnNeoForge() throws Exception {
        ForgeOwnerMappings mappings = ForgeOwnerMappings.defaults();
        List<String> owners = List.of(
                "net/minecraftforge/client/extensions/common/IClientItemExtensions",
                "net/minecraftforge/client/extensions/common/IClientItemExtensions$FontContext",
                "net/minecraftforge/client/extensions/common/IClientMobEffectExtensions",
                "net/minecraftforge/client/event/RenderItemInFrameEvent",
                "net/minecraftforge/client/event/RenderNameTagEvent",
                "net/minecraftforge/client/gui/overlay/ForgeGui");
        for (String owner : owners) {
            ForgeOwnerMappings.Entry entry = mappings.get(owner);
            Class<?> target = load(entry.newOwner());
            for (String member : entry.supportedMembers()) {
                boolean method = member.contains("(");
                String name;
                String descriptor;
                if (method) {
                    int paren = member.indexOf('(');
                    name = member.substring(0, paren);
                    descriptor = member.substring(paren);
                } else {
                    var matcher = FIELD.matcher(member);
                    assertTrue(matcher.matches(), "invalid field mapping " + member);
                    name = matcher.group(1);
                    descriptor = matcher.group(2);
                }
                descriptor = mappings.remapDescriptorTokens(descriptor, new HashSet<>(), new HashMap<>());
                boolean exists = method
                        ? name.equals("<init>") ? hasConstructor(target, descriptor) : hasMethod(target, name, descriptor)
                        : hasField(target, name, descriptor);
                assertTrue(exists, "missing target member " + entry.newOwner() + "." + name + descriptor);
            }
        }
    }

    @Test
    void mappedFluidStackMembersExistOnNeoForge() throws Exception {
        ForgeOwnerMappings mappings = ForgeOwnerMappings.defaults();
        ForgeOwnerMappings.Entry entry = mappings.get("net/minecraftforge/fluids/FluidStack");
        Class<?> target = load(entry.newOwner());
        for (String member : entry.supportedMembers()) {
            int paren = member.indexOf('(');
            String name;
            String descriptor;
            boolean method = paren >= 0;
            if (method) {
                name = member.substring(0, paren);
                descriptor = member.substring(paren);
            } else {
                var matcher = FIELD.matcher(member);
                assertTrue(matcher.matches(), "invalid field mapping " + member);
                name = matcher.group(1);
                descriptor = matcher.group(2);
            }
            descriptor = mappings.remapDescriptorTokens(descriptor, new HashSet<>(), new HashMap<>());
            boolean exists = method
                    ? name.equals("<init>") ? hasConstructor(target, descriptor) : hasMethod(target, name, descriptor)
                    : hasField(target, name, descriptor);
            assertTrue(exists, "missing target member " + entry.newOwner() + "." + name + descriptor);
        }
    }

    @Test
    void existingFileHelperResourceTypeTrackGeneratedOverloadExistsOnNeoForge() throws Exception {
        ForgeOwnerMappings.Entry entry = DataMappings.entries().stream()
                .filter(candidate -> candidate.oldOwner().equals(
                        "net/minecraftforge/common/data/ExistingFileHelper"))
                .findFirst().orElseThrow();
        ForgeOwnerMappings mappings = ForgeOwnerMappings.defaults();
        Class<?> target = load(entry.newOwner());
        String oldDescriptor = "(Lnet/minecraft/resources/ResourceLocation;"
                + "Lnet/minecraftforge/common/data/ExistingFileHelper$IResourceType;)V";
        String descriptor = mappings.remapDescriptorTokens(oldDescriptor, new HashSet<>(), new HashMap<>());
        assertTrue(hasMethod(target, "trackGenerated", descriptor));
    }

    @Test
    void existingFileHelperResourceTypeExistsOverloadExistsOnNeoForge() throws Exception {
        ForgeOwnerMappings.Entry entry = DataMappings.entries().stream()
                .filter(candidate -> candidate.oldOwner().equals(
                        "net/minecraftforge/common/data/ExistingFileHelper"))
                .findFirst().orElseThrow();
        ForgeOwnerMappings mappings = ForgeOwnerMappings.defaults();
        Class<?> target = load(entry.newOwner());
        String oldDescriptor = "(Lnet/minecraft/resources/ResourceLocation;"
                + "Lnet/minecraftforge/common/data/ExistingFileHelper$IResourceType;)Z";
        String descriptor = mappings.remapDescriptorTokens(oldDescriptor, new HashSet<>(), new HashMap<>());
        assertTrue(hasMethod(target, "exists", descriptor));
    }

    @Test
    void reloadContextAndModInfoMembersExistOnNeoForge() throws Exception {
        ForgeOwnerMappings mappings = ForgeOwnerMappings.defaults();
        for (String owner : List.of("net/minecraftforge/event/AddReloadListenerEvent",
                "net/minecraftforge/fml/ModContainer", "net/minecraftforge/forgespi/language/IModInfo")) {
            ForgeOwnerMappings.Entry entry = mappings.get(owner);
            Class<?> target = load(entry.newOwner());
            for (String member : entry.supportedMembers()) {
                if (member.indexOf('(') < 0) continue;
                int paren = member.indexOf('(');
                String name = member.substring(0, paren);
                String descriptor = mappings.remapDescriptorTokens(member.substring(paren), new HashSet<>(),
                        new HashMap<>());
                assertTrue(hasMethod(target, name, descriptor), "missing target method " + owner + "." + member);
            }
        }
    }

    @Test
    void mappedFluidTypeStackOverloadsExistOnNeoForge() throws Exception {
        ForgeOwnerMappings.Entry entry = ConditionMappings.entries().stream()
                .filter(candidate -> candidate.oldOwner().equals("net/minecraftforge/fluids/FluidType"))
                .findFirst().orElseThrow();
        ForgeOwnerMappings mappings = ForgeOwnerMappings.defaults();
        Class<?> target = load(entry.newOwner());
        for (String member : entry.supportedMembers()) {
            int paren = member.indexOf('(');
            if (paren < 0 || member.startsWith("<init>")) continue;
            String name = member.substring(0, paren);
            String descriptor = mappings.remapDescriptorTokens(member.substring(paren), new HashSet<>(),
                    new HashMap<>());
            assertTrue(hasMethod(target, name, descriptor), "missing target method " + name + descriptor);
        }
    }

    @Test
    void mappedModelGeneratorMembersExistOnNeoForge() throws Exception {
        ForgeOwnerMappings mappings = ForgeOwnerMappings.defaults();
        List<String> missing = new ArrayList<>();
        for (ForgeOwnerMappings.Entry entry : ModelGeneratorMappings.entries()) {
            Class<?> target = load(entry.newOwner());
            for (String member : entry.supportedMembers()) {
                int paren = member.indexOf('(');
                String name;
                String descriptor;
                boolean method = paren >= 0;
                if (method) {
                    name = member.substring(0, paren);
                    descriptor = member.substring(paren);
                } else {
                    var matcher = FIELD.matcher(member);
                    assertTrue(matcher.matches(), "invalid field mapping " + member);
                    name = matcher.group(1);
                    descriptor = matcher.group(2);
                }
                descriptor = mappings.remapDescriptorTokens(descriptor, new HashSet<>(), new HashMap<>());
                boolean exists = method
                        ? name.equals("<init>") ? hasConstructor(target, descriptor) : hasMethod(target, name, descriptor)
                        : hasField(target, name, descriptor);
                if (!exists) missing.add(entry.newOwner() + "." + name + descriptor);
            }
        }
        assertEquals(List.of(), missing);
    }

    @Test
    void mappedItemHandlerHelperMembersExistOnNeoForge() throws Exception {
        ForgeOwnerMappings mappings = ForgeOwnerMappings.defaults();
        List<String> missing = new ArrayList<>();
        for (ForgeOwnerMappings.Entry entry : ItemHandlerMappings.entries()) {
            Class<?> target = load(entry.newOwner());
            for (String member : entry.supportedMembers()) {
                int paren = member.indexOf('(');
                if (paren < 0) {
                    var matcher = FIELD.matcher(member);
                    assertTrue(matcher.matches(), "invalid field mapping " + member);
                    String descriptor = mappings.remapDescriptorTokens(matcher.group(2), new HashSet<>(),
                            new HashMap<>());
                    if (!hasField(target, matcher.group(1), descriptor)) {
                        missing.add(entry.newOwner() + "." + member);
                    }
                    continue;
                }
                String name = member.substring(0, paren);
                String descriptor = mappings.remapDescriptorTokens(member.substring(paren), new HashSet<>(),
                        new HashMap<>());
                boolean exists = name.equals("<init>") ? hasConstructor(target, descriptor)
                        : hasMethod(target, name, descriptor);
                if (!exists) {
                    missing.add(entry.newOwner() + "." + name + descriptor);
                }
            }
        }
        assertEquals(List.of(), missing);
    }

    @Test
    void mappedClientModelMembersExistOnNeoForge() throws Exception {
        ForgeOwnerMappings mappings = ForgeOwnerMappings.defaults();
        List<String> missing = new ArrayList<>();
        for (ForgeOwnerMappings.Entry entry : ClientModelMappings.entries()) {
            Class<?> target = load(entry.newOwner());
            for (String member : entry.supportedMembers()) {
                int paren = member.indexOf('(');
                String name;
                String descriptor;
                boolean method = paren >= 0;
                if (method) {
                    name = member.substring(0, paren);
                    descriptor = member.substring(paren);
                } else {
                    var matcher = FIELD.matcher(member);
                    assertTrue(matcher.matches(), "invalid field mapping " + member);
                    name = matcher.group(1);
                    descriptor = matcher.group(2);
                }
                descriptor = mappings.remapDescriptorTokens(descriptor, new HashSet<>(), new HashMap<>());
                boolean exists = method
                        ? name.equals("<init>") ? hasConstructor(target, descriptor) : hasMethod(target, name, descriptor)
                        : hasField(target, name, descriptor);
                if (!exists) missing.add(entry.newOwner() + "." + name + descriptor + " available="
                        + java.util.Arrays.toString(target.getDeclaredMethods()));
            }
        }
        assertEquals(List.of(), missing);
    }

    @Test
    void mappedFluidUtilityMembersExistOnNeoForge() throws Exception {
        ForgeOwnerMappings mappings = ForgeOwnerMappings.defaults();
        List<String> missing = new ArrayList<>();
        for (ForgeOwnerMappings.Entry entry : FluidUtilityMappings.entries()) {
            Class<?> target = load(entry.newOwner());
            for (String member : entry.supportedMembers()) {
                if (isCallSiteAdapted(entry.oldOwner(), member)) continue;
                int paren = member.indexOf('(');
                String name;
                String descriptor;
                boolean method = paren >= 0;
                if (method) {
                    name = member.substring(0, paren);
                    descriptor = member.substring(paren);
                } else {
                    var matcher = FIELD.matcher(member);
                    assertTrue(matcher.matches(), "invalid field mapping " + member);
                    name = matcher.group(1);
                    descriptor = matcher.group(2);
                }
                descriptor = mappings.remapDescriptorTokens(descriptor, new HashSet<>(), new HashMap<>());
                boolean exists = method
                        ? name.equals("<init>") ? hasConstructor(target, descriptor) : hasMethod(target, name, descriptor)
                        : hasField(target, name, descriptor);
                if (!exists) missing.add(entry.newOwner() + "." + name + descriptor);
            }
        }
        assertEquals(List.of(), missing);
    }

    @Test
    void legacyCapabilityTokenAndBuiltInConstantsHaveCompatibilityTypes() {
        ForgeOwnerMappings mappings = ForgeOwnerMappings.defaults();
        var provider = mappings.get("net/minecraftforge/common/capabilities/ICapabilityProvider");
        assertEquals("net/neoforged/forgestuck/compat/LegacyICapabilityProvider", provider.newOwner());
        assertTrue(mappings.supportsMember("net/minecraftforge/common/capabilities/ICapabilityProvider",
                "getCapability", "(Lnet/minecraftforge/common/capabilities/Capability;)"
                        + "Lnet/minecraftforge/common/util/LazyOptional;"));
        assertTrue(mappings.supportsMember("net/minecraftforge/common/capabilities/ICapabilityProvider",
                "getCapability", "(Lnet/minecraftforge/common/capabilities/Capability;"
                        + "Lnet/minecraft/core/Direction;)Lnet/minecraftforge/common/util/LazyOptional;"));
        for (String n : List.of("Capability", "CapabilityManager", "CapabilityToken", "ForgeCapabilities")) {
            assertNotNull(mappings.get("net/minecraftforge/common/capabilities/" + n), n);
        }
        assertEquals("net/neoforged/forgestuck/compat/LegacyICapabilitySerializable",
                mappings.get("net/minecraftforge/common/capabilities/ICapabilitySerializable").newOwner());
    }

    @Test
    void legacyCapabilityTokensResolveCanonicalCapabilities() {
        var itemHandler = net.neoforged.forgestuck.compat.LegacyCapabilityManager.get(
                new net.neoforged.forgestuck.compat.LegacyCapabilityToken<
                        net.neoforged.neoforge.items.IItemHandler>() {});
        assertSame(net.neoforged.forgestuck.compat.LegacyForgeCapabilities.ITEM_HANDLER, itemHandler);

        var present = net.neoforged.forgestuck.compat.LazyOptional.of(() -> "present");
        var stringCapability = net.neoforged.forgestuck.compat.LegacyCapabilityManager.get(
                new net.neoforged.forgestuck.compat.LegacyCapabilityToken<String>() {});
        assertSame(present, stringCapability.orEmpty(stringCapability, present));
        assertFalse(stringCapability.orEmpty(null, present).isPresent());
    }

    @Test
    void legacyItemStackFluidCapabilityMatchesNeoForgeItemCapability() {
        net.neoforged.forgestuck.testsupport.TestBootstrap.init();
        var stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WATER_BUCKET);
        var neoForgeCapability = net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM
                .getCapability(stack, null);
        var handler = net.neoforged.forgestuck.compat.LegacyCapabilities.getCapability(stack,
                net.neoforged.forgestuck.compat.LegacyForgeCapabilities.FLUID_HANDLER_ITEM);
        assertEquals(neoForgeCapability != null, handler.isPresent());
        if (neoForgeCapability != null) {
            assertSame(neoForgeCapability, handler.resolve().orElseThrow());
        }
    }

    @Test
    void legacyEmptyItemHandlerPreservesEmptyBehavior() {
        net.neoforged.forgestuck.testsupport.TestBootstrap.init();
        var handler = net.neoforged.forgestuck.compat.LegacyEmptyItemHandler.INSTANCE;
        var stack = net.minecraft.world.item.ItemStack.EMPTY;
        assertEquals(0, handler.getSlots());
        assertTrue(handler.getStackInSlot(0).isEmpty());
        assertSame(stack, handler.insertItem(0, stack, false));
        assertTrue(handler.extractItem(0, 1, false).isEmpty());
        assertEquals(0, handler.getSlotLimit(0));
        assertFalse(handler.isItemValid(0, stack));
    }

    @Test
    void legacyTablePrinterFormatsHeadersAndRows() {
        var printer = new net.neoforged.forgestuck.compat.LegacyTablePrinter<String>()
                .header("Name", value -> value)
                .header("Count", ignored -> "2", true)
                .add("ore");
        StringBuilder output = new StringBuilder("prefix\n");
        printer.build(output);
        assertTrue(output.toString().startsWith("prefix\n|"));
        assertTrue(output.toString().contains("|  Name  |  Count  |"));
        assertTrue(output.toString().contains("ore"));
        assertTrue(output.toString().contains("2"));
        printer.clearRows();
        assertFalse(printer.toString().contains("ore"));
    }

    @Test
    void everyWhitelistedMemberExistsOnTarget() throws Exception {
        ForgeOwnerMappings mappings = ForgeOwnerMappings.defaults();
        List<String> missing = new ArrayList<>();
        for (String owner : owners()) {
            ForgeOwnerMappings.Entry e = mappings.get(owner);
            if (e.supportedMembers() == null) continue;
            Class<?> target = load(e.newOwner());
            for (String member : e.supportedMembers()) {
                if (isCallSiteAdapted(e.oldOwner(), member)) continue;
                int paren = member.indexOf('(');
                boolean isMethod = paren >= 0;
                String name;
                String desc;
                if (isMethod) {
                    name = member.substring(0, paren);
                    desc = member.substring(paren);
                } else {
                    java.util.regex.Matcher fm = FIELD.matcher(member);
                    assertTrue(fm.matches(), "unparsable field member " + member);
                    name = fm.group(1);
                    desc = fm.group(2);
                }
                desc = mappings.remapDescriptorTokens(desc, new HashSet<>(), new HashMap<>());
                if (LegacyNameMappings.looksLikeSrg(name)) {
                    String mapped = LegacyNameMappings.defaults().map(e.newOwner(), name, desc);
                    assertNotNull(mapped, "missing SRG mapping for " + e.newOwner() + "." + name + desc);
                    name = mapped;
                }
                boolean ok = !isMethod ? hasField(target, name, desc)
                        : name.equals("<init>") ? hasConstructor(target, desc) : hasMethod(target, name, desc);
                if (!ok) {
                    missing.add(e.newOwner() + "." + name + desc);
                }
            }
        }
        assertEquals(List.of(), missing);
    }

    private static boolean isCallSiteAdapted(String oldOwner, String member) {
        // These mapped members are replaced by dedicated bytecode rewrites, not direct target invocations.
        if (oldOwner.equals("net/minecraftforge/event/entity/living/MobEffectEvent$Added")) {
            return member.equals("isCanceled()Z");
        }
        if (oldOwner.equals("net/minecraftforge/fluids/capability/templates/EmptyFluidHandler")) {
            return member.equals("<init>()V");
        }
        if (oldOwner.equals("net/minecraftforge/client/event/RenderGuiOverlayEvent$Post")) {
            return member.equals("getPartialTick()F")
                    || member.startsWith("<init>(");
        }
        return oldOwner.equals("net/minecraftforge/client/event/RenderGuiOverlayEvent$Pre")
                && member.equals("getPartialTick()F");
    }

    private static final java.util.regex.Pattern FIELD =
            java.util.regex.Pattern.compile("^(.+?)(\\[*(?:[ZBCSIJFD]|L(?:net|java|com|org)/[^;]+;))$");

    private static boolean hasConstructor(Class<?> c, String desc) {
        for (java.lang.reflect.Constructor<?> k : c.getDeclaredConstructors()) {
            int mod = k.getModifiers();
            boolean subclassOnly = java.lang.reflect.Modifier.isProtected(mod)
                    && java.lang.reflect.Modifier.isAbstract(c.getModifiers());
            if ((java.lang.reflect.Modifier.isPublic(mod) || subclassOnly)
                    && Type.getConstructorDescriptor(k).equals(desc)) return true;
        }
        return false;
    }

    private static boolean hasMethod(Class<?> c, String name, String desc) {
        List<Method> all = new ArrayList<>(List.of(c.getMethods()));
        all.addAll(List.of(c.getDeclaredMethods()));
        for (Method m : all) if (m.getName().equals(name) && Type.getMethodDescriptor(m).equals(desc)) return true;
        return false;
    }

    private static boolean hasField(Class<?> c, String name, String desc) {
        for (Field f : c.getFields()) if (f.getName().equals(name) && Type.getDescriptor(f.getType()).equals(desc)) return true;
        return false;
    }
}
