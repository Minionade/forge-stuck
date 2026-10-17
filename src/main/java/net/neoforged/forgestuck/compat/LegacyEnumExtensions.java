package net.neoforged.forgestuck.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * Target of rewritten Forge 1.20.1 enum-extension factories such as
 * {@code ItemDisplayContext.create(String, ResourceLocation, ItemDisplayContext)}. Forge created the constant at call
 * time; NeoForge 1.21.1 only creates constants while the enum class loads, from {@code META-INF/enumextensions.json}.
 * The transformer emits that JSON for every call whose arguments it can evaluate statically, and these methods then
 * look the already-created constant up by its serialized name (the registry id), like Forge's own lookup key.
 * A call whose constant could not be generated fails precisely instead of returning a wrong value.
 */
public final class LegacyEnumExtensions {
    private LegacyEnumExtensions() {}

    public static ItemDisplayContext itemDisplayContext(String keyName, ResourceLocation id, ItemDisplayContext fallback) {
        ItemDisplayContext found = find(id.toString());
        if (found != null) return found;
        throw new UnsupportedOperationException("Forge enum extension ItemDisplayContext.create(\"" + keyName + "\", "
                + id + ", " + fallback + ") has no generated NeoForge enum constant: Forge Stuck could not evaluate "
                + "its arguments statically when transforming the mod jar (see the transformer log), so no "
                + "enumextensions.json entry was emitted for it");
    }

    private static ItemDisplayContext find(String serializedName) {
        for (ItemDisplayContext context : ItemDisplayContext.values()) {
            if (context.isModded() && serializedName.equals(context.getSerializedName())) return context;
        }
        return null;
    }
}
