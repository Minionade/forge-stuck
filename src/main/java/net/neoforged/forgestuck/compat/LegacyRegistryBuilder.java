package net.neoforged.forgestuck.compat;

import net.minecraft.resources.ResourceLocation;

/**
 * Link-level stand-in for Forge's {@code RegistryBuilder}. Configuration calls are recorded, but custom registries
 * are not created: {@link #create()} fails explicitly so a mod only breaks when it really builds a registry.
 */
public class LegacyRegistryBuilder<T> {
    private ResourceLocation name;
    private int maxId = Integer.MAX_VALUE - 1;
    private ResourceLocation defaultKey;
    private boolean sync = true;
    private boolean savingDisabled;
    private boolean modificationsAllowed;
    private boolean tagsEnabled;

    public LegacyRegistryBuilder() {}

    /** Applies the recorded settings to the NeoForge builder; the registry key is owned by the DeferredRegister. */
    void configure(net.neoforged.neoforge.registries.RegistryBuilder<T> target) {
        if (maxId != Integer.MAX_VALUE - 1) target.maxId(maxId);
        if (defaultKey != null) target.defaultKey(defaultKey);
        target.sync(sync);
        if (savingDisabled) {
            UnsupportedForgeApi.fail("net.minecraftforge.registries.RegistryBuilder.disableSaving()",
                    String.valueOf(name));
        }
        if (modificationsAllowed) {
            UnsupportedForgeApi.fail("net.minecraftforge.registries.RegistryBuilder.allowModification()",
                    String.valueOf(name));
        }
        if (tagsEnabled) {
            UnsupportedForgeApi.fail("net.minecraftforge.registries.RegistryBuilder.hasTags()", String.valueOf(name));
        }
    }

    public LegacyRegistryBuilder<T> setName(ResourceLocation name) {
        this.name = name;
        return this;
    }

    public LegacyRegistryBuilder<T> setMaxID(int maxId) {
        this.maxId = maxId;
        return this;
    }

    public LegacyRegistryBuilder<T> setDefaultKey(ResourceLocation key) {
        this.defaultKey = key;
        return this;
    }

    public LegacyRegistryBuilder<T> disableSaving() {
        savingDisabled = true;
        return this;
    }

    public LegacyRegistryBuilder<T> disableSync() {
        sync = false;
        return this;
    }

    /**
     * NeoForge's vanilla-backed registries already reject duplicate registrations, so this Forge setting is their
     * default behavior rather than a separate builder option.
     */
    public LegacyRegistryBuilder<T> disableOverrides() {
        return this;
    }

    public LegacyRegistryBuilder<T> allowModification() {
        modificationsAllowed = true;
        return this;
    }

    public LegacyRegistryBuilder<T> hasTags() {
        tagsEnabled = true;
        return this;
    }

    public IForgeRegistry<T> create() {
        UnsupportedForgeApi.fail("net.minecraftforge.registries.RegistryBuilder.create()", String.valueOf(name));
        return null;
    }
}
