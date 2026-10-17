package net.neoforged.forgestuck.compat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Stand-in for Forge 1.20.1's {@code LootModifierManager}, which NeoForge 1.21.1 removed. Only the static
 * {@code GSON_INSTANCE} is provided so classes that capture it in a static initializer still load; the plain Gson
 * carries none of the vanilla loot (de)serializer adapters, which no longer exist in 1.21.1.
 */
public final class LegacyLootModifierManager {
    public static final Gson GSON_INSTANCE = new GsonBuilder().create();

    private LegacyLootModifierManager() {}
}
