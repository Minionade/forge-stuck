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

    public LegacyRegistryBuilder() {}

    /** Applies the recorded settings to the NeoForge builder; the registry key is owned by the DeferredRegister. */
    void configure(net.neoforged.neoforge.registries.RegistryBuilder<T> target) {
        if (maxId != Integer.MAX_VALUE - 1) target.maxId(maxId);
        if (defaultKey != null) target.defaultKey(defaultKey);
        target.sync(sync);
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
        return this;
    }

    public LegacyRegistryBuilder<T> disableSync() {
        sync = false;
        return this;
    }

    public LegacyRegistryBuilder<T> allowModification() {
        return this;
    }

    public LegacyRegistryBuilder<T> hasTags() {
        return this;
    }

    public IForgeRegistry<T> create() {
        UnsupportedForgeApi.fail("net.minecraftforge.registries.RegistryBuilder.create()", String.valueOf(name));
        return null;
    }
}
