package net.neoforged.forgestuck.compat;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.common.data.SpriteSourceProvider;

/** Adapts Forge's sprite-source provider constructor to NeoForge's registry-aware constructor. */
public abstract class LegacySpriteSourceProvider extends SpriteSourceProvider {
    protected LegacySpriteSourceProvider(PackOutput output, ExistingFileHelper fileHelper, String modId) {
        super(output, CompletableFuture.completedFuture(
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)), modId, fileHelper);
    }
}
