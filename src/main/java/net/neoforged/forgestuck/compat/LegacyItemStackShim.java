package net.neoforged.forgestuck.compat;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.food.FoodProperties;
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
    private static final ThreadLocal<HolderLookup.Provider> REGISTRY_PROVIDER = new ThreadLocal<>();

    private LegacyItemStackShim() {}

    public static HolderLookup.Provider pushRegistryProvider(HolderLookup.Provider provider) {
        Objects.requireNonNull(provider, "provider");
        HolderLookup.Provider previous = REGISTRY_PROVIDER.get();
        REGISTRY_PROVIDER.set(provider);
        return previous;
    }

    public static void restoreRegistryProvider(HolderLookup.Provider provider) {
        if (provider == null) {
            REGISTRY_PROVIDER.remove();
        } else {
            REGISTRY_PROVIDER.set(provider);
        }
    }

    public static CompoundTag save(ItemStack stack, CompoundTag tag) {
        Tag saved = stack.save(getRegistryProvider(), tag);
        if (saved instanceof CompoundTag compound) return compound;
        throw new IllegalStateException("Serialized item stack did not produce a compound tag");
    }

    public static ItemStack parse(CompoundTag tag) {
        return ItemStack.parseOptional(getRegistryProvider(), tag);
    }

    public static int getMaxStackSize(Item item) {
        return item.getDefaultMaxStackSize();
    }

    public static CompoundTag getBlockEntityData(ItemStack stack) {
        CustomData data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        return data == null ? null : data.copyTag();
    }

    public static CompoundTag getTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        return owned(stack, data).getUnsafe();
    }

    public static net.minecraft.nbt.ListTag getEnchantmentTags(ItemStack stack) {
        net.minecraft.nbt.ListTag tags = new net.minecraft.nbt.ListTag();
        ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments == null) return tags;
        for (var entry : enchantments.entrySet()) {
            String id = entry.getKey().unwrapKey()
                    .orElseThrow(() -> new IllegalStateException("Enchantment has no registry key"))
                    .location().toString();
            CompoundTag tag = new CompoundTag();
            tag.putString("id", id);
            tag.putShort("lvl", (short) entry.getIntValue());
            tags.add(tag);
        }
        return tags;
    }

    /** Forge 1.20.1 {@code hasTag()}: true when the stack carries a tag, i.e. custom data. */
    public static boolean hasTag(ItemStack stack) {
        return stack.get(DataComponents.CUSTOM_DATA) != null;
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

    public static CompoundTag getOrCreateTagElement(ItemStack stack, String key) {
        CompoundTag tag = getOrCreateTag(stack);
        if (!tag.contains(key, Tag.TAG_COMPOUND)) {
            CompoundTag element = new CompoundTag();
            tag.put(key, element);
            return element;
        }
        return tag.getCompound(key);
    }

    public static CompoundTag getTagElement(ItemStack stack, String key) {
        CompoundTag tag = getTag(stack);
        return tag != null && tag.contains(key, Tag.TAG_COMPOUND) ? tag.getCompound(key) : null;
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

    public static CompoundTag getShareTag(FriendlyByteBuf buffer, ItemStack stack) {
        Tag encoded = DataComponentPatch.CODEC.encodeStart(registryOps(buffer), stack.getComponentsPatch())
                .getOrThrow(IllegalArgumentException::new);
        if (encoded instanceof CompoundTag compound) return compound;
        throw new IllegalStateException("ItemStack component patch did not encode to a compound tag");
    }

    public static void readShareTag(FriendlyByteBuf buffer, ItemStack stack, CompoundTag tag) {
        DataComponentPatch patch = DataComponentPatch.CODEC.parse(registryOps(buffer), tag)
                .getOrThrow(IllegalArgumentException::new);
        stack.applyComponents(patch);
    }

    public static FoodProperties getFoodProperties(Item item) {
        return item.components().get(DataComponents.FOOD);
    }

    public static boolean isEdible(Item item) {
        return item.components().has(DataComponents.FOOD);
    }

    public static boolean isDamageable(Item item) {
        return item.getDefaultInstance().isDamageableItem();
    }

    public static ItemStack setHoverName(ItemStack stack, Component name) {
        if (name == null) {
            stack.remove(DataComponents.CUSTOM_NAME);
        } else {
            stack.set(DataComponents.CUSTOM_NAME, name);
        }
        return stack;
    }

    public static boolean hasCustomHoverName(ItemStack stack) {
        return stack.get(DataComponents.CUSTOM_NAME) != null;
    }

    private static CustomData owned(ItemStack stack, CustomData current) {
        if (OWNED.get(stack) == current) return current;
        CustomData copy = CustomData.of(current.copyTag());
        stack.set(DataComponents.CUSTOM_DATA, copy);
        OWNED.put(stack, copy);
        return copy;
    }

    public static HolderLookup.Provider getRegistryProvider() {
        HolderLookup.Provider provider = REGISTRY_PROVIDER.get();
        if (provider == null) {
            throw new IllegalStateException("Legacy ItemStack NBT serialization requires a registry provider");
        }
        return provider;
    }

    private static DynamicOps<Tag> registryOps(FriendlyByteBuf buffer) {
        if (buffer instanceof RegistryFriendlyByteBuf registryBuffer) {
            return RegistryOps.create(NbtOps.INSTANCE, registryBuffer.registryAccess());
        }
        throw new IllegalArgumentException("Legacy item stack share tags require a registry-friendly buffer");
    }
}
