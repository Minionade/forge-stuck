package net.neoforged.forgestuck.compat;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.GlobalLootModifierProvider;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;

/** Bridges the Forge data-provider constructor and no-condition add overload. */
public abstract class LegacyGlobalLootModifierProvider extends GlobalLootModifierProvider {
    protected LegacyGlobalLootModifierProvider(PackOutput output, String modId) {
        super(output, CompletableFuture.completedFuture(
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)), modId);
    }

    public void add(String name, IGlobalLootModifier modifier) {
        super.add(name, modifier, List.of());
    }
}
