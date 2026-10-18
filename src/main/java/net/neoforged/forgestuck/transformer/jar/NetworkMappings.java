package net.neoforged.forgestuck.transformer.jar;

import java.util.List;
import java.util.Set;

/**
 * Forge 1.20.1 SimpleChannel networking mapped onto {@code compat.LegacySimpleChannel} and friends, which sit on
 * NeoForge's payload registration. Members outside the whitelist (messageBuilder, isRemotePresent,
 * login-phase APIs) fail at the call that uses them. Reflection-checked in
 * {@code ForgeOwnerMappingTargetsTest}.
 */
final class NetworkMappings {
    private NetworkMappings() {}

    private static final String N = "net/minecraftforge/network/";
    private static final String COMPAT = "net/neoforged/forgestuck/compat/";

    static List<ForgeOwnerMappings.Entry> entries() {
        String rl = "Lnet/minecraft/resources/ResourceLocation;";
        String sc = "L" + N + "simple/SimpleChannel;";
        String dir = "L" + N + "NetworkDirection;";
        String ctx = "L" + N + "NetworkEvent$Context;";
        String pd = "L" + N + "PacketDistributor;";
        String target = "L" + N + "PacketDistributor$PacketTarget;";
        String tp = "L" + N + "PacketDistributor$TargetPoint;";
        String cb = "L" + N + "NetworkRegistry$ChannelBuilder;";
        String sup = "Ljava/util/function/Supplier;";
        String pred = "Ljava/util/function/Predicate;";
        String key = "Lnet/minecraft/resources/ResourceKey;";
        String o = "Ljava/lang/Object;";
        String mh = "L" + N + "simple/IndexedMessageCodec$MessageHandler;";
        String factory = "L" + N + "IContainerFactory;";
        String reg = "ILjava/lang/Class;Ljava/util/function/BiConsumer;Ljava/util/function/Function;"
                + "Ljava/util/function/BiConsumer;";
        Set<String> dirs = Set.of("PLAY_TO_SERVER" + dir, "PLAY_TO_CLIENT" + dir, "LOGIN_TO_SERVER" + dir,
                "LOGIN_TO_CLIENT" + dir, "getOriginationSide()Lnet/minecraftforge/fml/LogicalSide;",
                "getReceptionSide()Lnet/minecraftforge/fml/LogicalSide;", "values()[" + dir,
                "valueOf(Ljava/lang/String;)" + dir,
                "buildPacket(Lorg/apache/commons/lang3/tuple/Pair;" + rl + ")L" + N + "ICustomPacket;");
        return List.of(
                e(N + "IContainerFactory", COMPAT + "LegacyContainerFactory", Set.of(
                        "create(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/network/FriendlyByteBuf;)"
                                + "Lnet/minecraft/world/inventory/AbstractContainerMenu;",
                        "create(ILnet/minecraft/world/entity/player/Inventory;)"
                                + "Lnet/minecraft/world/inventory/AbstractContainerMenu;")),
                e("net/minecraftforge/common/extensions/IForgeMenuType",
                        COMPAT + "LegacyForgeMenuType", Set.of(
                                "create(" + factory + ")Lnet/minecraft/world/inventory/MenuType;")),
                e(N + "simple/SimpleChannel", COMPAT + "LegacySimpleChannel", Set.of(
                        "registerMessage(" + reg + ")" + mh,
                        "registerMessage(" + reg + "Ljava/util/Optional;)" + mh,
                        "send(" + target + o + ")V", "sendToServer(" + o + ")V",
                        "sendTo(" + o + "Lnet/minecraft/network/Connection;" + dir + ")V",
                        "reply(" + o + ctx + ")V")),
                e(N + "simple/IndexedMessageCodec$MessageHandler", COMPAT + "LegacyMessageHandler", Set.of()),
                e(N + "NetworkRegistry", COMPAT + "LegacyNetworkRegistry", Set.of(
                        "newSimpleChannel(" + rl + sup + pred + pred + ")" + sc)),
                e(N + "NetworkRegistry$ChannelBuilder", COMPAT + "LegacyNetworkRegistry$LegacyChannelBuilder", Set.of(
                        "named(" + rl + ")" + cb, "networkProtocolVersion(" + sup + ")" + cb,
                        "clientAcceptedVersions(" + pred + ")" + cb, "serverAcceptedVersions(" + pred + ")" + cb,
                        "simpleChannel()" + sc, "eventNetworkChannel()L" + N + "event/EventNetworkChannel;")),
                e(N + "event/EventNetworkChannel", COMPAT + "LegacyEventChannel", Set.of("registerObject(" + o + ")V")),
                e(N + "NetworkEvent", COMPAT + "LegacyNetworkEvent", Set.of(
                        "getPayload()Lnet/minecraft/network/FriendlyByteBuf;", "getSource()" + sup)),
                e(N + "NetworkEvent$ClientCustomPayloadEvent", COMPAT + "LegacyNetworkEvent$ClientCustomPayloadEvent", Set.of(
                        "getPayload()Lnet/minecraft/network/FriendlyByteBuf;", "getSource()" + sup)),
                e(N + "NetworkEvent$ServerCustomPayloadEvent", COMPAT + "LegacyNetworkEvent$ServerCustomPayloadEvent", Set.of(
                        "getPayload()Lnet/minecraft/network/FriendlyByteBuf;", "getSource()" + sup)),
                e(N + "ICustomPacket", COMPAT + "LegacyCustomPacket", Set.of("getThis()Lnet/minecraft/network/protocol/Packet;")),
                e(N + "NetworkEvent$Context", COMPAT + "LegacyNetworkContext", Set.of(
                        "getDirection()" + dir, "getSender()Lnet/minecraft/server/level/ServerPlayer;",
                        "getNetworkManager()Lnet/minecraft/network/Connection;",
                        "enqueueWork(Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;",
                        "setPacketHandled(Z)V", "getPacketHandled()Z")),
                e(N + "NetworkDirection", COMPAT + "LegacyNetworkDirection", dirs),
                e(N + "PacketDistributor", COMPAT + "LegacyPacketDistributor", Set.of(
                        "PLAYER" + pd, "DIMENSION" + pd, "NEAR" + pd, "ALL" + pd, "SERVER" + pd,
                        "TRACKING_ENTITY" + pd, "TRACKING_ENTITY_AND_SELF" + pd, "TRACKING_CHUNK" + pd,
                        "with(" + sup + ")" + target, "noArg()" + target)),
                e(N + "PacketDistributor$PacketTarget", COMPAT + "LegacyPacketDistributor$LegacyPacketTarget", Set.of()),
                e(N + "PacketDistributor$TargetPoint", COMPAT + "LegacyPacketDistributor$LegacyTargetPoint", Set.of(
                        "<init>(DDDD" + key + ")V",
                        "<init>(Lnet/minecraft/server/level/ServerPlayer;DDDD" + key + ")V",
                        "p(DDDD" + key + ")" + tp)));
    }

    private static ForgeOwnerMappings.Entry e(String oldOwner, String newOwner, Set<String> members) {
        return new ForgeOwnerMappings.Entry(oldOwner, newOwner, members);
    }
}