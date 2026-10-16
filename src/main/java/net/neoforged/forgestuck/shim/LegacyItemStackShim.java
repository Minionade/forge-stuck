package net.neoforged.forgestuck.shim;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal, safe shim for common legacy ItemStack tag access patterns used by Forge 1.20.1 mods.
 *
 * It defers to the modern NeoForge ItemStack API and no-ops if the stack or tag is absent.
 */
public final class LegacyItemStackShim {
    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyItemStackShim.class);

    private LegacyItemStackShim() {
    }

    public static CompoundTag getTag(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        return stack.getTag();
    }

    public static CompoundTag getOrCreateTag(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        return stack.getOrCreateTag();
    }

    public static boolean hasTag(ItemStack stack) {
        return stack != null && stack.hasTag();
    }

    public static void setTag(ItemStack stack, CompoundTag tag) {
        if (stack == null) {
            return;
        }
        stack.setTag(tag);
    }

    public static void syncTag(ItemStack stack, CompoundTag tag) {
        if (stack == null) {
            return;
        }
        stack.setTag(tag);
    }
}
