package net.neoforged.forgestuck.compat;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.stats.StatType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Stand-in for Forge 1.20.1's {@code ForgeRegistries}, backed by static registries where possible. {@code ENCHANTMENTS}
 * views the active datapack registry when available and vanilla enchantments before a world exists, while
 * {@code DISPLAY_CONTEXTS} views enum extensions rather than a runtime registry. Other legacy fields without a 1.21.1
 * equivalent (e.g. {@code BIOMES}) remain absent.
 */
public final class ForgeRegistries {
    private ForgeRegistries() {}

    public static final IForgeRegistry<Block> BLOCKS = of(Registries.BLOCK);
    public static final IForgeRegistry<Fluid> FLUIDS = of(Registries.FLUID);
    public static final IForgeRegistry<Item> ITEMS = of(Registries.ITEM);
    public static final IForgeRegistry<MobEffect> MOB_EFFECTS = of(Registries.MOB_EFFECT);
    public static final IForgeRegistry<Potion> POTIONS = of(Registries.POTION);
    public static final IForgeRegistry<Attribute> ATTRIBUTES = of(Registries.ATTRIBUTE);
    public static final IForgeRegistry<StatType<?>> STAT_TYPES = of(Registries.STAT_TYPE);
    public static final IForgeRegistry<SoundEvent> SOUND_EVENTS = of(Registries.SOUND_EVENT);
    public static final IForgeRegistry<ParticleType<?>> PARTICLE_TYPES = of(Registries.PARTICLE_TYPE);
    public static final IForgeRegistry<BlockEntityType<?>> BLOCK_ENTITY_TYPES = of(Registries.BLOCK_ENTITY_TYPE);
    public static final IForgeRegistry<EntityType<?>> ENTITY_TYPES = of(Registries.ENTITY_TYPE);
    public static final IForgeRegistry<MenuType<?>> MENU_TYPES = of(Registries.MENU);
    public static final IForgeRegistry<RecipeType<?>> RECIPE_TYPES = of(Registries.RECIPE_TYPE);
    public static final IForgeRegistry<RecipeSerializer<?>> RECIPE_SERIALIZERS = of(Registries.RECIPE_SERIALIZER);
    public static final IForgeRegistry<ArgumentTypeInfo<?, ?>> COMMAND_ARGUMENT_TYPES =
            of(Registries.COMMAND_ARGUMENT_TYPE);
    public static final IForgeRegistry<VillagerProfession> VILLAGER_PROFESSIONS = of(Registries.VILLAGER_PROFESSION);
    public static final IForgeRegistry<PoiType> POI_TYPES = of(Registries.POINT_OF_INTEREST_TYPE);
    public static final IForgeRegistry<Feature<?>> FEATURES = of(Registries.FEATURE);
    /** Datapack registry in 1.21.1: lookups use the active access, or vanilla contents during menu-time preview. */
    public static final IForgeRegistry<Enchantment> ENCHANTMENTS =
            new DatapackRegistryView<>(Registries.ENCHANTMENT, () -> LegacyEnchantmentRegistry.ENCHANTMENT);

    public static final IForgeRegistry<EntityDataSerializer<?>> ENTITY_DATA_SERIALIZERS =
            of(NeoForgeRegistries.Keys.ENTITY_DATA_SERIALIZERS);
    public static final Supplier<IForgeRegistry<FluidType>> FLUID_TYPES =
            () -> of(NeoForgeRegistries.Keys.FLUID_TYPES);
    public static final Supplier<IForgeRegistry<ItemDisplayContext>> DISPLAY_CONTEXTS =
            () -> DisplayContextRegistryView.INSTANCE;

    private static <V> IForgeRegistry<V> of(ResourceKey<? extends Registry<V>> key) {
        return new VanillaRegistryView<>(key);
    }

    static IForgeRegistry<?> byName(net.minecraft.resources.ResourceLocation name) {
        return List.of(BLOCKS, FLUIDS, ITEMS, MOB_EFFECTS, POTIONS, ATTRIBUTES, STAT_TYPES, SOUND_EVENTS,
                        PARTICLE_TYPES, BLOCK_ENTITY_TYPES, ENTITY_TYPES, MENU_TYPES, RECIPE_TYPES,
                        RECIPE_SERIALIZERS, COMMAND_ARGUMENT_TYPES, VILLAGER_PROFESSIONS, POI_TYPES, FEATURES,
                        ENCHANTMENTS, ENTITY_DATA_SERIALIZERS, FLUID_TYPES.get(), DISPLAY_CONTEXTS.get())
                .stream()
                .filter(registry -> registry.getRegistryName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown Forge registry " + name));
    }

    /** Stand-in for {@code ForgeRegistries.Keys}: the registry keys themselves. */
    public static final class Keys {
        private Keys() {}

        public static final ResourceKey<Registry<Block>> BLOCKS = Registries.BLOCK;
        public static final ResourceKey<Registry<Fluid>> FLUIDS = Registries.FLUID;
        public static final ResourceKey<Registry<Item>> ITEMS = Registries.ITEM;
        public static final ResourceKey<Registry<MobEffect>> MOB_EFFECTS = Registries.MOB_EFFECT;
        public static final ResourceKey<Registry<Potion>> POTIONS = Registries.POTION;
        public static final ResourceKey<Registry<Attribute>> ATTRIBUTES = Registries.ATTRIBUTE;
        public static final ResourceKey<Registry<StatType<?>>> STAT_TYPES = Registries.STAT_TYPE;
        public static final ResourceKey<Registry<SoundEvent>> SOUND_EVENTS = Registries.SOUND_EVENT;
        public static final ResourceKey<Registry<ParticleType<?>>> PARTICLE_TYPES = Registries.PARTICLE_TYPE;
        public static final ResourceKey<Registry<BlockEntityType<?>>> BLOCK_ENTITY_TYPES = Registries.BLOCK_ENTITY_TYPE;
        public static final ResourceKey<Registry<EntityType<?>>> ENTITY_TYPES = Registries.ENTITY_TYPE;
        public static final ResourceKey<Registry<MenuType<?>>> MENU_TYPES = Registries.MENU;
        public static final ResourceKey<Registry<RecipeType<?>>> RECIPE_TYPES = Registries.RECIPE_TYPE;
        public static final ResourceKey<Registry<RecipeSerializer<?>>> RECIPE_SERIALIZERS = Registries.RECIPE_SERIALIZER;
        public static final ResourceKey<Registry<ArgumentTypeInfo<?, ?>>> COMMAND_ARGUMENT_TYPES =
                Registries.COMMAND_ARGUMENT_TYPE;
        public static final ResourceKey<Registry<VillagerProfession>> VILLAGER_PROFESSIONS = Registries.VILLAGER_PROFESSION;
        public static final ResourceKey<Registry<PoiType>> POI_TYPES = Registries.POINT_OF_INTEREST_TYPE;
        public static final ResourceKey<Registry<Feature<?>>> FEATURES = Registries.FEATURE;
        public static final ResourceKey<Registry<Enchantment>> ENCHANTMENTS = Registries.ENCHANTMENT;
        public static final ResourceKey<Registry<EntityDataSerializer<?>>> ENTITY_DATA_SERIALIZERS =
                NeoForgeRegistries.Keys.ENTITY_DATA_SERIALIZERS;
        public static final ResourceKey<Registry<ItemDisplayContext>> DISPLAY_CONTEXTS = DisplayContextRegistryView.KEY;
        public static final ResourceKey<? extends Registry<?>> GLOBAL_LOOT_MODIFIER_SERIALIZERS =
                NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS;
        public static final ResourceKey<? extends Registry<?>> BIOME_MODIFIER_SERIALIZERS =
                NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS;
        public static final ResourceKey<? extends Registry<?>> STRUCTURE_MODIFIER_SERIALIZERS =
                NeoForgeRegistries.Keys.STRUCTURE_MODIFIER_SERIALIZERS;
        public static final ResourceKey<? extends Registry<?>> FLUID_TYPES = NeoForgeRegistries.Keys.FLUID_TYPES;
        public static final ResourceKey<? extends Registry<?>> HOLDER_SET_TYPES = NeoForgeRegistries.Keys.HOLDER_SET_TYPES;
        public static final ResourceKey<? extends Registry<?>> BIOME_MODIFIERS = NeoForgeRegistries.Keys.BIOME_MODIFIERS;
        public static final ResourceKey<? extends Registry<?>> STRUCTURE_MODIFIERS =
                NeoForgeRegistries.Keys.STRUCTURE_MODIFIERS;
    }
}
