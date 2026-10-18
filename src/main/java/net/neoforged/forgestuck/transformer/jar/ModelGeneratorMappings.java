package net.neoforged.forgestuck.transformer.jar;

import java.util.List;
import java.util.Set;

final class ModelGeneratorMappings {
    private static final String OLD = "net/minecraftforge/client/model/generators/";
    private static final String NEW = "net/neoforged/neoforge/client/model/generators/";
    private static final String RESOURCE_HELPER = "Lnet/minecraftforge/common/data/ExistingFileHelper;";
    private static final String MODEL_FILE = "Lnet/minecraftforge/client/model/generators/ModelFile;";
    private static final String MODEL_BUILDER = "Lnet/minecraftforge/client/model/generators/ModelBuilder;";
    private static final String CONFIGURED_MODEL = "Lnet/minecraftforge/client/model/generators/ConfiguredModel;";
    private static final String CONFIGURED_BUILDER =
            "Lnet/minecraftforge/client/model/generators/ConfiguredModel$Builder;";

    private ModelGeneratorMappings() {}

    static List<ForgeOwnerMappings.Entry> entries() {
        return List.of(
                entry("BlockModelBuilder", Set.of(
                        "customLoader(Ljava/util/function/BiFunction;)"
                                + "Lnet/minecraftforge/client/model/generators/CustomLoaderBuilder;",
                        "renderType(Ljava/lang/String;)" + MODEL_BUILDER,
                        "texture(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;)" + MODEL_BUILDER)),
                entry("BlockModelProvider", Set.of(
                        "button(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;)" + MODEL_BUILDER,
                        "buttonPressed(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;)" + MODEL_BUILDER,
                        "cross(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;)" + MODEL_BUILDER,
                        "cubeAll(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;)" + MODEL_BUILDER,
                        "cubeBottomTop(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;"
                                + "Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;)"
                                + MODEL_BUILDER,
                        "cubeColumn(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;"
                                + "Lnet/minecraft/resources/ResourceLocation;)" + MODEL_BUILDER,
                        "cubeColumnHorizontal(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;"
                                + "Lnet/minecraft/resources/ResourceLocation;)" + MODEL_BUILDER,
                        "doorBottomLeft(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;"
                                + "Lnet/minecraft/resources/ResourceLocation;)" + MODEL_BUILDER,
                        "doorBottomLeftOpen(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;"
                                + "Lnet/minecraft/resources/ResourceLocation;)" + MODEL_BUILDER,
                        "doorBottomRight(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;"
                                + "Lnet/minecraft/resources/ResourceLocation;)" + MODEL_BUILDER,
                        "doorBottomRightOpen(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;"
                                + "Lnet/minecraft/resources/ResourceLocation;)" + MODEL_BUILDER,
                        "doorTopLeft(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;"
                                + "Lnet/minecraft/resources/ResourceLocation;)" + MODEL_BUILDER,
                        "doorTopLeftOpen(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;"
                                + "Lnet/minecraft/resources/ResourceLocation;)" + MODEL_BUILDER,
                        "doorTopRight(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;"
                                + "Lnet/minecraft/resources/ResourceLocation;)" + MODEL_BUILDER,
                        "doorTopRightOpen(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;"
                                + "Lnet/minecraft/resources/ResourceLocation;)" + MODEL_BUILDER,
                        "fenceGate(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;)" + MODEL_BUILDER,
                        "fenceGateOpen(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;)" + MODEL_BUILDER,
                        "fenceGateWall(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;)" + MODEL_BUILDER,
                        "fenceGateWallOpen(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;)"
                                + MODEL_BUILDER,
                        "fencePost(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;)" + MODEL_BUILDER,
                        "fenceSide(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;)" + MODEL_BUILDER,
                        "getBuilder(Ljava/lang/String;)" + MODEL_BUILDER,
                        "getExistingFile(Lnet/minecraft/resources/ResourceLocation;)"
                                + "Lnet/minecraftforge/client/model/generators/ModelFile$ExistingModelFile;",
                        "pressurePlate(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;)" + MODEL_BUILDER,
                        "pressurePlateDown(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;)"
                                + MODEL_BUILDER,
                        "sign(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;)" + MODEL_BUILDER,
                        "slab(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;"
                                + "Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;)"
                                + MODEL_BUILDER,
                        "slabTop(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;"
                                + "Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;)"
                                + MODEL_BUILDER,
                        "stairs(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;"
                                + "Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;)"
                                + MODEL_BUILDER,
                        "stairsInner(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;"
                                + "Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;)"
                                + MODEL_BUILDER,
                        "stairsOuter(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;"
                                + "Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;)"
                                + MODEL_BUILDER,
                        "trapdoorBottom(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;)" + MODEL_BUILDER,
                        "trapdoorOpen(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;)" + MODEL_BUILDER,
                        "trapdoorOrientableBottom(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;)"
                                + MODEL_BUILDER,
                        "trapdoorOrientableOpen(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;)"
                                + MODEL_BUILDER,
                        "trapdoorOrientableTop(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;)"
                                + MODEL_BUILDER,
                        "trapdoorTop(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;)" + MODEL_BUILDER,
                        "withExistingParent(Ljava/lang/String;Ljava/lang/String;)" + MODEL_BUILDER,
                        "withExistingParent(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;)"
                                + MODEL_BUILDER)),
                entry("BlockStateProvider", Set.of(
                        "<init>(Lnet/minecraft/data/PackOutput;Ljava/lang/String;" + RESOURCE_HELPER + ")V")),
                entry("ConfiguredModel", Set.of(
                        "<init>(" + MODEL_FILE + "IIZ)V",
                        "builder()" + CONFIGURED_BUILDER)),
                entry("ConfiguredModel$Builder", Set.of(
                        "addModel()Ljava/lang/Object;",
                        "build()[" + CONFIGURED_MODEL,
                        "modelFile(" + MODEL_FILE + ")" + CONFIGURED_BUILDER,
                        "rotationX(I)" + CONFIGURED_BUILDER,
                        "rotationY(I)" + CONFIGURED_BUILDER)),
                entry("CustomLoaderBuilder", Set.of(
                        "<init>(Lnet/minecraft/resources/ResourceLocation;" + MODEL_BUILDER + RESOURCE_HELPER
                                + "Z)V",
                        "toJson(Lcom/google/gson/JsonObject;)Lcom/google/gson/JsonObject;")),
                entry("ItemModelBuilder", Set.of(
                        "customLoader(Ljava/util/function/BiFunction;)"
                                + "Lnet/minecraftforge/client/model/generators/CustomLoaderBuilder;",
                        "parent(" + MODEL_FILE + ")" + MODEL_BUILDER,
                        "renderType(Ljava/lang/String;)" + MODEL_BUILDER,
                        "texture(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;)" + MODEL_BUILDER)),
                entry("ItemModelProvider", Set.of(
                        "<init>(Lnet/minecraft/data/PackOutput;Ljava/lang/String;" + RESOURCE_HELPER + ")V",
                        "getBuilder(Ljava/lang/String;)" + MODEL_BUILDER,
                        "withExistingParent(Ljava/lang/String;Ljava/lang/String;)" + MODEL_BUILDER,
                        "withExistingParent(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;)"
                                + MODEL_BUILDER)),
                entry("ModelBuilder", Set.of(
                        "parent(" + MODEL_FILE + ")" + MODEL_BUILDER,
                        "texture(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;)" + MODEL_BUILDER,
                        "toJson()Lcom/google/gson/JsonObject;")),
                entry("ModelFile", Set.of()),
                entry("ModelFile$ExistingModelFile", Set.of(
                        "<init>(Lnet/minecraft/resources/ResourceLocation;" + RESOURCE_HELPER + ")V")),
                entry("ModelFile$UncheckedModelFile", Set.of(
                        "<init>(Ljava/lang/String;)V",
                        "<init>(Lnet/minecraft/resources/ResourceLocation;)V")),
                entry("MultiPartBlockStateBuilder", Set.of(
                        "part()Lnet/minecraftforge/client/model/generators/ConfiguredModel$Builder;")),
                entry("MultiPartBlockStateBuilder$PartBuilder", Set.of(
                        "condition(Lnet/minecraft/world/level/block/state/properties/Property;"
                                + "[Ljava/lang/Comparable;)Lnet/minecraftforge/client/model/generators/"
                                + "MultiPartBlockStateBuilder$PartBuilder;",
                        "end()Lnet/minecraftforge/client/model/generators/MultiPartBlockStateBuilder;")),
                entry("VariantBlockStateBuilder", Set.of(
                        "forAllStates(Ljava/util/function/Function;)"
                                + "Lnet/minecraftforge/client/model/generators/VariantBlockStateBuilder;",
                        "partialState()Lnet/minecraftforge/client/model/generators/"
                                + "VariantBlockStateBuilder$PartialBlockstate;")),
                entry("VariantBlockStateBuilder$PartialBlockstate", Set.of(
                        "modelForState()Lnet/minecraftforge/client/model/generators/ConfiguredModel$Builder;",
                        "setModels([Lnet/minecraftforge/client/model/generators/ConfiguredModel;)"
                                + "Lnet/minecraftforge/client/model/generators/VariantBlockStateBuilder;",
                        "with(Lnet/minecraft/world/level/block/state/properties/Property;"
                                + "Ljava/lang/Comparable;)Lnet/minecraftforge/client/model/generators/"
                                + "VariantBlockStateBuilder$PartialBlockstate;")));
    }

    private static ForgeOwnerMappings.Entry entry(String simpleName, Set<String> members) {
        String owner = OLD + simpleName;
        return new ForgeOwnerMappings.Entry(owner, NEW + simpleName, members);
    }
}
