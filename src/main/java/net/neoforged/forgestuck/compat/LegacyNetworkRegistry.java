package net.neoforged.forgestuck.compat;

import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;

/** Forge 1.20.1 {@code NetworkRegistry}: channel creation only (see {@link LegacySimpleChannel} for limits). */
public final class LegacyNetworkRegistry {
    static final String ACCEPT_VANILLA = "ALLOWVANILLA";
    static final String ABSENT = "ABSENT \uD83E\uDD14";

    private LegacyNetworkRegistry() {}

    public static LegacySimpleChannel newSimpleChannel(ResourceLocation name, Supplier<String> version,
            Predicate<String> client, Predicate<String> server) {
        return new LegacySimpleChannel(name, version.get(), FMLJavaModLoadingContext.get().getModEventBus());
    }

    /** Forge's {@code NetworkRegistry.ChannelBuilder}. */
    public static final class LegacyChannelBuilder {
        private final ResourceLocation name;
        private Supplier<String> version;
        private Predicate<String> client;
        private Predicate<String> server;

        private LegacyChannelBuilder(ResourceLocation name) {
            this.name = name;
        }

        public static LegacyChannelBuilder named(ResourceLocation name) {
            return new LegacyChannelBuilder(name);
        }

        public LegacyChannelBuilder networkProtocolVersion(Supplier<String> version) {
            this.version = version;
            return this;
        }

        public LegacyChannelBuilder clientAcceptedVersions(Predicate<String> predicate) {
            this.client = predicate;
            return this;
        }

        public LegacyChannelBuilder serverAcceptedVersions(Predicate<String> predicate) {
            this.server = predicate;
            return this;
        }

        public LegacyEventChannel eventNetworkChannel() {
            if (version == null) throw new IllegalStateException("networkProtocolVersion not set for channel " + name);
            return new LegacyEventChannel(name, version.get(), FMLJavaModLoadingContext.get().getModEventBus());
        }

        public LegacySimpleChannel simpleChannel() {
            if (version == null) throw new IllegalStateException("networkProtocolVersion not set for channel " + name);
            return newSimpleChannel(name, version, client, server);
        }
    }
}