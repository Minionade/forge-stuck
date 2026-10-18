package net.neoforged.forgestuck.transformer.jar;

import java.util.List;
import java.util.Set;

final class DataMappings {
    private DataMappings() {}

    static List<ForgeOwnerMappings.Entry> entries() {
        String helper = "net/minecraftforge/common/data/ExistingFileHelper";
        String resourceType = helper + "$IResourceType";
        String type = helper + "$ResourceType";
        String helperDesc = "L" + helper + ";";
        String packType = "Lnet/minecraft/server/packs/PackType;";
        String location = "Lnet/minecraft/resources/ResourceLocation;";
        String resource = "Lnet/minecraft/server/packs/resources/Resource;";
        return List.of(
                new ForgeOwnerMappings.Entry("net/minecraftforge/data/event/GatherDataEvent",
                        "net/neoforged/neoforge/data/event/GatherDataEvent", Set.of(
                                "getGenerator()Lnet/minecraft/data/DataGenerator;",
                                "getLookupProvider()Ljava/util/concurrent/CompletableFuture;",
                                "getExistingFileHelper()" + helperDesc,
                                "includeClient()Z", "includeServer()Z")),
                new ForgeOwnerMappings.Entry(helper, "net/neoforged/neoforge/common/data/ExistingFileHelper",
                        Set.of("<init>(Ljava/util/Collection;Ljava/util/Set;ZLjava/lang/String;Ljava/io/File;)V",
                                "exists(" + location + packType + ")Z",
                                "exists(" + location + packType + "Ljava/lang/String;Ljava/lang/String;)Z",
                                "trackGenerated(" + location + packType + "Ljava/lang/String;Ljava/lang/String;)V",
                                "getResource(" + location + packType + "Ljava/lang/String;Ljava/lang/String;)" + resource,
                                "getResource(" + location + packType + ")" + resource,
                                "getResourceStack(" + location + packType + ")Ljava/util/List;",
                                "isEnabled()Z")),
                new ForgeOwnerMappings.Entry(resourceType,
                        "net/neoforged/neoforge/common/data/ExistingFileHelper$IResourceType",
                        Set.of("getPackType()" + packType, "getSuffix()Ljava/lang/String;",
                                "getPrefix()Ljava/lang/String;")),
                new ForgeOwnerMappings.Entry(type,
                        "net/neoforged/neoforge/common/data/ExistingFileHelper$ResourceType",
                        Set.of("<init>(" + packType + "Ljava/lang/String;Ljava/lang/String;)V",
                                "getPackType()" + packType, "getSuffix()Ljava/lang/String;",
                                "getPrefix()Ljava/lang/String;")));
    }
}
