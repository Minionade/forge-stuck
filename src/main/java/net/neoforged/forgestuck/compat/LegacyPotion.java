package net.neoforged.forgestuck.compat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.nbt.Tag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

public final class LegacyPotion {
    private LegacyPotion() {}

    public static ResourceKey<Potion> emptyKey() {
        return Potions.WATER.unwrapKey()
                .orElseThrow(() -> new IllegalStateException("Water potion is missing its registry key"));
    }

    public static Potion getPotion(CompoundTag tag) {
        if (tag == null) return Potions.WATER.value();
        ResourceLocation key = ResourceLocation.tryParse(tag.getString("Potion"));
        if (key == null) return Potions.WATER.value();
        Potion potion = BuiltInRegistries.POTION.get(key);
        return potion == null ? Potions.WATER.value() : potion;
    }

    public static int getColor(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return (contents == null ? PotionContents.EMPTY : contents).getColor();
    }

    public static String getName(Potion potion, String prefix) {
        return potion.getName(BuiltInRegistries.POTION.getResourceKey(potion)
                .flatMap(BuiltInRegistries.POTION::getHolder), prefix);
    }

    public static List<MobEffectInstance> getAllEffects(CompoundTag tag) {
        List<MobEffectInstance> effects = new ArrayList<>(getPotion(tag).getEffects());
        effects.addAll(getCustomEffects(tag));
        return effects;
    }

    public static List<MobEffectInstance> getCustomEffects(CompoundTag tag) {
        List<MobEffectInstance> effects = new ArrayList<>();
        if (tag == null) return effects;
        for (Tag effectTag : tag.getList("CustomPotionEffects", Tag.TAG_COMPOUND)) {
            MobEffectInstance effect = MobEffectInstance.load((CompoundTag) effectTag);
            if (effect != null) effects.add(effect);
        }
        return effects;
    }
}
