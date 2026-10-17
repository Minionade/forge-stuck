package net.neoforged.forgestuck.compat;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.stats.StatType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.core.particles.ParticleType;

/**
 * Stand-in for Forge 1.20.1's {@code ForgeRegistries}, limited to fields whose registry is a static (non-datapack)
 * registry in 1.21.1. Legacy fields without a 1.21.1 static registry (e.g. {@code ENCHANTMENTS}, {@code BIOMES}) are
 * intentionally absent so that a class using them is reported rather than silently mis-wired.
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
    public static final IForgeRegistry<VillagerProfession> VILLAGER_PROFESSIONS = of(Registries.VILLAGER_PROFESSION);
    public static final IForgeRegistry<PoiType> POI_TYPES = of(Registries.POINT_OF_INTEREST_TYPE);
    public static final IForgeRegistry<Feature<?>> FEATURES = of(Registries.FEATURE);

    private static <V> IForgeRegistry<V> of(ResourceKey<? extends Registry<V>> key) {
        return new VanillaRegistryView<>(key);
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
        public static final ResourceKey<Registry<VillagerProfession>> VILLAGER_PROFESSIONS = Registries.VILLAGER_PROFESSION;
        public static final ResourceKey<Registry<PoiType>> POI_TYPES = Registries.POINT_OF_INTEREST_TYPE;
        public static final ResourceKey<Registry<Feature<?>>> FEATURES = Registries.FEATURE;
    }
}
