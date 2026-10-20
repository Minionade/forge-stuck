package net.neoforged.forgestuck.compat;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.forgestuck.testsupport.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LegacyPotionTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.init();
    }

    @Test
    void resolvesPotionFromLegacyCompoundTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Potion", "minecraft:water");

        assertSame(Potions.WATER.value(), LegacyPotion.getPotion(tag));
    }

    @Test
    void defaultsToEmptyPotionWhenLegacyTagHasNoPotionId() {
        assertSame(Potions.WATER.value(), LegacyPotion.getPotion(new CompoundTag()));
    }

    @Test
    void defaultsToWaterWhenLegacyTagIsAbsent() {
        assertSame(Potions.WATER.value(), LegacyPotion.getPotion(null));
    }

    @Test
    void returnsBasePotionEffectsForLegacyTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Potion", "minecraft:swiftness");

        assertTrue(LegacyPotion.getAllEffects(tag).stream()
                .anyMatch(effect -> effect.getEffect().is(MobEffects.MOVEMENT_SPEED)));
    }

    @Test
    void returnsCustomEffectsForLegacyTag() {
        MobEffectInstance customEffect = new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120);
        ListTag customEffects = new ListTag();
        customEffects.add((CompoundTag) customEffect.save());
        CompoundTag tag = new CompoundTag();
        tag.put("CustomPotionEffects", customEffects);

        assertTrue(LegacyPotion.getCustomEffects(tag).stream()
                .anyMatch(effect -> effect.getEffect().is(MobEffects.MOVEMENT_SPEED)
                        && effect.getDuration() == 120));
    }

    @Test
    void getsPotionColorFromComponentsAndDefaultsWhenMissing() {
        ItemStack stack = new ItemStack(Items.POTION);
        assertEquals(PotionContents.EMPTY.getColor(), LegacyPotion.getColor(stack));

        int customColor = 0x123456;
        stack.set(DataComponents.POTION_CONTENTS,
                new PotionContents(Optional.empty(), Optional.of(customColor), List.of()));
        assertEquals(customColor, LegacyPotion.getColor(stack));
    }
}
