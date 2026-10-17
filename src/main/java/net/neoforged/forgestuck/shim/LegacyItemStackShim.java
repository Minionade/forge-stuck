package net.neoforged.forgestuck.shim;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Static replacements for Forge 1.20.1 {@code ItemStack.getTag/getOrCreateTag/setTag}, which do not exist on
 * 1.21.1. The tag is backed by {@link DataComponents#CUSTOM_DATA}.
 *
 * <p>Contract:
 * <ul>
 * <li>{@link #getTag} returns {@code null} when the stack has no custom data, otherwise a <em>live</em> tag: changes
 * made through it are visible on the stack without a write-back.</li>
 * <li>{@link #getOrCreateTag} creates empty custom data when missing and returns a live tag.</li>
 * <li>{@link #setTag} with {@code null} removes the custom data; otherwise the stack stores a copy of the tag.</li>
 * </ul>
 *
 * <p>Live tags are only handed out for a {@link CustomData} instance that this shim created for the stack itself, so a
 * mutation never leaks into a component shared with the item prototype or another stack that was produced by
 * {@code ItemStack.copy()} (which shares component values). Known limits: a live tag obtained before
 * {@code stack.copy()} keeps aliasing the copy until the next shim call on either stack, and vanilla-managed NBT
 * keys of 1.20.1 ({@code Damage}, {@code display}, {@code Enchantments}, ...) are separate components in 1.21.1 and
 * are not mapped.
 */
public final class LegacyItemStackShim {
    private static final Map<ItemStack, CustomData> OWNED = Collections.synchronizedMap(new WeakHashMap<>());

    private LegacyItemStackShim() {}

    public static CompoundTag getTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        return owned(stack, data).getUnsafe();
    }

    public static CompoundTag getOrCreateTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            CustomData created = CustomData.of(new CompoundTag());
            stack.set(DataComponents.CUSTOM_DATA, created);
            OWNED.put(stack, created);
            return created.getUnsafe();
        }
        return owned(stack, data).getUnsafe();
    }

    public static void setTag(ItemStack stack, CompoundTag tag) {
        if (tag == null) {
            stack.remove(DataComponents.CUSTOM_DATA);
            OWNED.remove(stack);
            return;
        }
        CustomData fresh = CustomData.of(tag);
        stack.set(DataComponents.CUSTOM_DATA, fresh);
        OWNED.put(stack, fresh);
    }

    private static CustomData owned(ItemStack stack, CustomData current) {
        if (OWNED.get(stack) == current) return current;
        CustomData copy = CustomData.of(current.copyTag());
        stack.set(DataComponents.CUSTOM_DATA, copy);
        OWNED.put(stack, copy);
        return copy;
    }
}
